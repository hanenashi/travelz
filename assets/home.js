(() => {
  const host = document.getElementById('trip-list');
  if (!host) return;

  fetch('trips/index.json', { cache: 'no-store' })
    .then(r => {
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return r.json();
    })
    .then(data => {
      host.innerHTML = data.trips.map(trip => `
        <a class="trip-card" href="${trip.href}">
          <span class="chip live">ready</span>
          <div class="trip-route">${trip.route}</div>
          <div class="meta"><span>${trip.label}</span></div>
        </a>
      `).join('');
    })
    .catch(error => {
      console.error(error);
      host.innerHTML = '<div class="empty-card">Trip index failed to load.</div>';
    });

  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(console.error);
})();
