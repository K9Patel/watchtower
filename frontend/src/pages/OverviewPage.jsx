import React, { useState, useEffect } from 'react';
import axios from 'axios';
import SummaryCards from '../components/SummaryCards';
import DevicesTable from '../components/DevicesTable';
import AlertsPanel from '../components/AlertsPanel';
import PredictionBanner from '../components/PredictionBanner';
import IncidentTimelineReplay from '../components/IncidentTimelineReplay';
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
  const [timeline, setTimeline] = useState(null);
  const [replaySnapshot, setReplaySnapshot] = useState(null);
  const [loading, setLoading] = useState(true);
  const [toastAlert, setToastAlert] = useState(null);

  const { dashboardData, latestAlert, deviceUpdates, connected } = useWebSocket();

  const fetchTrendData = async () => {
    try {
      const { data } = await axios.get(`${API_BASE_URL}/trend/analysis`);
      setTrend(data);
    } catch (error) {
      console.error('Error fetching trend data:', error);
    }
  };

  const fetchClusters = async () => {
    try {
      const { data } = await axios.get(`${API_BASE_URL}/trend/clusters`);
      const clusterMap = {};
      data.forEach((c) => {
        clusterMap[c.deviceId] = c.cluster;
      });
      setClusters(clusterMap);
    } catch (error) {
      console.error('Error fetching cluster data:', error);
    }
  };

  const fetchTimelineData = async () => {
    try {
      const { data } = await axios.get(`${API_BASE_URL}/stats/timeline?minutes=60`);
      setTimeline(data);
    } catch (error) {
      console.error('Error fetching timeline data:', error);
    }
  };

  // Initial data fetch only (no interval)
  useEffect(() => {
    fetchDashboardData();
  }, []); // Remove interval dependency

  // Handle incoming WebSocket dashboard updates
  useEffect(() => {
    if (dashboardData) {
      setSummary(dashboardData);
      // Keep trend fresh even when initial batch fetch had a transient failure.
      fetchTrendData();
      fetchTimelineData();
    }
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

      fetchClusters();
    }
  }, [deviceUpdates]);

  const fetchDashboardData = async () => {
    try {
      const [summaryRes, devicesRes, alertsRes, trendRes, clustersRes] = await Promise.allSettled([
        axios.get(`${API_BASE_URL}/stats/summary`),
        axios.get(`${API_BASE_URL}/devices/active`), // Fetch active instead of all
        axios.get(`${API_BASE_URL}/alerts`),
        axios.get(`${API_BASE_URL}/trend/analysis`),
        axios.get(`${API_BASE_URL}/trend/clusters`),
      ]);

      if (summaryRes.status === 'fulfilled') {
        setSummary(summaryRes.value.data);
      }
      if (devicesRes.status === 'fulfilled') {
        setDevices(devicesRes.value.data);
      }
      if (alertsRes.status === 'fulfilled') {
        setAlerts(alertsRes.value.data);
      }
      if (trendRes.status === 'fulfilled') {
        setTrend(trendRes.value.data);
      }

      if (clustersRes.status === 'fulfilled') {
        const clusterMap = {};
        clustersRes.value.data.forEach((c) => (clusterMap[c.deviceId] = c.cluster));
        setClusters(clusterMap);
      }

      fetchTimelineData();
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
    } finally {
      setLoading(false);
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
          position: 'fixed', top: '20px', right: '20px',
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
      <SummaryCards summary={summary} devices={devices} replay={replaySnapshot} />

      <IncidentTimelineReplay
        timeline={timeline}
        onReplaySnapshot={setReplaySnapshot}
      />

      <div className="overview-grid">
        <div className="main-charts">
          <DevicesTable devices={devices} clusters={clusters} onRefresh={fetchDashboardData} />
        </div>

        <div className="alerts-column">
          <AlertsPanel alerts={alerts} onRefresh={fetchDashboardData} />
        </div>
      </div>
    </div>
  );
};

export default OverviewPage;
