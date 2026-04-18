import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Activity, BarChart3, Zap, FileText, Settings, Menu, X, Home, AlertCircle } from 'lucide-react';
import './Layout.css';

const Layout = ({ children }) => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const location = useLocation();

  const navItems = [
    { path: '/', label: 'Overview', icon: Home },
    { path: '/devices', label: 'Devices', icon: Activity },
    { path: '/alerts', label: 'Alerts', icon: AlertCircle },
    { path: '/analytics', label: 'Analytics', icon: BarChart3 },
    { path: '/reports', label: 'Reports', icon: FileText },
    { path: '/settings', label: 'Settings', icon: Settings },
  ];

  const isActive = (path) => location.pathname === path;

  return (
    <div className="layout-container">
      {/* Sidebar */}
      <aside className={`sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
        <div className="sidebar-header">
          <div className="logo">
            <div className="logo-icon">
              <Activity size={24} />
            </div>
            {sidebarOpen && (
              <div>
                <h2>Watch<span className="logo-accent">Tower</span></h2>
                <p className="logo-subtitle">Network Control</p>
              </div>
            )}
          </div>
          <button 
            className="sidebar-toggle"
            onClick={() => setSidebarOpen(!sidebarOpen)}
          >
            {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>

        <nav className="sidebar-nav">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`nav-item ${isActive(item.path) ? 'active' : ''}`}
                title={!sidebarOpen ? item.label : ''}
              >
                <Icon size={20} />
                {sidebarOpen && <span>{item.label}</span>}
              </Link>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          {sidebarOpen && (
            <div className="status-info">
              <div className="status-badge">
                <div className="status-dot"></div>
                <span>Live</span>
              </div>
              <p>System Status: Online</p>
            </div>
          )}
        </div>
      </aside>

      {/* Main Content */}
      <main className="main-content">
        {children}
      </main>
    </div>
  );
};

export default Layout;
