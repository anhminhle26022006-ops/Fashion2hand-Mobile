/* Leaflet assets are bundled locally. Only visible map tiles are requested online. */
const map = L.map('map', { zoomControl: true, attributionControl: true });
const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);
const notice = document.getElementById('mapNotice');
tiles.on('tileerror', () => { notice.hidden = false; });
tiles.on('tileload', () => { notice.hidden = true; });
const markers = L.featureGroup().addTo(map);
window.renderTracking = function (points) {
  markers.clearLayers();
  for (const point of points) {
    if (!Number.isFinite(point.lat) || !Number.isFinite(point.lng)
        || Math.abs(point.lat) > 90 || Math.abs(point.lng) > 180) continue;
    const kind = ['pickup', 'delivery', 'checkpoint'].includes(point.kind) ? point.kind : 'checkpoint';
    const label = kind === 'pickup' ? 'G' : kind === 'delivery' ? 'N' : '•';
    const icon = L.divIcon({ className: 'tracking-pin ' + kind, html: label, iconSize: [28, 28], iconAnchor: [17, 17] });
    const popup = document.createElement('div');
    popup.textContent = point.label;
    const marker = L.marker([point.lat, point.lng], { icon, title: point.label }).bindPopup(popup);
    markers.addLayer(marker);
  }
  if (markers.getLayers().length) {
    map.invalidateSize();
    map.fitBounds(markers.getBounds(), { padding: [32, 32], maxZoom: 15 });
  }
  return markers.getLayers().length;
};
