import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { ChevronLeft, ChevronRight, Search, Filter } from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import './Pages.css';

const DevicesPage = () => {
  const navigate = useNavigate();
  const [devices, setDevices] = useState([]);
  const [summary, setSummary] = useState({ onlineDevices: 0, totalDevices: 0 });
  const [filteredDevices, setFilteredDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState('ALL');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 12;

  useEffect(() => {
    fetchData();
    const intervalId = setInterval(fetchData, 10000); // Refresh every 10 seconds for live data
    return () => clearInterval(intervalId);
  }, []);

  useEffect(() => {
    applyFilters();
  }, [searchTerm, filterType, devices]);

  const fetchData = async () => {
    try {
      const [devicesRes, summaryRes] = await Promise.all([
        axios.get(`${API_BASE_URL}/devices/live`), // New live endpoint with bandwidth
        axios.get(`${API_BASE_URL}/stats/summary`),
      ]);
      console.log('Live devices with bandwidth:', devicesRes.data);
      setDevices(devicesRes.data);
      setSummary(summaryRes.data);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching data:', error);
      setLoading(false);
    }
  };

  const applyFilters = () => {
    let filtered = devices;

    if (filterType !== 'ALL') {
      filtered = filtered.filter((d) => d.deviceType === filterType);
    }

    if (searchTerm) {
      filtered = filtered.filter(
        (d) =>
          d.deviceName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          d.macAddress?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          d.ipAddress?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    setFilteredDevices(filtered);
    setCurrentPage(1);
  };

  const paginatedDevices = filteredDevices.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const totalPages = Math.ceil(filteredDevices.length / itemsPerPage);

  const getStatusBadge = (status, isActive) => {
    // Use actual status from real-time monitoring, not just isActive flag
    const isOnline = status === 'ONLINE' && isActive;
    return (
      <span className={`status-badge ${isOnline ? 'active' : 'inactive'}`}>
        {isOnline ? '● Online' : '● Offline'}
      </span>
    );
  };

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading devices...</p>
      </div>
    );
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Devices</h1>
          <p className="page-subtitle">Manage and monitor all network devices</p>
        </div>
        <div className="header-stats">
          <div className="stat">
            <span className="stat-value">{summary.totalDevices}</span>
            <span className="stat-label">Total Devices</span>
          </div>
          <div className="stat">
            <span className="stat-value" style={{ color: '#22c55e' }}>
              {summary.onlineDevices}
            </span>
            <span className="stat-label">Online</span>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="glass-panel filters-panel">
        <div className="filter-group">
          <Search size={18} />
          <input
            type="text"
            placeholder="Search by name, MAC, or IP address..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="search-input"
          />
        </div>

        <div className="filter-group">
          <Filter size={18} />
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            className="filter-select"
          >
            <option value="ALL">All Types</option>
            <option value="STUDENT">Student Devices</option>
            <option value="STAFF">Staff Devices</option>
            <option value="ADMIN">Admin Devices</option>
          </select>
        </div>

        <div className="filter-info">
          Showing {paginatedDevices.length} of {filteredDevices.length} devices
        </div>
      </div>

      {/* Devices Grid */}
      <div className="devices-grid">
        {paginatedDevices.map((device) => (
          <div
            key={device.id}
            className="device-card"
            onClick={() => navigate(`/devices/${device.id}`)}
            style={{ cursor: 'pointer' }}
          >
            <div className="device-header">
              <h3 className="device-name">{device.deviceName}</h3>
              {getStatusBadge(device.status || 'OFFLINE', device.isActive)}
            </div>

            <div className="device-details">
              <div className="detail-row">
                <span className="detail-label">Type</span>
                <span className="detail-value badge">{device.deviceType}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">MAC Address</span>
                <span className="detail-value font-mono">{device.macAddress}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">IP Address</span>
                <span className="detail-value font-mono">{device.ipAddress}</span>
              </div>
              {device.systemLoad !== undefined && (
                <div className="detail-row">
                  <span className="detail-label">Load</span>
                  <span className="detail-value">{device.systemLoad.toFixed(1)}%</span>
                </div>
              )}
              {device.bandwidth !== undefined && device.bandwidth > 0 && (
                <div className="detail-row">
                  <span className="detail-label">Bandwidth Share</span>
                  <span className="detail-value" style={{ color: '#3b82f6', fontWeight: 600 }}>
                    {device.bandwidth.toFixed(2)}%
                  </span>
                </div>
              )}
            </div>
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

      {filteredDevices.length === 0 && (
        <div className="empty-state">
          <p>No devices found matching your filters</p>
        </div>
      )}
    </div>
  );
};

export default DevicesPage;
