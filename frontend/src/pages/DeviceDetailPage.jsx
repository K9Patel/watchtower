import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { ArrowLeft, Activity, ShieldAlert, Clock, Network, Server, HardDrive, AlertTriangle } from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import './Pages.css';

const DeviceDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
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

  useEffect(() => {
    setLoading(true);
    fetchDeviceDetails();
    stopPolling();
    pollRef.current = setInterval(fetchDeviceDetails, 10000); // refresh every 10s while available
    return () => stopPolling();
  }, [fetchDeviceDetails]);

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
            <h1 style={{ display: 'flex', alignItems: 'center', gap: '12px', margin: 0 }}>
              {device.deviceName}
              <span className={`status-badge ${isOnline ? 'active' : 'inactive'}`} style={{ fontSize: '14px', padding: '4px 8px', alignSelf: 'center' }}>
                {isOnline ? '● Online' : '● Offline'}
              </span>
            </h1>
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
      </div>
    </div>
  );
};

export default DeviceDetailPage;
