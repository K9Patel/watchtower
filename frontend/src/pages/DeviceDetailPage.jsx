import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { ArrowLeft, Activity, ShieldAlert, Clock, Network, Server, HardDrive, AlertTriangle, Wifi, TrendingUp } from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import TrafficBadge from '../components/TrafficBadge';
import './Pages.css';

const DeviceDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [dpiSnapshot, setDpiSnapshot] = useState(null);
  const [dpiHistory, setDpiHistory] = useState([]);
  const [dpiHistoryRange, setDpiHistoryRange] = useState('1h');
  const [loading, setLoading] = useState(true);
  const [dpiLoading, setDpiLoading] = useState(false);
  const [error, setError] = useState(null);
  const pollRef = useRef(null);

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const fetchDeviceDetails = useCallback(async () => {
    try {
      const res = await axios.get(`${API_BASE_URL}/devices/${id}/details`);
      setData(res.data);
      setError(null);
      setLoading(false);
    } catch (err) {
      const status = err.response?.status;
      if (status === 404) {
        stopPolling();
        setError('Device not found. It may have gone offline and been removed.');
      } else {
        console.error('Error fetching device details:', err);
        setError('Failed to load device details.');
      }
      setLoading(false);
    }
  }, [id]);

  const fetchDpiData = useCallback(async () => {
    if (!id) return;
    try {
      setDpiLoading(true);
      const [snapshotRes, historyRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/dpi/device/${id}`),
        axios.get(`${API_BASE_URL}/dpi/device/${id}/history?range=${dpiHistoryRange}`),
      ]);
      setDpiSnapshot(snapshotRes.data);
      setDpiHistory(historyRes.data || []);
    } catch (err) {
      console.debug('DPI data not available:', err.message);
    } finally {
      setDpiLoading(false);
    }
  }, [id, dpiHistoryRange]);

  useEffect(() => {
    setLoading(true);
    fetchDeviceDetails();
    stopPolling();
    pollRef.current = setInterval(fetchDeviceDetails, 10000); // refresh every 10s while available
    return () => stopPolling();
  }, [fetchDeviceDetails]);

  useEffect(() => {
    fetchDpiData();
    const dpiInterval = setInterval(fetchDpiData, 5000); // refresh DPI every 5s
    return () => clearInterval(dpiInterval);
  }, [fetchDpiData]);

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading device details...</p>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="empty-state">
        <AlertTriangle size={48} style={{ color: '#ef4444', marginBottom: '16px' }} />
        <h2>{error || 'Device not found'}</h2>
        <button className="btn-secondary" onClick={() => navigate('/devices')} style={{ marginTop: '16px' }}>
          Back to Devices
        </button>
      </div>
    );
  }

  const { device, uptime, pingLatency, openPorts, vendor, alertsCount, bandwidthShare } = data;
  const isOnline = device.status === 'ONLINE' && device.isActive;

  return (
    <div className="page-container">
      <div className="page-header" style={{ alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <button className="btn-secondary" onClick={() => navigate('/devices')} style={{ padding: '8px', display: 'flex', alignItems: 'center' }}>
            <ArrowLeft size={20} />
          </button>
          <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <h1 className="btn-amber-text" data-text="Device Details" style={{ margin: 0, textTransform: 'none' }}>
              <span className="actual-text">Device Details</span>
              <span aria-hidden="true" className="hover-text">Device Details</span>
            </h1>
            <span style={{ fontSize: '24px', fontWeight: 'bold', color: 'white' }}>{device.deviceName}</span>
            <span className={`status-badge ${isOnline ? 'active' : 'inactive'}`} style={{ fontSize: '14px', padding: '4px 8px', alignSelf: 'center' }}>
              {isOnline ? '● Online' : '● Offline'}
            </span>
          </div>
            <p className="page-subtitle">{device.ipAddress} • {device.macAddress}</p>
          </div>
        </div>
      </div>

      <div className="metrics-grid">
        <div className="metric-card glass-panel" style={{ background: 'var(--panel-bg)', borderRadius: '12px', border: '1px solid var(--panel-border)' }}>
          <div className="metric-icon" style={{ background: 'rgba(59, 130, 246, 0.1)', color: '#3b82f6' }}>
            <Activity size={24} />
          </div>
          <div className="metric-content">
            <p className="metric-label">Latency</p>
            <h3 className="metric-value">{pingLatency >= 0 ? `${pingLatency} ms` : 'N/A'}</h3>
            <p className="metric-trend">Current network response</p>
          </div>
        </div>

        <div className="metric-card glass-panel" style={{ background: 'var(--panel-bg)', borderRadius: '12px', border: '1px solid var(--panel-border)' }}>
          <div className="metric-icon" style={{ background: 'rgba(16, 185, 129, 0.1)', color: '#10b981' }}>
            <Network size={24} />
          </div>
          <div className="metric-content">
            <p className="metric-label">Bandwidth Share</p>
            <h3 className="metric-value">{bandwidthShare ? `${bandwidthShare}%` : '0%'}</h3>
            <p className="metric-trend">Of total network traffic</p>
          </div>
        </div>

        <div className="metric-card glass-panel" style={{ background: 'var(--panel-bg)', borderRadius: '12px', border: '1px solid var(--panel-border)' }}>
          <div className="metric-icon" style={{ background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444' }}>
            <ShieldAlert size={24} />
          </div>
          <div className="metric-content">
            <p className="metric-label">Active Alerts</p>
            <h3 className="metric-value">{alertsCount || 0}</h3>
            <p className="metric-trend">Requires attention</p>
          </div>
        </div>

        <div className="metric-card glass-panel" style={{ background: 'var(--panel-bg)', borderRadius: '12px', border: '1px solid var(--panel-border)' }}>
          <div className="metric-icon" style={{ background: 'rgba(139, 92, 246, 0.1)', color: '#8b5cf6' }}>
            <Clock size={24} />
          </div>
          <div className="metric-content">
            <p className="metric-label">Uptime</p>
            <h3 className="metric-value">{uptime || 'N/A'}</h3>
            <p className="metric-trend">Since registration</p>
          </div>
        </div>
      </div>

      <div className="devices-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', marginTop: '32px' }}>
        <div className="device-card">
          <div className="device-header">
            <h3 className="device-name" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Server size={18} /> Hardware Details
            </h3>
          </div>
          <div className="device-details">
            <div className="detail-row">
              <span className="detail-label">Hardware Vendor</span>
              <span className="detail-value">{vendor}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Device Type</span>
              <span className="detail-value badge">{device.deviceType}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Registration Date</span>
              <span className="detail-value">{new Date(device.registeredAt).toLocaleString()}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Last Seen</span>
              <span className="detail-value">{new Date(device.lastSeenAt).toLocaleString()}</span>
            </div>
          </div>
        </div>

        <div className="device-card">
          <div className="device-header">
            <h3 className="device-name" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <HardDrive size={18} /> Network Diagnostics
            </h3>
          </div>
          <div className="device-details">
            <div className="detail-row">
              <span className="detail-label">Open Ports</span>
              <span className="detail-value">
                {openPorts && openPorts.length > 0 ? (
                  <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                    {openPorts.map(port => (
                      <span key={port} className="badge" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#10b981', padding: '2px 6px', fontSize: '11px' }}>
                        {port}
                      </span>
                    ))}
                  </div>
                ) : 'None detected'}
              </span>
            </div>
            <div className="detail-row">
              <span className="detail-label">MAC Address</span>
              <span className="detail-value font-mono">{device.macAddress}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">IP Address</span>
              <span className="detail-value font-mono">{device.ipAddress}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Status History</span>
              <span className="detail-value" style={{ opacity: 0.8, fontWeight: 'normal' }}>
                 Always On
              </span>
            </div>
          </div>
        </div>

        <div className="device-card">
          <div className="device-header">
            <h3 className="device-name" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Wifi size={18} /> Deep Packet Inspection (DPI)
            </h3>
          </div>
          <div className="device-details">
            {dpiSnapshot ? (
              <>
                <div className="detail-row">
                  <span className="detail-label">Current Traffic</span>
                  <span className="detail-value">
                    <TrafficBadge
                      service={dpiSnapshot.currentService}
                      category={dpiSnapshot.currentCategory}
                      confidence={dpiSnapshot.confidence}
                    />
                  </span>
                </div>
                <div className="detail-row">
                  <span className="detail-label">SNI Hostname</span>
                  <span className="detail-value font-mono" style={{ fontSize: '11px' }}>{dpiSnapshot.sniHostname || 'N/A'}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-label">Destination IP</span>
                  <span className="detail-value font-mono">{dpiSnapshot.destinationIp || 'N/A'}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-label">Port</span>
                  <span className="detail-value font-mono">{dpiSnapshot.destinationPort || 'N/A'}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-label">Last Updated</span>
                  <span className="detail-value">{dpiSnapshot.lastUpdated ? new Date(dpiSnapshot.lastUpdated).toLocaleString() : 'Never'}</span>
                </div>
              </>
            ) : (
              <p style={{ color: 'var(--text-secondary)', textAlign: 'center', margin: '20px 0' }}>
                No DPI data available yet. Enable packet capture to see live traffic classification.
              </p>
            )}
          </div>
        </div>
      </div>

      {/* DPI History */}
      {dpiSnapshot && dpiHistory && dpiHistory.length > 0 && (
        <div className="glass-panel" style={{ marginTop: '32px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h3 style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: 0 }}>
              <TrendingUp size={20} color="var(--accent-color)" />
              Traffic History
            </h3>
            <div style={{ display: 'flex', gap: '8px' }}>
              {['1h', '6h', '24h', '7d'].map(range => (
                <button
                  key={range}
                  onClick={() => setDpiHistoryRange(range)}
                  disabled={dpiLoading}
                  style={{
                    background: dpiHistoryRange === range ? 'var(--accent-color)' : 'rgba(51, 65, 85, 0.4)',
                    color: dpiHistoryRange === range ? 'white' : 'var(--text-secondary)',
                    border: 'none',
                    padding: '6px 12px',
                    borderRadius: '6px',
                    fontSize: '11px',
                    fontWeight: 600,
                    cursor: dpiLoading ? 'not-allowed' : 'pointer',
                    opacity: dpiLoading ? 0.6 : 1,
                  }}
                >
                  {range}
                </button>
              ))}
            </div>
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--panel-border)', color: 'var(--text-secondary)' }}>
                  <th style={{ padding: '8px', textAlign: 'left' }}>Timestamp</th>
                  <th style={{ padding: '8px', textAlign: 'left' }}>Service</th>
                  <th style={{ padding: '8px', textAlign: 'left' }}>Category</th>
                  <th style={{ padding: '8px', textAlign: 'left' }}>SNI Hostname</th>
                  <th style={{ padding: '8px', textAlign: 'left' }}>Destination IP</th>
                  <th style={{ padding: '8px', textAlign: 'center' }}>Port</th>
                  <th style={{ padding: '8px', textAlign: 'center' }}>Confidence</th>
                </tr>
              </thead>
              <tbody>
                {dpiHistory.map((entry, idx) => (
                  <tr key={idx} style={{ borderBottom: '1px solid rgba(51, 65, 85, 0.2)' }}>
                    <td style={{ padding: '8px', fontSize: '10px', color: 'var(--text-secondary)' }}>
                      {new Date(entry.classifiedAt).toLocaleString()}
                    </td>
                    <td style={{ padding: '8px' }}>
                      <TrafficBadge service={entry.serviceName} category={entry.trafficCategory} confidence={entry.confidence} />
                    </td>
                    <td style={{ padding: '8px', color: 'var(--text-secondary)' }}>{entry.trafficCategory}</td>
                    <td style={{ padding: '8px', fontFamily: 'monospace', fontSize: '10px', color: 'var(--text-secondary)' }}>
                      {entry.sniHostname || '—'}
                    </td>
                    <td style={{ padding: '8px', fontFamily: 'monospace', fontSize: '10px', color: 'var(--text-secondary)' }}>
                      {entry.destinationIp || '—'}
                    </td>
                    <td style={{ padding: '8px', textAlign: 'center', fontFamily: 'monospace', color: 'var(--text-secondary)' }}>
                      {entry.port || '—'}
                    </td>
                    <td style={{ padding: '8px', textAlign: 'center', color: 'var(--text-secondary)' }}>
                      {entry.confidence}%
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

export default DeviceDetailPage;
