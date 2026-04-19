import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Filler, Legend, BarElement } from 'chart.js';
import { Line, Bar } from 'react-chartjs-2';
import { API_BASE_URL } from '../config/api';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, BarElement, Title, Tooltip, Filler, Legend);

const HistoryCharts = () => {
  const [daily, setDaily] = useState([]);
  const [traffic, setTraffic] = useState({});
  const [healthHourly, setHealthHourly] = useState([]);

  useEffect(() => {
    const fetch = async () => {
      try {
        const [dailyRes, trafficRes, healthRes] = await Promise.all([
          axios.get(`${API_BASE_URL}/history/daily`),
          axios.get(`${API_BASE_URL}/stats/traffic`),
          axios.get(`${API_BASE_URL}/history/health-hourly?minutes=60`)
        ]);
        setDaily(dailyRes.data);
        setTraffic(trafficRes.data);
        setHealthHourly(healthRes.data || []);
      } catch (e) {
        console.error(e);
      }
    };
    fetch();
    const id = setInterval(fetch, 10000);
    return () => clearInterval(id);
  }, []);

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    color: '#94a3b8',
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { color: 'rgba(51, 65, 85, 0.3)' }, ticks: { color: '#94a3b8' } },
      y: { grid: { color: 'rgba(51, 65, 85, 0.3)' }, ticks: { color: '#94a3b8' }, beginAtZero: true }
    }
  };

  const lineData = {
    labels: daily.map(d => d.date),
    datasets: [{
      fill: true,
      label: 'Total MB',
      data: daily.map(d => d.totalTraffic),
      borderColor: '#6366f1',
      backgroundColor: 'rgba(99, 102, 241, 0.1)',
      tension: 0.4
    }]
  };

  const trafficData = {
    labels: Object.keys(traffic),
    datasets: [{
      data: Object.values(traffic),
      backgroundColor: [
        '#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'
      ],
      borderRadius: 4
    }]
  };

  const latestHealthScore = healthHourly.length > 0
    ? Number(healthHourly[healthHourly.length - 1]?.healthScore || 0)
    : Number(daily[daily.length - 1]?.hourlyHealthScore || 0);

  const normalizedHealthScore = Number.isFinite(latestHealthScore)
    ? Math.max(0, Math.min(100, latestHealthScore))
    : 0;

  const healthColor = normalizedHealthScore >= 85
    ? '#22c55e'
    : normalizedHealthScore >= 50
      ? '#f59e0b'
      : '#ef4444';

  const healthSeries = healthHourly.length > 0
    ? healthHourly
    : daily
      .filter((d) => d?.hourlyHealthScore !== undefined)
      .map((d) => ({
        label: d.date,
        healthScore: Number(d.hourlyHealthScore || 0),
        trend: 'stable'
      }));

  const healthData = {
    labels: healthSeries.map((point) => point.label),
    datasets: [{
      fill: true,
      label: 'Health Score',
      data: healthSeries.map((point) => Number(point.healthScore || 0)),
      borderColor: healthColor,
      backgroundColor: `${healthColor}22`,
      tension: 0.35,
      pointRadius: 2.5,
      pointHoverRadius: 4,
    }]
  };

  const healthOptions = {
    ...chartOptions,
    scales: {
      ...chartOptions.scales,
      y: {
        ...chartOptions.scales.y,
        min: 0,
        max: 100,
      }
    }
  };

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px' }}>
      <div className="glass-panel" style={{ height: '300px', display: 'flex', flexDirection: 'column' }}>
        <h3 style={{ fontSize: '15px', marginBottom: '16px' }}>7-Day Bandwidth Transfer (MB)</h3>
        <div style={{ flex: 1, position: 'relative' }}>
          <Line options={chartOptions} data={lineData} />
        </div>
      </div>
      
      <div className="glass-panel" style={{ height: '300px', display: 'flex', flexDirection: 'column' }}>
        <h3 style={{ fontSize: '15px', marginBottom: '16px' }}>Network Traffic Composition</h3>
        <div style={{ flex: 1, position: 'relative' }}>
          <Bar options={{ ...chartOptions, plugins: { legend: { display: false } }, indexAxis: 'y' }} data={trafficData} />
        </div>
      </div>

      <div className="glass-panel" style={{ height: '280px', display: 'flex', flexDirection: 'column', gridColumn: '1 / -1' }}>
        <h3 style={{ fontSize: '15px', marginBottom: '16px' }}>Network Health Score (1 Hour)</h3>
        <div style={{ flex: 1, position: 'relative' }}>
          <Line options={healthOptions} data={healthData} />
        </div>
      </div>
    </div>
  );
};

export default HistoryCharts;
