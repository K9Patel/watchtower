import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { Download, Calendar, FileText, CheckCircle } from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import './Pages.css';

const ReportsPage = () => {
  const [reportData, setReportData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchReportPreview();
  }, []);

  const fetchReportPreview = async () => {
    try {
      const res = await axios.get(`${API_BASE_URL}/report/preview`);
      setReportData(res.data);
      setError(null);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching report preview:', error);
      setError('Failed to load reports. Please try again.');
      setLoading(false);
    }
  };

  const downloadPDF = async () => {
    setGenerating(true);
    try {
      const res = await axios.get(`${API_BASE_URL}/report/weekly`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `watchtower-report-${new Date().toISOString().split('T')[0]}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
    } catch (error) {
      console.error('Error downloading report:', error);
      setError('PDF generation endpoint not available. The backend service may not have this feature implemented yet.');
    }
    setGenerating(false);
  };

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading reports...</p>
      </div>
    );
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Reports</h1>
          <p className="page-subtitle">Generate and view network reports</p>
        </div>
      </div>

      {error && (
        <div className="glass-panel" style={{ background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.3)', padding: '16px', marginBottom: '24px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <p style={{ color: '#ef4444', margin: 0 }}>⚠️ {error}</p>
          <button onClick={fetchReportPreview} className="btn-secondary" style={{ fontSize: '12px' }}>Retry</button>
        </div>
      )}

      {/* Report Generation */}
      <div className="glass-panel report-generator">
        <div className="generator-content">
          <div className="generator-icon">
            <FileText size={40} />
          </div>
          <div className="generator-info">
            <h2>Weekly Network Report</h2>
            <p>Comprehensive analysis of network activity, trends, and device performance</p>
          </div>
        </div>

        <button
          onClick={downloadPDF}
          disabled={generating}
          className="btn-primary"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '12px 24px',
          }}
        >
          <Download size={18} />
          {generating ? 'Generating...' : 'Download PDF'}
        </button>
      </div>

      {/* Report Summary */}
      {!reportData && !error && (
        <div className="glass-panel" style={{ textAlign: 'center', padding: '40px', opacity: 0.6 }}>
          <p>Loading report data...</p>
        </div>
      )}

      {reportData && (
        <>
          <div className="glass-panel summary-section">
            <h2 className="section-title">Report Summary</h2>
            <div className="summary-grid">
              <div className="summary-item">
                <span className="summary-label">Report Title</span>
                <span className="summary-value">{reportData.reportTitle}</span>
              </div>
              <div className="summary-item">
                <span className="summary-label">Generated At</span>
                <span className="summary-value">{new Date(reportData.generatedAt).toLocaleString()}</span>
              </div>
              <div className="summary-item">
                <span className="summary-label">Period</span>
                <span className="summary-value">Last 7 Days</span>
              </div>
            </div>
          </div>

          {/* Daily Statistics */}
          {Array.isArray(reportData.dailyTotals) && (
            <div className="glass-panel daily-stats">
              <h2 className="section-title">Daily Usage Statistics</h2>
              <div className="stats-table">
                <div className="table-header">
                  <span>Date</span>
                  <span>Total Traffic (GB)</span>
                  <span>Peak Load (%)</span>
                </div>
                {reportData.dailyTotals.map((day, idx) => (
                  <div key={idx} className="table-row">
                    <span>{day.date || `Day ${idx + 1}`}</span>
                    <span>{((day.totalTraffic || 0) / 1024).toFixed(2)}</span>
                    <span>{day.peakLoad?.toFixed(1) || '0.0'}%</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Peak Hours */}
          {Array.isArray(reportData.peakHours) && (
            <div className="glass-panel peak-hours">
              <h2 className="section-title">Peak Activity Hours</h2>
              <div className="peak-list">
                {reportData.peakHours.map((hour, idx) => {
                  const maxTraffic = Math.max(...reportData.peakHours.filter(h => typeof h.traffic === 'number').map((h) => h.traffic || 0), 1);
                  return (
                    <div key={idx} className="peak-item">
                      <span className="peak-time">{hour.hour || `${idx * 4}:00`}</span>
                      <div className="peak-bar">
                        <div
                          className="peak-fill"
                          style={{
                            width: `${((hour.traffic || 0) / maxTraffic) * 100}%`,
                          }}
                        ></div>
                      </div>
                      <span className="peak-value">{hour.traffic?.toFixed(0) || 'N/A'} MB</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Per Device Summary */}
          {Array.isArray(reportData.perDevice) && (
            <div className="glass-panel per-device">
              <h2 className="section-title">Per-Device Summary</h2>
              <div className="device-list">
                {reportData.perDevice.slice(0, 10).map((device, idx) => (
                  <div key={idx} className="device-summary-item">
                    <div className="device-info">
                      <span className="device-name">{device.deviceName || `Device ${idx + 1}`}</span>
                      <span className="device-type">{device.deviceType}</span>
                    </div>
                    <div className="device-stats">
                      <span className="stat">{((device.totalTraffic || 0) / 1024).toFixed(2)} GB</span>
                      <span className="stat">{device.avgLoad?.toFixed(1) || 0}% avg</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Trend Analysis */}
          {reportData.trend && (
            <div className="glass-panel trend-analysis">
              <h2 className="section-title">Trend Analysis</h2>
              <div className="trend-info">
                <div className="trend-item">
                  <span className="trend-label">Overall Trend</span>
                  <span className="trend-value">{reportData.trend.direction || 'Stable'}</span>
                </div>
                <div className="trend-item">
                  <span className="trend-label">Week-over-Week Change</span>
                  <span className="trend-value">{reportData.trend.weeklyChange?.toFixed(1) || '0'}%</span>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* Additional Reports */}
      <div className="additional-reports">
        <h2 className="section-title">Other Reports</h2>
        <div className="reports-grid">
          <div className="glass-panel report-card">
            <div className="report-header">
              <FileText size={24} />
              <span className="badge">Coming Soon</span>
            </div>
            <h3>Daily Report</h3>
            <p>24-hour network activity snapshot</p>
          </div>

          <div className="glass-panel report-card">
            <div className="report-header">
              <FileText size={24} />
              <span className="badge">Coming Soon</span>
            </div>
            <h3>Monthly Summary</h3>
            <p>Extended analysis of monthly trends</p>
          </div>

          <div className="glass-panel report-card">
            <div className="report-header">
              <FileText size={24} />
              <span className="badge">Coming Soon</span>
            </div>
            <h3>Device Report</h3>
            <p>Individual device performance analysis</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ReportsPage;
