import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { ChevronLeft, ChevronRight, Search, Filter, Check, Trash2 } from 'lucide-react';
import { useSettings } from '../context/SettingsContext';
import { API_BASE_URL } from '../config/api';
import AnimatedSearch from '../components/AnimatedSearch';
import './Pages.css';

const AlertsPage = () => {
  const { settings } = useSettings();
  const [alerts, setAlerts] = useState([]);
  const [filteredAlerts, setFilteredAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterSeverity, setFilterSeverity] = useState('ALL');
  const [filterResolved, setFilterResolved] = useState('UNRESOLVED');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;

  useEffect(() => {
    fetchAlerts();
    const intervalId = setInterval(fetchAlerts, settings.refreshInterval * 1000);
    return () => clearInterval(intervalId);
  }, [settings.refreshInterval]);

  useEffect(() => {
    applyFilters();
  }, [searchTerm, filterSeverity, filterResolved, alerts]);

  const fetchAlerts = async () => {
    try {
      const res = await axios.get(`${API_BASE_URL}/alerts/all?page=0&size=100`);
      setAlerts(res.data.content || res.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching alerts:', error);
      setLoading(false);
    }
  };

  const applyFilters = () => {
    let filtered = alerts;

    if (filterResolved === 'UNRESOLVED') {
      filtered = filtered.filter((a) => !a.isResolved);
    } else if (filterResolved === 'RESOLVED') {
      filtered = filtered.filter((a) => a.isResolved);
    }

    if (filterSeverity !== 'ALL') {
      filtered = filtered.filter((a) => a.severity === filterSeverity);
    }

    if (searchTerm) {
      filtered = filtered.filter(
        (a) =>
          a.device?.deviceName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          a.message?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    setFilteredAlerts(filtered);
    setCurrentPage(1);
  };

  const paginatedAlerts = filteredAlerts.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const totalPages = Math.ceil(filteredAlerts.length / itemsPerPage);

  const getSeverityColor = (severity) => {
    switch (severity) {
      case 'CRITICAL':
        return '#ef4444';
      case 'HIGH':
        return '#f97316';
      case 'MEDIUM':
        return '#eab308';
      case 'LOW':
        return '#3b82f6';
      default:
        return '#6b7280';
    }
  };

  const markAsResolved = async (id) => {
    try {
      await axios.put(`${API_BASE_URL}/alerts/${id}/resolve`);
      fetchAlerts();
    } catch (error) {
      console.error('Error resolving alert:', error);
    }
  };

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading alerts...</p>
      </div>
    );
  }

  const unresolvedCount = alerts.filter((a) => !a.isResolved).length;

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 className="btn-amber-text" data-text="Alerts" style={{ margin: 0, textTransform: 'none' }}>
            <span className="actual-text">Alerts</span>
            <span aria-hidden="true" className="hover-text">Alerts</span>
          </h1>
          <p className="page-subtitle">Network notifications and events</p>
        </div>
        <div className="header-stats">
          {unresolvedCount > 0 && (
            <button
              onClick={async () => {
                await axios.put(`${API_BASE_URL}/alerts/resolve-all`);
                fetchAlerts();
              }}
              className="btn-sleek"
              style={{
                borderColor: 'rgba(216, 178, 119, 0.38)',
                color: '#d8b277',
                background: 'rgba(171, 139, 84, 0.12)'
              }}
            >
              Resolve All
            </button>
          )}
          <div className="stat alerts-stat-card">
            <span className="stat-value" style={{ color: '#d8b277' }}>
              {unresolvedCount}
            </span>
            <span className="stat-label">Unresolved</span>
          </div>
          <div className="stat alerts-stat-card">
            <span className="stat-value" style={{ color: '#b6bcc6' }}>
              {alerts.filter((a) => a.isResolved).length}
            </span>
            <span className="stat-label">Resolved</span>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="glass-panel filters-panel alerts-filters-panel">
        <div className="filter-group alerts-filter-search">
          <AnimatedSearch 
            value={searchTerm} 
            onChange={e => setSearchTerm(e.target.value)} 
            placeholder="Search alerts..." 
          />
        </div>

        <div className="filter-group alerts-filter-select">
          <Filter size={18} />
          <select
            value={filterSeverity}
            onChange={(e) => setFilterSeverity(e.target.value)}
            className="filter-select"
          >
            <option value="ALL">All Severity Levels</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </div>

        <div className="filter-group alerts-filter-select">
          <select
            value={filterResolved}
            onChange={(e) => setFilterResolved(e.target.value)}
            className="filter-select"
          >
            <option value="UNRESOLVED">Unresolved</option>
            <option value="RESOLVED">Resolved</option>
            <option value="ALL">All Status</option>
          </select>
        </div>

        <div className="filter-info">
          Showing {paginatedAlerts.length} of {filteredAlerts.length} alerts
        </div>
      </div>

      {/* Alerts List */}
      <div className="alerts-list">
        {paginatedAlerts.map((alert) => (
          <div
            key={alert.id}
            className={`alert-item ${alert.isResolved ? 'resolved' : ''}`}
            style={{ borderLeftColor: getSeverityColor(alert.severity) }}
          >
            <div className="alert-content">
              <div className="alert-header">
                <h3 className="alert-severity">{alert.severity}</h3>
                <span className="alert-device">{alert.device?.deviceName || 'Unknown Device'}</span>
                {alert.isResolved && <span className="resolved-badge">✓ Resolved</span>}
              </div>
              <p className="alert-message">{alert.message}</p>
              <div className="alert-meta">
                <span className="alert-time">
                  {new Date(alert.createdAt).toLocaleString()}
                </span>
              </div>
            </div>

            {!alert.isResolved && (
              <button
                onClick={() => markAsResolved(alert.id)}
                className="alert-action"
                title="Mark as resolved"
              >
                <Check size={18} />
              </button>
            )}
          </div>
        ))}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="pagination">
          <button
            onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
            disabled={currentPage === 1}
            className="pagination-btn"
          >
            <ChevronLeft size={18} />
            Previous
          </button>

          <div className="pagination-info">
            Page {currentPage} of {totalPages}
          </div>

          <button
            onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
            disabled={currentPage === totalPages}
            className="pagination-btn"
          >
            Next
            <ChevronRight size={18} />
          </button>
        </div>
      )}

      {filteredAlerts.length === 0 && (
        <div className="empty-state">
          <p>No alerts matching your filters</p>
        </div>
      )}
    </div>
  );
};

export default AlertsPage;
