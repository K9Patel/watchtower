import React, { useState, useEffect } from 'react';
import axios from 'axios';
import SummaryCards from '../components/SummaryCards';
import DevicesTable from '../components/DevicesTable';
import AlertsPanel from '../components/AlertsPanel';
import PredictionBanner from '../components/PredictionBanner';
import RmiDemoPanel from '../components/RmiDemoPanel';
import { useSettings } from '../context/SettingsContext';
import { useWebSocket } from '../hooks/useWebSocket';
import { API_BASE_URL } from '../config/api';
import './Pages.css';

const OverviewPage = () => {
  const { settings } = useSettings();
  const [summary, setSummary] = useState(null);
  const [devices, setDevices] = useState([]);
  const [clusters, setClusters] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [trend, setTrend] = useState(null);
  const [loading, setLoading] = useState(true);
  const [toastAlert, setToastAlert] = useState(null);

  const { dashboardData, latestAlert, deviceUpdates, connected } = useWebSocket();

  // Initial data fetch only (no interval)
  useEffect(() => {
    fetchDashboardData();
  }, []); // Remove interval dependency

  // Handle incoming WebSocket dashboard updates
  useEffect(() => {
    if (dashboardData) setSummary(dashboardData);
  }, [dashboardData]);

  // Handle incoming WebSocket alert updates (append to list & show toast)
  useEffect(() => {
    if (latestAlert) {
      setAlerts(prev => [latestAlert, ...prev]);
      setToastAlert(latestAlert);
      setTimeout(() => setToastAlert(null), 5000); // Hide toast after 5s
    }
  }, [latestAlert]);

  // Handle incoming WebSocket device updates
  useEffect(() => {
    if (deviceUpdates) {
      setDevices(prevDevices => {
        const idx = prevDevices.findIndex(d => d.id === deviceUpdates.id);
        if (idx !== -1) {
          const newDevices = [...prevDevices];
          newDevices[idx] = deviceUpdates;
          return newDevices;
        } else {
          return [deviceUpdates, ...prevDevices];
        }
      });
    }
  }, [deviceUpdates]);

  const fetchDashboardData = async () => {
    try {
      const [summaryRes, devicesRes, alertsRes, trendRes, clustersRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/stats/summary`),
        axios.get(`${API_BASE_URL}/devices/active`), // Fetch active instead of all
        axios.get(`${API_BASE_URL}/alerts`),
        axios.get(`${API_BASE_URL}/trend/predict`),
        axios.get(`${API_BASE_URL}/trend/clusters`),
      ]);

      setSummary(summaryRes.data);
      setDevices(devicesRes.data);
      setAlerts(alertsRes.data);
      setTrend(trendRes.data);

      const clusterMap = {};
      clustersRes.data.forEach((c) => (clusterMap[c.deviceId] = c.cluster));
      setClusters(clusterMap);

      setLoading(false);
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
    }
  };

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <h2>Initializing WatchTower Core Engine...</h2>
      </div>
    );
  }

  return (
    <div className="page-container relative">
      {/* Toast Alert */}
      {toastAlert && (
        <div style={{
          position: 'fixed', top: '20px', left: '50%', transform: 'translateX(-50%)',
          background: 'linear-gradient(135deg, #ef4444, #b91c1c)', color: 'white',
          padding: '12px 24px', borderRadius: '8px', zIndex: 1000,
          boxShadow: '0 10px 25px rgba(239, 68, 68, 0.4)', fontWeight: 600,
          display: 'flex', alignItems: 'center', gap: '10px',
          animation: 'slideDown 0.3s ease-out'
        }}>
          ⚠️ New Alert: {toastAlert.message}
        </div>
      )}

      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h1>Overview</h1>
          <p className="page-subtitle">Real-time network monitoring dashboard</p>
        </div>

        {/* WebSocket Connection Badge */}
        <div style={{
          padding: '6px 14px', borderRadius: '20px', fontWeight: 800, fontSize: '11px',
          letterSpacing: '0.1em', display: 'flex', alignItems: 'center', gap: '8px',
          background: connected ? 'rgba(34,197,94,0.1)' : 'rgba(239,68,68,0.1)',
          color: connected ? '#22c55e' : '#ef4444',
          border: `1px solid ${connected ? 'rgba(34,197,94,0.3)' : 'rgba(239,68,68,0.3)'}`
        }}>
          <div style={{
            width: '8px', height: '8px', borderRadius: '50%',
            background: connected ? '#22c55e' : '#ef4444',
            animation: connected ? 'newPulse 2s infinite' : 'none'
          }} />
          {connected ? 'LIVE' : 'RECONNECTING...'}
        </div>
      </div>

      <PredictionBanner trend={trend} />
      <SummaryCards summary={summary} />

      <div className="overview-grid">
        <div className="main-charts">
          <DevicesTable devices={devices} clusters={clusters} onRefresh={fetchDashboardData} />
        </div>

        <div className="alerts-column">
          <AlertsPanel alerts={alerts} onRefresh={fetchDashboardData} />
        </div>
      </div>

      <div className="full-width-section">
        <RmiDemoPanel />
      </div>
    </div>
  );
};

export default OverviewPage;
