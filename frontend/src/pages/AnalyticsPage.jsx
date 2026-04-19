import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { TrendingUp, TrendingDown, Activity, Zap, AlertTriangle } from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import HistoryCharts from '../components/HistoryCharts';
import './Pages.css';

const AnalyticsPage = () => {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [previousNetworks, setPreviousNetworks] = useState([]);
  const [selectedNetwork, setSelectedNetwork] = useState('');
  const [previousSnapshot, setPreviousSnapshot] = useState(null);
  const [previousLoading, setPreviousLoading] = useState(false);
  const [previousError, setPreviousError] = useState('');

  const authHeaders = () => {
    const token = localStorage.getItem('auth_token');
    return token ? { Authorization: `Bearer ${token}` } : {};
  };

  useEffect(() => {
    fetchAnalytics();
    fetchPreviousNetworks();
    const intervalId = setInterval(fetchAnalytics, 10000); // Refresh every 10 seconds for live data
    return () => clearInterval(intervalId);
  }, []);

  const fetchPreviousNetworks = async () => {
    try {
      const res = await axios.get(`${API_BASE_URL}/history/network-states/previous`, {
        headers: authHeaders(),
      });
      const networks = Array.isArray(res.data) ? res.data : [];
      setPreviousNetworks(networks);
      if (networks.length > 0) {
        setSelectedNetwork(networks[0].networkPrefix);
      }
      setPreviousError('');
    } catch (err) {
      console.error('Failed to load previous network list:', err);
      setPreviousError('Could not load previous network connections.');
    }
  };

  const loadPreviousNetworkState = async () => {
    if (!selectedNetwork) return;
    try {
      setPreviousLoading(true);
      const res = await axios.get(`${API_BASE_URL}/history/network-state`, {
        params: { networkPrefix: selectedNetwork },
        headers: authHeaders(),
      });
      setPreviousSnapshot(res.data || null);
      setPreviousError('');
    } catch (err) {
      console.error('Failed to load previous network snapshot:', err);
      setPreviousError('Could not load selected previous network state.');
      setPreviousSnapshot(null);
    } finally {
      setPreviousLoading(false);
    }
  };

  const fetchAnalytics = async () => {
    try {
      const [summaryRes, dailyRes, peakHoursRes, perDeviceRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/stats/summary`),
        axios.get(`${API_BASE_URL}/history/daily`),
        axios.get(`${API_BASE_URL}/history/peak-hours`),
        axios.get(`${API_BASE_URL}/history/per-device`),
      ]);

      console.log('Analytics summary:', summaryRes.data);
      console.log('Daily data:', dailyRes.data);
      console.log('Peak hours:', peakHoursRes.data);
      console.log('Per-device:', perDeviceRes.data);
      
      setAnalytics({
        summary: summaryRes.data,
        daily: dailyRes.data,
        peakHours: peakHoursRes.data,
        perDevice: perDeviceRes.data,
      });
      setError(null);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching analytics:', error);
      setError('Failed to load analytics data. Please check the backend is running.');
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading analytics...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-container">
        <div className="page-header">
          <div>
            <h1 className="btn-amber-text" data-text="Analytics" style={{ margin: 0, textTransform: 'none' }}>
              <span className="actual-text">Analytics</span>
              <span aria-hidden="true" className="hover-text">Analytics</span>
            </h1>
            <p className="page-subtitle">Detailed network statistics and trends</p>
          </div>
        </div>
        <div className="glass-panel" style={{
          background: 'rgba(239, 68, 68, 0.1)',
          border: '1px solid rgba(239, 68, 68, 0.3)',
          padding: '20px',
          borderRadius: '8px',
          textAlign: 'center'
        }}>
          <p style={{ color: '#ef4444', marginBottom: '10px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
            <AlertTriangle size={18} /> {error}
          </p>
          <button onClick={fetchAnalytics} className="btn-secondary">Retry</button>
        </div>
      </div>
    );
  }

  const summary = analytics?.summary || {};
  const onlineDevices = summary.onlineDevices ?? 0;
  const totalDevices = summary.totalDevices ?? 0;
  const connectivityPercent = totalDevices > 0 ? ((onlineDevices / totalDevices) * 100).toFixed(1) : 0;

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 className="btn-amber-text" data-text="Analytics" style={{ margin: 0, textTransform: 'none' }}>
            <span className="actual-text">Analytics</span>
            <span aria-hidden="true" className="hover-text">Analytics</span>
          </h1>
          <p className="page-subtitle">Detailed network statistics and trends</p>
        </div>
      </div>

      <div className="glass-panel" style={{ marginBottom: '20px' }}>
        <h2 className="section-title">Previous Network States</h2>
        <p style={{ color: 'var(--text-secondary)', marginTop: 0 }}>
          View only the historical state captured before each network change.
        </p>

        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
          <select
            className="filter-select"
            value={selectedNetwork}
            onChange={(e) => setSelectedNetwork(e.target.value)}
            style={{ minWidth: '260px', maxWidth: '420px' }}
          >
            {previousNetworks.length === 0 && <option value="">No previous networks found</option>}
            {previousNetworks.map((network) => (
              <option key={network.networkPrefix} value={network.networkPrefix}>
                {network.networkPrefix} | last seen {new Date(network.lastSeenAt).toLocaleString()}
              </option>
            ))}
          </select>

          <button
            className="btn-secondary"
            onClick={loadPreviousNetworkState}
            disabled={!selectedNetwork || previousLoading}
          >
            {previousLoading ? 'Loading state...' : 'Load Previous State'}
          </button>
        </div>

        {previousError && (
          <p style={{ marginTop: '12px', color: '#ef4444' }}>{previousError}</p>
        )}

        {previousSnapshot && (
          <div style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', gap: '18px', flexWrap: 'wrap', marginBottom: '12px' }}>
              <span><strong>Network:</strong> {previousSnapshot.networkPrefix}</span>
              <span><strong>From:</strong> {new Date(previousSnapshot.startedAt).toLocaleString()}</span>
              <span><strong>Until:</strong> {new Date(previousSnapshot.lastSeenAt).toLocaleString()}</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px' }}>
              <div className="glass-panel" style={{ padding: '12px' }}>
                <h3 style={{ marginTop: 0, marginBottom: '8px' }}>Daily Points</h3>
                <p style={{ margin: 0 }}>{Array.isArray(previousSnapshot.daily) ? previousSnapshot.daily.length : 0}</p>
              </div>
              <div className="glass-panel" style={{ padding: '12px' }}>
                <h3 style={{ marginTop: 0, marginBottom: '8px' }}>Peak Hour Entries</h3>
                <p style={{ margin: 0 }}>{previousSnapshot.peakHours ? Object.keys(previousSnapshot.peakHours).length : 0}</p>
              </div>
              <div className="glass-panel" style={{ padding: '12px' }}>
                <h3 style={{ marginTop: 0, marginBottom: '8px' }}>Devices Seen</h3>
                <p style={{ margin: 0 }}>{Array.isArray(previousSnapshot.perDevice) ? previousSnapshot.perDevice.length : 0}</p>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Charts */}
      <div className="glass-panel chart-panel" style={{ marginTop: '20px' }}>
        <h2 className="section-title">Usage Trends</h2>
        <HistoryCharts />
      </div>

      {/* Traffic Breakdown */}
      {summary.trafficBreakdown && Object.keys(summary.trafficBreakdown).length > 0 ? (
        <div className="glass-panel traffic-panel">
          <h2 className="section-title">Traffic Breakdown</h2>
          <div className="traffic-grid">
            {Object.entries(summary.trafficBreakdown).map(([trafficType, count]) => {
              const totalCount = Object.values(summary.trafficBreakdown).reduce((a, b) => a + b, 0);
              const percentage = totalCount > 0 ? (count / totalCount) * 100 : 0;
              return (
                <div key={trafficType} className="traffic-item">
                  <div className="traffic-label">{trafficType}</div>
                  <div className="traffic-bar">
                    <div
                      className="traffic-fill"
                      style={{
                        width: `${percentage}%`,
                      }}
                    ></div>
                  </div>
                  <div className="traffic-value">{count} events</div>
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        <div className="glass-panel traffic-panel">
          <h2 className="section-title">Traffic Breakdown</h2>
          <p style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>No traffic data available</p>
        </div>
      )}

      {/* Performance Indicators */}
      <div className="performance-grid">
        <div className="glass-panel perf-card">
          <h3 className="perf-title">System Health</h3>
          <div className="health-indicator">
            <div className="health-bar" style={{ background: 'rgba(0, 0, 0, 0.2)' }}>
              <div style={{ 
                width: `${summary.systemHealth || 0}%`, 
                height: '100%', 
                background: (summary.systemHealth || 0) > 80 ? 'linear-gradient(90deg, #22c55e, #16a34a)' : 'linear-gradient(90deg, #f97316, #ea580c)', 
                borderRadius: '4px',
                transition: 'width 1s ease' 
              }}></div>
              <span>{summary.systemHealth || 0}%</span>
            </div>
            <p className="health-text">{(summary.systemHealth || 0) > 80 ? 'Excellent system performance' : 'Degraded due to load or alerts'}</p>
          </div>
        </div>

        <div className="glass-panel perf-card">
          <h3 className="perf-title">Core Uptime</h3>
          <div className="uptime-stat">
            <span className="uptime-value" style={{ fontSize: '20px' }}>{summary.uptime || 'Offline'}</span>
            <p className="uptime-text">Live JVM runtime</p>
          </div>
        </div>

        <div className="glass-panel perf-card">
          <h3 className="perf-title">Avg Response Time</h3>
          <div className="response-stat">
            <span className="response-value">{summary.avgResponseTime >= 0 ? `${summary.avgResponseTime}ms` : 'Offline'}</span>
            <p className="response-text">Physical TCP ping latency</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AnalyticsPage;
