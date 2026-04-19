import React, { useMemo, useState } from 'react';
import { Circle, CircleMarker, MapContainer, Polyline, Popup, TileLayer } from 'react-leaflet';
import { LocateFixed, Navigation, RefreshCw } from 'lucide-react';
import 'leaflet/dist/leaflet.css';

const DEFAULT_CENTER = [20, 0];

const toNumber = (v) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};

const hashToUnit = (input) => {
  let hash = 0;
  for (let i = 0; i < input.length; i += 1) {
    hash = (hash << 5) - hash + input.charCodeAt(i);
    hash |= 0;
  }
  return ((hash >>> 0) % 10000) / 10000;
};

const jitterAround = (center, ip, minMeters = 12, maxMeters = 55) => {
  const base = ip || 'unknown';
  const angle = hashToUnit(`${base}-angle`) * 2 * Math.PI;
  const distanceMeters = minMeters + hashToUnit(`${base}-distance`) * Math.max(1, (maxMeters - minMeters));
  const distanceKm = distanceMeters / 1000;
  const latOffset = (distanceKm / 111) * Math.sin(angle);
  const cosLat = Math.cos((center[0] * Math.PI) / 180);
  const lngOffset = (distanceKm / (111 * (Math.abs(cosLat) < 0.05 ? 0.05 : cosLat))) * Math.cos(angle);
  return [center[0] + latOffset, center[1] + lngOffset];
};

const spreadAround = (coords, index, total) => {
  if (total <= 1) return coords;
  const angle = (2 * Math.PI * index) / total;
  const radiusMeters = 8 + Math.min(total, 10) * 3;
  const radiusKm = radiusMeters / 1000;
  const latOffset = (radiusKm / 111) * Math.sin(angle);
  const cosLat = Math.cos((coords[0] * Math.PI) / 180);
  const lngOffset = (radiusKm / (111 * (Math.abs(cosLat) < 0.05 ? 0.05 : cosLat))) * Math.cos(angle);
  return [coords[0] + latOffset, coords[1] + lngOffset];
};

const getLocationText = (device) => {
  if (device.location) return device.location;
  const parts = [device.city, device.region, device.country].filter(Boolean);
  return parts.length ? parts.join(', ') : 'Unknown location';
};

const mapMarkerStyle = (device) => {
  const online = device.status === 'ONLINE';
  const hasAlert = Number(device.unresolvedAlerts || 0) > 0;

  if (!online) {
    return { color: '#94a3b8', fillColor: '#94a3b8', fillOpacity: 0.75 };
  }
  if (hasAlert) {
    return { color: '#ef4444', fillColor: '#ef4444', fillOpacity: 0.85 };
  }
  return { color: '#22c55e', fillColor: '#22c55e', fillOpacity: 0.85 };
};

const distanceKmBetween = (a, b) => {
  const r = 6371;
  const dLat = ((b[0] - a[0]) * Math.PI) / 180;
  const dLng = ((b[1] - a[1]) * Math.PI) / 180;
  const lat1 = (a[0] * Math.PI) / 180;
  const lat2 = (b[0] * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.sin(dLng / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
  return 2 * r * Math.asin(Math.sqrt(h));
};

const routeEngineForMode = (mode) => {
  if (mode === 'WALKING') return 'fossgis_osrm_foot';
  return 'fossgis_osrm_car';
};

const geoQualityBadge = (quality, confidence) => {
  if (quality === 'HIGH' || confidence >= 75) return { label: 'High confidence', tone: 'high' };
  if (quality === 'MEDIUM' || confidence >= 50) return { label: 'Medium confidence', tone: 'medium' };
  return { label: 'Low confidence', tone: 'low' };
};

const DeviceMapView = ({ devices, userCoords, userAccuracyMeters, onDeviceNavigate, onRelocate, loading }) => {
  const [selected, setSelected] = useState(null);
  const [routeMode, setRouteMode] = useState('DRIVING');
  const [hideEstimated, setHideEstimated] = useState(false);

  const mapCenter = userCoords || DEFAULT_CENTER;

  const plottedDevices = useMemo(() => {
    const list = devices || [];

    // Group by geo coordinate so devices sharing the same lookup point can be spread visually.
    const groups = new Map();
    list.forEach((d) => {
      const lat = toNumber(d.lat);
      const lng = toNumber(d.lng);
      if (lat === null || lng === null) return;
      const key = `${lat.toFixed(4)},${lng.toFixed(4)}`;
      const arr = groups.get(key) || [];
      arr.push(d.id);
      groups.set(key, arr);
    });

    return list.map((d) => {
      const lat = toNumber(d.lat);
      const lng = toNumber(d.lng);
      const hasGeo = lat !== null && lng !== null;
      let coords = hasGeo ? [lat, lng] : jitterAround(mapCenter, d.ipAddress, userCoords ? 15 : 40, userCoords ? 90 : 250);

      // Private LAN IP geolocation is gateway-level; cluster devices around the user's point in meters.
      if (hasGeo && d.isPrivateGeo && userCoords) {
        coords = jitterAround(userCoords, d.ipAddress, 10, 45);
      }

      if (hasGeo) {
        const key = `${lat.toFixed(4)},${lng.toFixed(4)}`;
        const ids = groups.get(key) || [];
        const idx = ids.indexOf(d.id);
        coords = spreadAround(coords, Math.max(0, idx), ids.length);
      }

      const bandwidth = Number(d.bandwidthMbps || 0);
      const radius = Math.max(5, Math.min(18, 5 + bandwidth / 4));

      return {
        ...d,
        coords,
        hasGeo,
        geoConfidence: Number(d.geoConfidence ?? (hasGeo ? (d.isPrivateGeo ? 45 : 68) : 25)),
        geoQuality: d.geoQuality || (hasGeo ? (d.isPrivateGeo ? 'MEDIUM' : 'HIGH') : 'LOW'),
        geoSourceLabel: d.geoSourceLabel || (hasGeo ? 'IP geolocation' : 'Estimated local topology'),
        radius,
      };
    });
  }, [devices, mapCenter]);

  const visibleDevices = useMemo(() => {
    if (!hideEstimated) return plottedDevices;
    return plottedDevices.filter((d) => d.hasGeo);
  }, [hideEstimated, plottedDevices]);

  const selectedDevice = visibleDevices.find((d) => d.id === selected) || plottedDevices.find((d) => d.id === selected);
  const routePath = selectedDevice && userCoords ? [userCoords, selectedDevice.coords] : null;
  const routeDistanceKm = routePath ? distanceKmBetween(routePath[0], routePath[1]) : null;

  const openExternalDirections = (provider) => {
    if (!selectedDevice || !userCoords) return;

    const origin = `${userCoords[0]},${userCoords[1]}`;
    const destination = `${selectedDevice.coords[0]},${selectedDevice.coords[1]}`;

    let url = '';
    if (provider === 'google') {
      const mode = routeMode === 'WALKING' ? 'walking' : 'driving';
      url = `https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&travelmode=${mode}`;
    } else {
      const engine = routeEngineForMode(routeMode);
      url = `https://www.openstreetmap.org/directions?engine=${encodeURIComponent(engine)}&route=${encodeURIComponent(origin)}%3B${encodeURIComponent(destination)}`;
    }

    window.open(url, '_blank', 'noopener,noreferrer');
  };

  return (
    <div className="glass-panel devices-map-panel">
      <div className="devices-map-header">
        <div>
          <h3>Device Geo Map</h3>
          <p>Click any device pin to draw route from your location. Right-click pin to re-locate it.</p>
        </div>
        <div className="devices-map-badges">
          <span className="devices-map-badge"><LocateFixed size={12} /> You</span>
          <span className="devices-map-badge"><Navigation size={12} /> Route on click</span>
          {typeof userAccuracyMeters === 'number' && (
            <span className="devices-map-badge accuracy">GPS +/- {Math.round(userAccuracyMeters)}m</span>
          )}
          <button
            type="button"
            className={`devices-map-badge toggle ${hideEstimated ? 'active' : ''}`}
            onClick={() => setHideEstimated((v) => !v)}
          >
            {hideEstimated ? 'Showing geocoded only' : 'Show all (incl estimated)'}
          </button>
        </div>
      </div>

      <div className="devices-map-legend">
        <span><i style={{ background: '#22c55e' }} />Online</span>
        <span><i style={{ background: '#ef4444' }} />Alert</span>
        <span><i style={{ background: '#94a3b8' }} />Offline</span>
        <span><i style={{ background: '#16a34a' }} />High confidence</span>
        <span><i style={{ background: '#f59e0b' }} />Medium confidence</span>
        <span><i style={{ background: '#ef4444' }} />Low confidence</span>
      </div>

      <div className="devices-map-canvas-wrap">
        {loading ? (
          <div className="attack-path-placeholder">Loading map view...</div>
        ) : (
          <MapContainer center={mapCenter} zoom={userCoords ? 13 : 3} scrollWheelZoom className="devices-map-canvas">
            <TileLayer
              attribution="&copy; OpenStreetMap contributors"
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />

            {userCoords && (
              <>
                {typeof userAccuracyMeters === 'number' && userAccuracyMeters > 0 && (
                  <Circle
                    center={userCoords}
                    radius={userAccuracyMeters}
                    pathOptions={{ color: '#38bdf8', fillColor: '#38bdf8', fillOpacity: 0.1, weight: 1 }}
                  />
                )}
                <CircleMarker
                  center={userCoords}
                  radius={8}
                  pathOptions={{ color: '#38bdf8', fillColor: '#0ea5e9', fillOpacity: 0.9 }}
                >
                  <Popup>
                    <strong>Your Device</strong>
                    <br />
                    Route source anchor
                    <br />
                    {typeof userAccuracyMeters === 'number' ? `GPS accuracy +/- ${Math.round(userAccuracyMeters)}m` : 'GPS accuracy unavailable'}
                  </Popup>
                </CircleMarker>
              </>
            )}

            {visibleDevices.map((device) => {
              const confidence = geoQualityBadge(device.geoQuality, device.geoConfidence);
              return (
                <CircleMarker
                  key={device.id}
                  center={device.coords}
                  radius={device.radius}
                  pathOptions={mapMarkerStyle(device)}
                  eventHandlers={{
                    click: () => setSelected(device.id),
                    contextmenu: () => onRelocate?.(device.id),
                  }}
                >
                  <Popup>
                    <strong>{device.deviceName || device.ipAddress}</strong>
                    <br />
                    {device.vendorName || 'Unknown vendor'}
                    <br />
                    {device.ipAddress}
                    <br />
                    {getLocationText(device)}
                    <br />
                    {typeof device.bandwidthMbps === 'number' ? `${device.bandwidthMbps.toFixed(1)} Mbps` : '0.0 Mbps'}
                    <br />
                    Source: {device.geoSourceLabel}
                    <br />
                    Confidence: {Math.round(device.geoConfidence)}% ({confidence.label})
                    <br />
                    <span className={`geo-quality-tag ${confidence.tone}`}>{confidence.label}</span>
                    <br />
                    <button
                      type="button"
                      className="devices-map-action"
                      onClick={() => onDeviceNavigate?.(device.id)}
                    >
                      Open Device
                    </button>
                    <button
                      type="button"
                      className="devices-map-action ghost"
                      onClick={() => onRelocate?.(device.id)}
                    >
                      <RefreshCw size={12} /> Re-locate
                    </button>
                  </Popup>
                </CircleMarker>
              );
            })}

            {routePath && (
              <Polyline positions={routePath} pathOptions={{ color: '#38bdf8', weight: 3, dashArray: '7 6' }} />
            )}
          </MapContainer>
        )}
      </div>

      {selectedDevice && routeDistanceKm !== null && (
        <div className="devices-map-route">
          <div className="devices-route-top">
            <span>
              Route: <strong>{(selectedDevice.deviceName || selectedDevice.ipAddress)}</strong> from your device, approx {routeDistanceKm.toFixed(2)} km
            </span>
            <div className="devices-route-mode">
              <button
                type="button"
                className={`devices-route-mode-btn ${routeMode === 'DRIVING' ? 'active' : ''}`}
                onClick={() => setRouteMode('DRIVING')}
              >
                Driving
              </button>
              <button
                type="button"
                className={`devices-route-mode-btn ${routeMode === 'WALKING' ? 'active' : ''}`}
                onClick={() => setRouteMode('WALKING')}
              >
                Walking
              </button>
            </div>
          </div>
          {selectedDevice.geoConfidence < 50 && (
            <div className="devices-route-warning">
              This destination is low-confidence estimated location. Use Re-locate or geocoded-only mode for stronger accuracy.
            </div>
          )}
          <div className="devices-route-links">
            <button type="button" className="devices-map-action" onClick={() => openExternalDirections('google')}>
              Open in Google Maps
            </button>
            <button type="button" className="devices-map-action ghost" onClick={() => openExternalDirections('osm')}>
              Open in OpenStreetMap
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default DeviceMapView;
