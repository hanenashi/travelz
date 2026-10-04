(() => {
  const cfg = window.TRAVELZ_GATE || {};
  const content = document.getElementById('trip-content');
  const empty = document.getElementById('trip-empty');
  const dataKeyKey = 'travelz-data-key';

  const hexToBytes = (hex) => {
    const out = new Uint8Array(hex.length / 2);
    for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
    return out;
  };

  const base64ToBytes = (value) => Uint8Array.from(atob(value), c => c.charCodeAt(0));

  async function decryptPayload(payload, keyHex) {
    const key = await crypto.subtle.importKey('raw', hexToBytes(keyHex), { name: 'AES-GCM' }, false, ['decrypt']);
    const plain = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: hexToBytes(payload.ivHex) },
      key,
      base64ToBytes(payload.ciphertextBase64)
    );
    return JSON.parse(new TextDecoder().decode(plain));
  }

  const text = (id, value) => {
    const node = document.getElementById(id);
    if (node) node.textContent = value || '';
  };

  function render(data) {
    text('trip-title', data.title);
    text('trip-route', data.route);
    document.title = `${data.title} · TRAVELZ`;

    const meta = document.getElementById('trip-meta');
    meta.replaceChildren();
    if (data.start && data.end) {
      const span = document.createElement('span');
      span.textContent = `${data.start} → ${data.end}`;
      meta.appendChild(span);
    }

    const schedule = document.getElementById('schedule');
    schedule.replaceChildren();
    for (const item of data.schedule || []) {
      const wrap = document.createElement('article');
      wrap.className = 'timeline-item';
      const time = document.createElement('time');
      time.textContent = item.date || '';
      const h = document.createElement('h4');
      h.textContent = item.title || '';
      const p = document.createElement('p');
      p.textContent = item.note || '';
      wrap.append(time, h, p);
      schedule.appendChild(wrap);
    }

    const money = document.getElementById('money');
    money.replaceChildren();
    for (const item of data.money || []) {
      const row = document.createElement('div');
      row.className = 'money-row';
      const code = document.createElement('div');
      code.className = 'money-code';
      code.textContent = item.currency || '';
      const details = document.createElement('div');
      const target = document.createElement('div');
      target.className = 'money-target';
      target.textContent = item.target || '';
      const note = document.createElement('div');
      note.className = 'muted';
      note.textContent = item.note || '';
      details.append(target, note);
      row.append(code, details);
      money.appendChild(row);
    }

    const connectivity = document.getElementById('connectivity');
    connectivity.replaceChildren();
    for (const item of data.connectivity || []) {
      const li = document.createElement('li');
      li.textContent = item;
      connectivity.appendChild(li);
    }

    const stays = document.getElementById('stays');
    stays.replaceChildren();
    for (const item of data.stays || []) {
      const p = document.createElement('p');
      p.innerHTML = `<strong></strong><br><span class="muted"></span>`;
      p.querySelector('strong').textContent = item.name || '';
      p.querySelector('span').textContent = [item.dates, item.note].filter(Boolean).join(' · ');
      stays.appendChild(p);
    }

    const lounge = document.getElementById('lounge');
    lounge.replaceChildren();
    if (data.lounge) {
      const p = document.createElement('p');
      const strong = document.createElement('strong');
      strong.textContent = data.lounge.title || '';
      const br = document.createElement('br');
      const span = document.createElement('span');
      span.className = 'muted';
      span.textContent = data.lounge.note || '';
      p.append(strong, br, span);
      lounge.appendChild(p);
    }

    const vault = document.getElementById('vault-link');
    if (vault && data.vault) vault.href = data.vault;

    empty.hidden = true;
    content.hidden = false;
  }

  async function load() {
    const keyHex = localStorage.getItem(dataKeyKey);
    if (cfg.enabled && !keyHex) return;

    try {
      const response = await fetch('trip.enc.json', { cache: 'no-store' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const payload = await response.json();
      if (!keyHex) throw new Error('No decryption key');
      const data = await decryptPayload(payload, keyHex);
      render(data);
    } catch (error) {
      console.info('Travelz trip data is not configured yet.', error);
      empty.hidden = false;
      content.hidden = true;
    }
  }

  window.addEventListener('travelz:unlocked', load);
  load();

  if ('serviceWorker' in navigator) navigator.serviceWorker.register('../../sw.js').catch(console.error);
})();
