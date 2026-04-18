import React, { useState } from 'react';
import { Bell, Lock, Palette, Database, Save } from 'lucide-react';
import { useSettings } from '../context/SettingsContext';
import './Pages.css';

const SettingsPage = () => {
  const { settings, updateSetting } = useSettings();
  const [saved, setSaved] = useState(false);

  const handleChange = (key, value) => {
    updateSetting(key, value);
    setSaved(false);
  };

  const handleSave = () => {
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Settings</h1>
          <p className="page-subtitle">Configure WatchTower preferences and system settings</p>
        </div>
      </div>

      {/* System Preferences */}
      <div className="settings-section">
        <div className="section-header">
          <Palette size={20} />
          <h2>System Preferences</h2>
        </div>

        <div className="glass-panel settings-panel">
          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Dashboard Refresh Interval</span>
              <span className="label-description">How often to update dashboard data (in seconds)</span>
            </div>
            <div className="setting-control">
              <select
                value={settings.refreshInterval}
                onChange={(e) => handleChange('refreshInterval', parseInt(e.target.value))}
                className="settings-select"
              >
                <option value={5}>5 seconds</option>
                <option value={10}>10 seconds</option>
                <option value={30}>30 seconds</option>
                <option value={60}>1 minute</option>
              </select>
            </div>
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Theme</span>
              <span className="label-description">Application appearance</span>
            </div>
            <div className="setting-control">
              <select
                value={settings.theme}
                onChange={(e) => handleChange('theme', e.target.value)}
                className="settings-select"
              >
                <option value="dark">Dark Mode</option>
                <option value="light">Light Mode</option>
                <option value="auto">Auto (System)</option>
              </select>
            </div>
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Data Retention</span>
              <span className="label-description">How many days to keep historical data</span>
            </div>
            <div className="setting-control">
              <input
                type="number"
                value={settings.dataRetention}
                onChange={(e) => handleChange('dataRetention', parseInt(e.target.value))}
                className="settings-input"
                min="7"
                max="365"
              />
              <span className="input-unit">days</span>
            </div>
          </div>
        </div>
      </div>

      {/* Notifications */}
      <div className="settings-section">
        <div className="section-header">
          <Bell size={20} />
          <h2>Notifications</h2>
        </div>

        <div className="glass-panel settings-panel">
          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Email Notifications</span>
              <span className="label-description">Receive alerts via email</span>
            </div>
            <div className="setting-control">
              <label className="toggle-switch">
                <input
                  type="checkbox"
                  checked={settings.emailNotifications}
                  onChange={(e) => handleChange('emailNotifications', e.target.checked)}
                />
                <span className="toggle-slider"></span>
              </label>
            </div>
          </div>

          {settings.emailNotifications && (
            <>
              <div className="setting-item">
                <div className="setting-label">
                  <span className="label-title">Critical Alerts</span>
                  <span className="label-description">Notify on critical issues</span>
                </div>
                <div className="setting-control">
                  <label className="toggle-switch">
                    <input
                      type="checkbox"
                      checked={settings.criticalAlerts}
                      onChange={(e) => handleChange('criticalAlerts', e.target.checked)}
                    />
                    <span className="toggle-slider"></span>
                  </label>
                </div>
              </div>

              <div className="setting-item">
                <div className="setting-label">
                  <span className="label-title">High Priority Alerts</span>
                  <span className="label-description">Notify on high priority issues</span>
                </div>
                <div className="setting-control">
                  <label className="toggle-switch">
                    <input
                      type="checkbox"
                      checked={settings.highAlerts}
                      onChange={(e) => handleChange('highAlerts', e.target.checked)}
                    />
                    <span className="toggle-slider"></span>
                  </label>
                </div>
              </div>

              <div className="setting-item">
                <div className="setting-label">
                  <span className="label-title">Medium Priority Alerts</span>
                  <span className="label-description">Notify on medium priority issues</span>
                </div>
                <div className="setting-control">
                  <label className="toggle-switch">
                    <input
                      type="checkbox"
                      checked={settings.mediumAlerts}
                      onChange={(e) => handleChange('mediumAlerts', e.target.checked)}
                    />
                    <span className="toggle-slider"></span>
                  </label>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Security */}
      <div className="settings-section">
        <div className="section-header">
          <Lock size={20} />
          <h2>Security</h2>
        </div>

        <div className="glass-panel settings-panel">
          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Change Password</span>
              <span className="label-description">Update your account password</span>
            </div>
            <button className="btn-secondary">Change Password</button>
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Two-Factor Authentication</span>
              <span className="label-description">Add extra security to your account</span>
            </div>
            <button className="btn-secondary">Configure 2FA</button>
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">API Keys</span>
              <span className="label-description">Manage API access tokens</span>
            </div>
            <button className="btn-secondary">Manage Keys</button>
          </div>
        </div>
      </div>

      {/* Database & Storage */}
      <div className="settings-section">
        <div className="section-header">
          <Database size={20} />
          <h2>Database & Storage</h2>
        </div>

        <div className="glass-panel settings-panel">
          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Database Status</span>
              <span className="label-description">PostgreSQL connection</span>
            </div>
            <div className="status-badge active">✓ Connected</div>
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Storage Usage</span>
              <span className="label-description">Database storage space</span>
            </div>
            <div className="storage-bar">
              <div className="storage-fill" style={{ width: '45%' }}></div>
              <span className="storage-text">4.5 GB / 10 GB</span>
            </div>
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Backup Database</span>
              <span className="label-description">Create a database backup</span>
            </div>
            <button className="btn-secondary">Backup Now</button>
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span className="label-title">Clear Old Data</span>
              <span className="label-description">Remove data older than specified days</span>
            </div>
            <button className="btn-secondary btn-danger">Clear Data</button>
          </div>
        </div>
      </div>

      {/* Save Settings */}
      <div className="settings-actions">
        <button onClick={handleSave} className="btn-primary">
          <Save size={18} />
          Save Settings
        </button>
        {saved && <span className="save-confirmation">✓ Settings saved successfully</span>}
      </div>
    </div>
  );
};

export default SettingsPage;
