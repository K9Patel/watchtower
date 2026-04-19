import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Search, Wifi, WifiOff, Radar, RefreshCw,
  Monitor, ChevronLeft, ChevronRight, Clock, Brain
} from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import './Pages.css';

function timeAgo(isoString) {
  if (!isoString) return 'never';
  const diff = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000);
  if (diff < 60)   return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return `${Math.floor(diff / 3600)}h ago`;
}

/* ─────────────────────────────────────────────────────────────
   Scan status banner
───────────────────────────────────────────────────────────── */
function ScanBanner({ scanStatus, onScanNow }) {
  const [countdown, setCountdown] = useState(60);
  const isScanning = scanStatus?.scanning ?? false;

  // Countdown timer to next auto-scan
  useEffect(() => {
    if (isScanning) return;
    const id = setInterval(() => setCountdown(c => c <= 1 ? 60 : c - 1), 1000);
    return () => clearInterval(id);
  }, [isScanning]);

  const lastScan = scanStatus?.lastScan;

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: '20px',
      padding: '16px 24px', marginBottom: '24px', borderRadius: '12px',
      background: 'var(--panel-bg)', border: '1px solid var(--panel-border)',
      borderLeft: `3px solid ${isScanning ? '#6366f1' : '#22c55e'}`,
      position: 'relative', overflow: 'hidden',
    }}>
      {/* Sweep animation while scanning */}
      {isScanning && (
        <div style={{
          position: 'absolute', inset: 0,
          background: 'linear-gradient(90deg, transparent, rgba(99,102,241,0.07), transparent)',
          animation: 'scanSweep 1.8s linear infinite', pointerEvents: 'none',
        }} />
      )}

      {/* Radar icon */}
      <div style={{
        width: 44, height: 44, borderRadius: '50%', flexShrink: 0,
        background: isScanning ? 'rgba(99,102,241,0.15)' : 'rgba(34,197,94,0.12)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        animation: isScanning ? 'radarPulse 1.5s ease-in-out infinite' : 'none',
      }}>
        <Radar size={22} color={isScanning ? '#6366f1' : '#22c55e'} />
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '3px' }}>
          {isScanning ? 'Scanning network…' : 'Live Network Discovery'}
        </div>
        <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
          {isScanning
            ? 'Running arp -a · discovering devices · updating database'
            : lastScan
              ? `Last scan: ${new Date(lastScan.scannedAt).toLocaleTimeString()} · found ${lastScan.totalFound} devices · ${lastScan.newDevices} new · next auto-scan in ${countdown}s`
              : 'No scan yet — click Scan Now to discover your network'
          }
        </div>
        {isScanning && (
          <div style={{ marginTop: '8px', height: '3px', background: 'rgba(99,102,241,0.15)', borderRadius: '4px', overflow: 'hidden' }}>
            <div style={{
              height: '100%', width: '35%', borderRadius: '4px',
              background: 'linear-gradient(90deg, #6366f1, #8b5cf6)',
              animation: 'progressIndeterminate 1.6s ease-in-out infinite',
            }} />
          </div>
        )}
      </div>

      <button
        id="scan-now-btn"
        onClick={onScanNow}
        disabled={isScanning}
        style={{
          display: 'flex', alignItems: 'center', gap: '8px',
          padding: '10px 20px', borderRadius: '8px', border: 'none',
          background: isScanning ? 'rgba(99,102,241,0.15)' : 'linear-gradient(135deg,#6366f1,#8b5cf6)',
          color: isScanning ? 'var(--text-secondary)' : 'white',
          fontWeight: 700, fontSize: '13px', cursor: isScanning ? 'not-allowed' : 'pointer',
          flexShrink: 0, boxShadow: isScanning ? 'none' : '0 4px 14px rgba(99,102,241,0.35)',
          transition: 'all 0.2s',
        }}>
        <RefreshCw size={15} style={{ animation: isScanning ? 'spin 1s linear infinite' : 'none' }} />
        {isScanning ? 'Scanning…' : 'Scan Now'}
      </button>
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────
   Stat pills row
───────────────────────────────────────────────────────────── */
function StatsPills({ devices, scanStatus }) {
  const online    = devices.filter(d => d.status === 'ONLINE').length;
  const autoDisc  = devices.filter(d => d.isAutoDiscovered).length;
  const learning  = devices.filter(d => !d.baselineReady).length;

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: '14px', marginBottom: '24px' }}>
      {[
        { label: 'Total Devices',  value: devices.length, color: '#6366f1', icon: <Monitor size={16}/> },
        { label: 'Online Now',     value: online,          color: '#22c55e', icon: <Wifi size={16}/> },
        { label: 'Offline',        value: devices.length - online, color: '#6b7280', icon: <WifiOff size={16}/> },
        { label: 'Auto-Discovered',value: autoDisc,        color: '#f59e0b', icon: <Radar size={16}/> },
        { label: 'Learning',       value: learning,        color: '#a855f7', icon: <Brain size={16}/> },
      ].map(({ label, value, color, icon }) => (
        <div key={label} className="glass-panel" style={{ padding: '14px 18px', display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ width: 36, height: 36, borderRadius: '9px', background: `${color}18`, color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {icon}
          </div>
          <div>
            <div style={{ fontSize: '20px', fontWeight: 800, color }}>{value}</div>
            <div style={{ fontSize: '10px', color: 'var(--text-secondary)', fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase' }}>{label}</div>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────
   Device card
───────────────────────────────────────────────────────────── */
function DeviceCard({ device, onClick }) {
  const isOnline  = device.status === 'ONLINE' && device.isActive;
  const isLearning = !device.baselineReady;
  // "NEW" = auto-discovered and first seen within the last 90 seconds
  const isNew = device.isAutoDiscovered &&
    device.lastSeenAt &&
    (Date.now() - new Date(device.lastSeenAt).getTime()) < 90_000;

  return (
    <div
      className="device-card"
      id={`device-card-${device.id}`}
      onClick={onClick}
      style={{
        cursor: 'pointer', position: 'relative', overflow: 'hidden',
        borderColor: isNew ? 'rgba(99,102,241,0.55)' : undefined,
        boxShadow: isNew ? '0 0 0 1px rgba(99,102,241,0.2),0 4px 20px rgba(99,102,241,0.12)' : undefined,
      }}
    >
      {/* Generic accent bar */}
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '3px', background: `linear-gradient(90deg,#4b5563,#9ca3af)` }} />

      <div className="device-card-top-tags">
        {isNew && <span className="device-pill device-pill-new">★ New</span>}
        {isLearning && (
          <span className="device-pill device-pill-learning">
            <Brain size={11} />
            Learning Baseline
          </span>
        )}
      </div>

      <div className="device-header" style={{ marginTop: '14px', paddingRight: '8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0, flex: 1 }}>
          {/* Avatar */}
          <div style={{
            width: 38, height: 38, borderRadius: '10px', flexShrink: 0, fontSize: '18px',
            background: `linear-gradient(135deg,#4b556320,#9ca3af20)`,
            border: `1px solid #4b556340`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Monitor size={18} color="var(--text-secondary)" />
          </div>
          <div style={{ overflow: 'hidden', flex: 1, minWidth: 0 }}>
            <h3 className="device-name" style={{ fontSize: '14px', marginBottom: '1px' }} title={device.deviceName}>{device.deviceName}</h3>
            {device.vendorName && (
              <div style={{ fontSize: '11px', fontWeight: 600, color: '#9ca3af', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={device.vendorName}>{device.vendorName}</div>
            )}
          </div>
        </div>
        <span
          className={`status-badge ${isOnline ? 'active' : 'inactive'}`}
          style={{
            alignSelf: 'flex-start',
            marginTop: '4px',
          }}
        >
          {isOnline ? '● Online' : '○ Offline'}
        </span>
      </div>

      <div className="device-details" style={{ marginTop: '14px' }}>
        <div className="detail-row">
          <span className="detail-label">IP Address</span>
          <span className="detail-value font-mono" style={{ fontSize: '13px', whiteSpace: 'nowrap' }}>{device.ipAddress}</span>
        </div>
        <div className="detail-row">
          <span className="detail-label">MAC</span>
          <span className="font-mono" style={{ fontSize: '11px', color: 'var(--text-secondary)' }} title={`Full MAC: ${device.macAddress || 'N/A'}`}>
            {device.macAddress || '—'}
          </span>
        </div>
        {device.vendorName && (
          <div className="detail-row">
            <span className="detail-label">Vendor</span>
            <span style={{
              fontSize: '11px', fontWeight: 600, padding: '2px 7px', borderRadius: '4px',
              background: `#4b556320`, color: '#9ca3af',
              maxWidth: '220px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }} title={device.vendorName}>{device.vendorName}</span>
          </div>
        )}
        {device.osType && (
          <div className="detail-row">
            <span className="detail-label">OS</span>
            <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{device.osType}</span>
          </div>
        )}
        <div className="detail-row">
          <span className="detail-label" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Clock size={11} /> Last Seen
          </span>
          <span style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
            {timeAgo(device.lastSeenAt)}
          </span>
        </div>
        {device.bandwidth > 0 && (
          <div className="detail-row">
            <span className="detail-label">Bandwidth</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
              <div style={{ width: '55px', height: '4px', background: 'rgba(0,0,0,0.2)', borderRadius: '2px', overflow: 'hidden' }}>
                <div style={{
                  width: `${Math.min(device.bandwidth, 100)}%`, height: '100%', borderRadius: '2px',
                  background: device.bandwidth > 50 ? '#ef4444' : device.bandwidth > 20 ? '#f59e0b' : '#22c55e',
                }} />
              </div>
              <span style={{ fontSize: '12px', fontWeight: 600, color: '#6366f1' }}>
                {typeof device.bandwidthMbps === 'number' && device.bandwidthMbps > 0
                  ? `${device.bandwidthMbps.toFixed(1)} Mbps`
                  : `${device.bandwidth.toFixed(1)}%`}
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Auto-discovered badge */}
      {device.isAutoDiscovered && !isNew && (
        <div style={{ marginTop: '12px', borderTop: '1px solid var(--panel-border)', paddingTop: '10px' }}>
          <span style={{ fontSize: '10px', color: 'var(--text-secondary)', fontWeight: 600, letterSpacing: '0.05em' }}>
            📡 AUTO-DISCOVERED
          </span>
        </div>
      )}
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────
   Main page
───────────────────────────────────────────────────────────── */
const DevicesPage = () => {
  const navigate = useNavigate();
  const [devices,         setDevices]         = useState([]);
  const [scanStatus,      setScanStatus]       = useState(null);
  const [filteredDevices, setFilteredDevices]  = useState([]);
  const [loading,         setLoading]          = useState(true);
  const [searchTerm,      setSearchTerm]       = useState('');
  const [statusFilter,    setStatusFilter]     = useState('ALL');
  const [currentPage,     setCurrentPage]      = useState(1);
  const pollRef = useRef(null);
  const ITEMS   = 12;

  const fetchData = useCallback(async () => {
    try {
      const [liveRes, statusRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/devices/live`),
        axios.get(`${API_BASE_URL}/devices/scan/status`),
      ]);
      setDevices(liveRes.data);
      setScanStatus(statusRes.data);
      setLoading(false);
    } catch (err) {
      console.error('DevicesPage fetch error:', err);
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    pollRef.current = setInterval(fetchData, scanStatus?.scanning ? 3000 : 10000);
    return () => clearInterval(pollRef.current);
  }, [fetchData]);

  // Re-poll faster while a scan is running
  useEffect(() => {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = setInterval(fetchData, scanStatus?.scanning ? 3000 : 10000);
    return () => clearInterval(pollRef.current);
  }, [scanStatus?.scanning, fetchData]);

  useEffect(() => {
    let f = devices;
    if (statusFilter === 'ONLINE')       f = f.filter(d => d.status === 'ONLINE');
    else if (statusFilter === 'OFFLINE') f = f.filter(d => d.status !== 'ONLINE');
    else if (statusFilter === 'AUTO')    f = f.filter(d => d.isAutoDiscovered);
    else if (statusFilter === 'LEARNING') f = f.filter(d => !d.baselineReady);
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      f = f.filter(d =>
        d.deviceName?.toLowerCase().includes(q) ||
        d.macAddress?.toLowerCase().includes(q)  ||
        d.ipAddress?.toLowerCase().includes(q)   ||
        d.vendorName?.toLowerCase().includes(q)
      );
    }
    setFilteredDevices(f);
    setCurrentPage(1);
  }, [searchTerm, statusFilter, devices]);

  const handleScanNow = async () => {
    try {
      await axios.post(`${API_BASE_URL}/devices/scan`);
      setScanStatus(s => ({ ...s, scanning: true }));
      if (pollRef.current) clearInterval(pollRef.current);
      pollRef.current = setInterval(fetchData, 3000);
    } catch (err) {
      console.error('Scan trigger error:', err);
    }
  };

  const paginated   = filteredDevices.slice((currentPage - 1) * ITEMS, currentPage * ITEMS);
  const totalPages  = Math.ceil(filteredDevices.length / ITEMS);

  if (loading) return (
    <div className="loading-screen">
      <div style={{ width: 56, height: 56, borderRadius: '50%', border: '3px solid rgba(99,102,241,0.2)', borderTopColor: '#6366f1', animation: 'spin 1s linear infinite' }} />
      <p style={{ color: 'var(--text-secondary)' }}>Discovering network devices…</p>
    </div>
  );

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Radar size={30} color="#6366f1" /> Live Network Discovery
          </h1>
          <p className="page-subtitle">
            ARP auto-discovery · MAC identification · Real-time status · Stale cleanup every 5 min
          </p>
        </div>
      </div>

      <ScanBanner scanStatus={scanStatus} onScanNow={handleScanNow} />
      <StatsPills devices={devices} scanStatus={scanStatus} />

      {/* Filters */}
      <div className="glass-panel filters-panel" style={{ marginBottom: '24px', flexWrap: 'wrap' }}>
        <div className="filter-group">
          <Search size={16} color="var(--text-secondary)" />
          <input
            id="device-search"
            type="text"
            placeholder="Search name, IP, MAC, vendor…"
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            className="search-input"
          />
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {[
            { key: 'ALL',     label: 'All' },
            { key: 'ONLINE',  label: '● Online' },
            { key: 'OFFLINE', label: '○ Offline' },
            { key: 'AUTO',    label: '📡 Auto-Discovered' },
            { key: 'LEARNING', label: '🧠 Learning' },
          ].map(({ key, label }) => (
            <button
              key={key}
              id={`filter-${key.toLowerCase()}`}
              onClick={() => setStatusFilter(key)}
              style={{
                padding: '6px 14px', borderRadius: '6px', border: 'none',
                fontWeight: 600, fontSize: '12px', cursor: 'pointer',
                background: statusFilter === key ? '#6366f1' : 'rgba(51,65,85,0.4)',
                color: statusFilter === key ? 'white' : 'var(--text-secondary)',
              }}>
              {label}
            </button>
          ))}
        </div>
        <div className="filter-info">{paginated.length} of {filteredDevices.length} devices</div>
      </div>

      {/* Grid */}
      <div className="devices-grid">
        {paginated.map(device => (
          <DeviceCard
            key={device.id}
            device={device}
            onClick={() => navigate(`/devices/${device.id}`)}
          />
        ))}
      </div>

      {filteredDevices.length === 0 && (
        <div className="empty-state">
          <Radar size={48} style={{ opacity: 0.3, marginBottom: '16px' }} />
          <p>No devices found. Try scanning the network.</p>
          <button onClick={handleScanNow} className="btn-primary" style={{ marginTop: '16px' }}>
            Scan Now
          </button>
        </div>
      )}

      {totalPages > 1 && (
        <div className="pagination">
          <button onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1} className="pagination-btn">
            <ChevronLeft size={18} /> Previous
          </button>
          <div className="pagination-info">Page {currentPage} of {totalPages}</div>
          <button onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages} className="pagination-btn">
            Next <ChevronRight size={18} />
          </button>
        </div>
      )}
    </div>
  );
};

export default DevicesPage;
