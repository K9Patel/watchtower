import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { CircleMarker, MapContainer, Popup, TileLayer } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { API_BASE_URL } from '../config/api';

const DEFAULT_CENTER = [20, 0];

const isPrivateIp = (ip) => {
  if (!ip) return true;
  return (
    ip.startsWith('10.') ||
    ip.startsWith('192.168.') ||
    /^172\.(1[6-9]|2\d|3[0-1])\./.test(ip) ||
    ip === '127.0.0.1'
  );
};

const hashToUnit = (input) => {
  let hash = 0;
  for (let i = 0; i < input.length; i += 1) {
    hash = (hash << 5) - hash + input.charCodeAt(i);
    hash |= 0;
  }
  return ((hash >>> 0) % 10000) / 10000;
};

const jitterAround = (center, ip) => {
  const angle = hashToUnit(`${ip}-angle`) * 2 * Math.PI;
  const distanceKm = 0.5 + hashToUnit(`${ip}-distance`) * 8;
  const latOffset = (distanceKm / 111) * Math.sin(angle);
  const lngOffset = (distanceKm / (111 * Math.cos((center[0] * Math.PI) / 180) || 1)) * Math.cos(angle);
  return [center[0] + latOffset, center[1] + lngOffset];
};

const locateBrowser = () => new Promise((resolve) => {
  if (!navigator.geolocation) {
    resolve(null);
    return;
  }

  navigator.geolocation.getCurrentPosition(
    (pos) => resolve([pos.coords.latitude, pos.coords.longitude]),
    () => resolve(null),
    { enableHighAccuracy: false, timeout: 6000, maximumAge: 60000 }
  );
});

const resolvePublicIpGeo = async (ip) => {
  try {
    const res = await fetch(`https://ipapi.co/${ip}/json/`);
    const data = await res.json();
    if (!Number.isFinite(data?.latitude) || !Number.isFinite(data?.longitude)) {
      return null;
    }
    return {
      coords: [Number(data.latitude), Number(data.longitude)],
      city: data.city || 'Unknown city',
      country: data.country_name || 'Unknown country',
      source: 'public-ip',
    };
  } catch (_) {
    return null;
  }
};

const DeviceGeographyMap = () => {
  const [loading, setLoading] = useState(true);
  const [center, setCenter] = useState(DEFAULT_CENTER);
  const [markers, setMarkers] = useState([]);
  const [message, setMessage] = useState('Resolving device locations...');

  useEffect(() => {
    let active = true;

    const buildMapData = async () => {
      try {
        const [browserCoords, devicesRes] = await Promise.all([
          locateBrowser(),
          axios.get(`${API_BASE_URL}/devices/active`),
        ]);

        const userCenter = browserCoords || DEFAULT_CENTER;
        if (active) {
          setCenter(userCenter);
        }

        const devices = Array.isArray(devicesRes.data) ? devicesRes.data : [];
        const publicIpLookups = [];

        for (const device of devices) {
          const ip = device.ipAddress || '';
          if (!ip || isPrivateIp(ip)) {
            publicIpLookups.push(Promise.resolve(null));
          } else {
            publicIpLookups.push(resolvePublicIpGeo(ip));
          }
        }

        const publicGeoResults = await Promise.all(publicIpLookups);
        const mapped = devices.map((device, idx) => {
          const ip = device.ipAddress || 'N/A';
          const publicGeo = publicGeoResults[idx];

          if (publicGeo?.coords) {
            return {
              id: device.id,
              label: device.deviceName || ip,
              ip,
              status: device.status || 'UNKNOWN',
              vendor: device.vendorName || 'Unknown',
              coords: publicGeo.coords,
              locationText: `${publicGeo.city}, ${publicGeo.country}`,
              source: 'Public IP geolocation',
            };
          }

          return {
            id: device.id,
            label: device.deviceName || ip,
            ip,
            status: device.status || 'UNKNOWN',
            vendor: device.vendorName || 'Unknown',
            coords: jitterAround(userCenter, ip),
            locationText: browserCoords ? 'Local network (estimated around your device)' : 'Local network (fallback estimate)',
            source: browserCoords ? 'Browser geolocation + LAN approximation' : 'Default map center + LAN approximation',
          };
        });

        if (!active) return;

        setMarkers(mapped);
        if (!browserCoords) {
          setMessage('Location permission denied or unavailable. Showing estimated local map around fallback center.');
        } else {
          setMessage('Your device location is used as center. Local LAN devices are plotted as near-by estimates.');
        }
      } catch (_) {
        if (!active) return;
        setMessage('Unable to load active devices for geography map.');
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    buildMapData();
    const id = setInterval(buildMapData, 60000);
    return () => {
      active = false;
      clearInterval(id);
    };
  }, []);

  const activeCount = useMemo(
    () => markers.filter((m) => (m.status || '').toUpperCase() === 'ONLINE').length,
    [markers]
  );

  return (
    <div className="glass-panel geography-map-panel">
      <div className="geography-map-header">
        <div>
          <h3>Device Geography Map</h3>
          <p>Maps devices relative to your own location and resolves public IP locations when available.</p>
        </div>
        <span className="geography-map-badge">REFRESH 60s</span>
      </div>

      <p className="geography-map-note">{message}</p>

      <div className="geography-map-stats">
        <span><b>{markers.length}</b> devices plotted</span>
        <span><b>{activeCount}</b> online</span>
      </div>

      <div className="geography-map-canvas-wrap">
        {loading ? (
          <div className="attack-path-placeholder">Building geography map...</div>
        ) : (
          <MapContainer
            center={center}
            zoom={13}
            scrollWheelZoom
            className="device-geography-map"
          >
            <TileLayer
              attribution="&copy; OpenStreetMap contributors"
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />

            <CircleMarker center={center} radius={9} pathOptions={{ color: '#22d3ee', fillColor: '#06b6d4', fillOpacity: 0.9 }}>
              <Popup>
                <strong>Your Device</strong>
                <br />
                Map center (browser geolocation)
              </Popup>
            </CircleMarker>

            {markers.map((m) => {
              const online = (m.status || '').toUpperCase() === 'ONLINE';
              return (
                <CircleMarker
                  key={m.id}
                  center={m.coords}
                  radius={online ? 7 : 5}
                  pathOptions={{
                    color: online ? '#22c55e' : '#94a3b8',
                    fillColor: online ? '#22c55e' : '#94a3b8',
                    fillOpacity: 0.8,
                  }}
                >
                  <Popup>
                    <strong>{m.label}</strong>
                    <br />
                    IP: {m.ip}
                    <br />
                    Status: {m.status}
                    <br />
                    Vendor: {m.vendor}
                    <br />
                    Location: {m.locationText}
                    <br />
                    Source: {m.source}
                  </Popup>
                </CircleMarker>
              );
            })}
          </MapContainer>
        )}
      </div>
    </div>
  );
};

export default DeviceGeographyMap;
