import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Activity, BarChart3, FileText, Settings, Menu, X, Home, AlertCircle, LogOut, User } from 'lucide-react';
import { useAuth } from './context/AuthContext';
import { logoutApi } from './config/authApi';
import './Layout.css';

const Layout = ({ children }) => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

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
              >
                <Icon size={20} />
                {sidebarOpen && <span>{item.label}</span>}
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
