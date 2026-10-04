(() => {
  const cfg = window.TRAVELZ_GATE || {};
  const gate = document.getElementById('gate');
  const app = document.getElementById('app');
  const form = document.getElementById('gate-form');
  const input = document.getElementById('gate-pin');
  const message = document.getElementById('gate-message');
  const storageKey = 'travelz-unlocked-until';

  const hexToBytes = (hex) => {
    const out = new Uint8Array(hex.length / 2);
    for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
    return out;
  };

  const bytesToHex = (bytes) => Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');

  async function derive(pin) {
    const material = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(pin),
      'PBKDF2',
      false,
      ['deriveBits']
    );
    const bits = await crypto.subtle.deriveBits(
      {
        name: 'PBKDF2',
        hash: 'SHA-256',
        salt: hexToBytes(cfg.saltHex),
        iterations: cfg.iterations || 250000
      },
      material,
      256
    );
    return bytesToHex(new Uint8Array(bits));
  }

  function showApp() {
    gate.hidden = true;
    app.hidden = false;
  }

  function lock() {
    localStorage.removeItem(storageKey);
    app.hidden = true;
    gate.hidden = false;
    if (input) {
      input.value = '';
      input.focus();
    }
  }

  if (!cfg.enabled || !cfg.saltHex || !cfg.verifierHex) {
    showApp();
  } else {
    const unlockedUntil = Number(localStorage.getItem(storageKey) || 0);
    if (Date.now() < unlockedUntil) showApp();
  }

  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    message.textContent = '';
    const pin = input.value.trim();
    if (!pin) return;

    try {
      const verifier = await derive(pin);
      if (verifier === cfg.verifierHex) {
        const hours = Number(cfg.rememberHours || 24);
        localStorage.setItem(storageKey, String(Date.now() + hours * 60 * 60 * 1000));
        input.value = '';
        showApp();
      } else {
        message.textContent = 'nope';
        gate.classList.remove('shake');
        void gate.offsetWidth;
        gate.classList.add('shake');
        input.select();
      }
    } catch (error) {
      console.error(error);
      message.textContent = 'unlock failed';
    }
  });

  document.addEventListener('click', (event) => {
    if (event.target.closest('[data-lock]')) lock();
  });
})();
