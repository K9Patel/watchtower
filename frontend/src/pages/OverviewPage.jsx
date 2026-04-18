import React, { useState, useEffect } from 'react';
import axios from 'axios';
import SummaryCards from '../components/SummaryCards';
import DevicesTable from '../components/DevicesTable';
import AlertsPanel from '../components/AlertsPanel';
import PredictionBanner from '../components/PredictionBanner';
import RmiDemoPanel from '../components/RmiDemoPanel';
import { useSettings } from '../context/SettingsContext';
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

  useEffect(() => {
    fetchDashboardData();
    const intervalId = setInterval(fetchDashboardData, settings.refreshInterval * 1000);
    return () => clearInterval(intervalId);
  }, [settings.refreshInterval]);

  const fetchDashboardData = async () => {
    try {
      const [summaryRes, devicesRes, alertsRes, trendRes, clustersRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/stats/summary`),
        axios.get(`${API_BASE_URL}/devices`),
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
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Overview</h1>
          <p className="page-subtitle">Real-time network monitoring dashboard</p>
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
