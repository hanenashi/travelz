(() => {
  const host = document.getElementById('trip-list');
  if (!host) return;

  const fmt = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' });

  function countdown(start, end) {
    const now = new Date();
    const s = new Date(`${start}T00:00:00`);
    const e = new Date(`${end}T23:59:59`);
    const day = 86400000;
    if (now < s) {
      const n = Math.ceil((s - now) / day);
      return `${n} day${n === 1 ? '' : 's'} to go`;
    }
    if (now <= e) return 'on the road';
    return 'completed';
  }

  fetch('trips/index.json', { cache: 'no-store' })
    .then(r => {
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return r.json();
    })
    .then(data => {
      host.innerHTML = data.trips.map(trip => `
        <a class="trip-card" href="${trip.href}">
          <span class="chip live">${countdown(trip.start, trip.end)}</span>
          <div class="trip-route">${trip.route}</div>
          <div class="meta">
            <span>${fmt.format(new Date(`${trip.start}T00:00:00`))} → ${fmt.format(new Date(`${trip.end}T00:00:00`))}</span>
            <span>${trip.label}</span>
          </div>
        </a>
      `).join('');
    })
    .catch(error => {
      console.error(error);
      host.innerHTML = '<div class="empty-card">Trip data failed to load.</div>';
    });

  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(console.error);
})();
