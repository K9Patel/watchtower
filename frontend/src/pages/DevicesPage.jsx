import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Search, Wifi, WifiOff, Radar, RefreshCw,
  Monitor, ChevronLeft, ChevronRight, Clock, Brain, Map as MapIcon, LayoutGrid
} from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import NetworkTopology from '../components/NetworkTopology';
import LoadingSpinner from '../components/LoadingSpinner';
import AnimatedSearch from '../components/AnimatedSearch';
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
      borderLeft: '3px solid #d4d4d8',
      position: 'relative', overflow: 'hidden',
    }}>
      {/* Sweep animation while scanning */}
      {isScanning && (
        <div style={{
          position: 'absolute', inset: 0,
          background: 'linear-gradient(90deg, transparent, rgba(212,212,216,0.07), transparent)',
          animation: 'scanSweep 1.8s linear infinite', pointerEvents: 'none',
        }} />
      )}

      {/* Full-screen Radar Loader Overlay when scanning */}
      {isScanning && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 9999, background: 'rgba(5, 5, 5, 0.85)',
          backdropFilter: 'blur(8px)', display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center'
        }}>
          <div className="radar-loader"><span></span></div>
          <h2 style={{ color: '#d4d4d8', marginTop: '40px', letterSpacing: '4px', textTransform: 'uppercase', fontSize: '18px', fontWeight: '600' }}>Scanning Network...</h2>
        </div>
      )}

      {/* Static Radar icon for banner */}
      <div style={{
        width: 44, height: 44, borderRadius: '50%', flexShrink: 0,
        background: 'rgba(212,212,216,0.12)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Radar size={22} color="#d4d4d8" />
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
          <div style={{ marginTop: '8px', height: '3px', background: 'rgba(212,212,216,0.15)', borderRadius: '4px', overflow: 'hidden' }}>
            <div style={{
              height: '100%', width: '35%', borderRadius: '4px',
              background: 'linear-gradient(90deg, #a1a1aa, #d4d4d8)',
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
          background: isScanning ? 'rgba(212,212,216,0.15)' : 'linear-gradient(135deg,#a1a1aa,#d4d4d8)',
          color: isScanning ? 'var(--text-secondary)' : '#111',
          fontWeight: 700, fontSize: '13px', cursor: isScanning ? 'not-allowed' : 'pointer',
          flexShrink: 0, boxShadow: isScanning ? 'none' : '0 4px 14px rgba(212,212,216,0.35)',
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
        { label: 'Total Devices',  value: devices.length, color: '#d4d4d8', icon: <Monitor size={16}/> },
        { label: 'Online Now',     value: online,          color: '#d4d4d8', icon: <Wifi size={16}/> },
        { label: 'Offline',        value: devices.length - online, color: '#d4d4d8', icon: <WifiOff size={16}/> },
        { label: 'Auto-Discovered',value: autoDisc,        color: '#d4d4d8', icon: <Radar size={16}/> },
        { label: 'Learning',       value: learning,        color: '#fab570', icon: <Brain size={16}/> },
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
    <div className="device-container">
      <div
        className="device-card"
        id={`device-card-${device.id}`}
        onClick={onClick}
        style={{ cursor: 'pointer' }}
      >
        <div className="device-card-top-tags">
          {isNew && <span className="device-pill device-pill-new">NEW</span>}
          {isLearning && <span className="device-pill device-pill-learning">LEARNING</span>}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem', marginTop: '0.5rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'white', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Monitor size={18} /> {device.deviceName || 'Unknown Device'}
          </h3>
          <span style={{ fontSize: '11px', fontWeight: 600, color: isOnline ? '#22c55e' : 'var(--text-secondary)' }}>
            {isOnline ? '● ONLINE' : '○ OFFLINE'}
          </span>
        </div>

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div className="device-network-row">
            <span className="device-network-label">IP Address</span>
            <span className="device-network-value" style={{ fontFamily: 'monospace' }}>{device.ipAddress || '—'}</span>
          </div>
          <div className="device-network-row">
            <span className="device-network-label">MAC</span>
            <span className="device-network-value" style={{ fontFamily: 'monospace' }}>{device.macAddress || '—'}</span>
          </div>
          <div className="device-network-row">
            <span className="device-network-label">Vendor</span>
            <span className="device-network-value" title={device.vendorName}>
              {device.vendorName ? (device.vendorName.length > 20 ? device.vendorName.substring(0,20) + '...' : device.vendorName) : 'Unknown'}
            </span>
          </div>
          <div className="device-network-row">
            <span className="device-network-label">Last Seen</span>
            <span className="device-network-value">{timeAgo(device.lastSeenAt)}</span>
          </div>
          {device.bandwidth > 0 && (
            <div className="device-network-row">
              <span className="device-network-label">Bandwidth</span>
              <span className="device-network-value" style={{ color: '#fab570' }}>
                {typeof device.bandwidthMbps === 'number' && device.bandwidthMbps > 0
                  ? `${device.bandwidthMbps.toFixed(1)} Mbps`
                  : `${device.bandwidth.toFixed(1)}%`}
              </span>
            </div>
          )}
        </div>
      </div>
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
  const [viewMode,        setViewMode]         = useState('CARDS');
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

  if (loading) return <LoadingSpinner text="DISCOVERING" />;

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 className="btn-amber-text" data-text="Live Network Discovery" style={{ margin: 0, textTransform: 'none' }}>
            <span className="actual-text">Live Network Discovery</span>
            <span aria-hidden="true" className="hover-text">Live Network Discovery</span>
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
          <AnimatedSearch 
            value={searchTerm} 
            onChange={e => setSearchTerm(e.target.value)} 
            placeholder="Search name, IP, MAC..." 
          />
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {[
            { key: 'ALL',     label: 'All' },
            { key: 'ONLINE',  label: 'Online' },
            { key: 'OFFLINE', label: 'Offline' },
            { key: 'AUTO',    label: 'Auto-Discovered' },
            { key: 'LEARNING', label: 'Learning' },
          ].map(({ key, label }) => (
            <button
              key={key}
              id={`filter-${key.toLowerCase()}`}
              onClick={() => setStatusFilter(key)}
              className={`btn-neo ${statusFilter === key ? 'active' : ''}`}
            >
              {label}
            </button>
          ))}
        </div>
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
