import React, { createContext, useState, useEffect, useContext } from 'react';

export const SettingsContext = createContext(null);

export const SettingsProvider = ({ children }) => {
  const [settings, setSettings] = useState({
    refreshInterval: 10,
    theme: 'dark', // 'dark', 'light', 'auto'
    emailNotifications: true,
    criticalAlerts: true,
    highAlerts: true,
    mediumAlerts: false,
    dataRetention: 30,
    simulatorMode: false,
  });

  // Load from localStorage if available
  useEffect(() => {
    const saved = localStorage.getItem('watchtower_settings');
    if (saved) {
      try {
        setSettings(JSON.parse(saved));
      } catch (e) {
        console.error("Failed to parse settings", e);
      }
    }
  }, []);

  // Save to localStorage and apply theme
  useEffect(() => {
    localStorage.setItem('watchtower_settings', JSON.stringify(settings));

    let activeTheme = settings.theme;
    if (activeTheme === 'auto') {
      activeTheme = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    }

    if (activeTheme === 'light') {
      document.body.classList.add('light-mode');
    } else {
      document.body.classList.remove('light-mode');
    }
  }, [settings]);

  const updateSettings = (newSettings) => {
    setSettings(newSettings);
  };

  const updateSetting = (key, value) => {
    setSettings((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <SettingsContext.Provider value={{ settings, updateSettings, updateSetting }}>
      {children}
    </SettingsContext.Provider>
  );
};

export const useSettings = () => useContext(SettingsContext);
