import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Activity, BarChart3, FileText, Settings, Menu, X, Home, AlertCircle, LogOut, User } from 'lucide-react';
import { useAuth } from './context/AuthContext';
import { logoutApi } from './config/authApi';
import { API_BASE_URL } from './config/api';
import './Layout.css';

const Layout = ({ children }) => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [unresolvedAlerts, setUnresolvedAlerts] = useState(0);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  useEffect(() => {
    const fetchCount = async () => {
      try {
        const res = await axios.get(`${API_BASE_URL}/alerts/count`);
        setUnresolvedAlerts(res.data?.unresolved || 0);
      } catch (e) {
        // Keep UI stable even if alerts API is temporarily unavailable.
      }
    };

    fetchCount();
    const id = setInterval(fetchCount, 10000);
    return () => clearInterval(id);
  }, []);

  const navItems = [
    { path: '/', label: 'Overview', icon: Home },
    { path: '/devices', label: 'Devices', icon: Activity },
    { path: '/alerts', label: 'Alerts', icon: AlertCircle },
    { path: '/analytics', label: 'Analytics', icon: BarChart3 },
    { path: '/reports', label: 'Reports', icon: FileText },
    { path: '/settings', label: 'Settings', icon: Settings },
  ];

  const isActive = (path) => location.pathname === path;

  const handleLogout = async () => {
    try {
      await logoutApi();
    } catch (err) {
      // Logout even if API call fails
    }
    logout();
    navigate('/login', { replace: true });
  };

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
                style={{ position: 'relative' }}
              >
                <Icon size={20} />
                {sidebarOpen && <span>{item.label}</span>}
                {item.path === '/alerts' && unresolvedAlerts > 0 && (
                  <span style={{
                    position: 'absolute',
                    right: sidebarOpen ? '12px' : '6px',
                    top: '8px',
                    minWidth: '18px',
                    height: '18px',
                    borderRadius: '9px',
                    background: '#ef4444',
                    color: 'white',
                    fontSize: '10px',
                    fontWeight: 700,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '0 5px',
                    lineHeight: 1
                  }}>
                    {unresolvedAlerts > 99 ? '99+' : unresolvedAlerts}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          {sidebarOpen ? (
            <div className="user-info">
              <div className="user-avatar">
                {user?.avatar ? (
                  <img src={user.avatar} alt={user.name} />
                ) : (
                  <User size={18} />
                )}
              </div>
              <div className="user-details">
                <p className="user-name">{user?.name || 'User'}</p>
                <p className="user-email">{user?.email || ''}</p>
              </div>
              <button
                className="logout-btn"
                onClick={handleLogout}
                title="Sign out"
              >
                <LogOut size={16} />
              </button>
            </div>
          ) : (
            <button
              className="nav-item logout-collapsed"
              onClick={handleLogout}
              title="Sign out"
            >
              <LogOut size={20} />
            </button>
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
