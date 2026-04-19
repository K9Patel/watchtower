import React, { useMemo } from 'react';
import { Activity, Users, Monitor, ShieldAlert } from 'lucide-react';

const SummaryCards = ({ summary, devices = [], replay = null }) => {
  if (!summary) return null;

  const computed = useMemo(() => {
    if (replay) {
      return {
        load: Number(replay.totalLoadPercent || 0),
        topConsumer: replay.topConsumer || 'N/A',
        onlineDevices: Number(replay.onlineDevices || 0),
        totalDevices: Number(replay.totalDevices || 0),
      };
    }

    const activeDevices = Array.isArray(devices)
      ? devices.filter((device) => device?.isActive !== false)
      : [];

    const onlineDevices = activeDevices.filter(
      (device) => String(device?.status || '').toUpperCase() === 'ONLINE'
    ).length;

    const totalDevices = activeDevices.length || Number(summary.totalDevices || 0);
    const normalizedLoad = Number.isFinite(Number(summary.totalLoadPercent))
      ? Math.max(0, Math.min(100, Number(summary.totalLoadPercent)))
      : 0;

    let topConsumer = summary.topConsumer;
    if (!topConsumer || topConsumer === 'N/A') {
      const shareEntries = Object.entries(summary.bandwidthShare || {});
      if (shareEntries.length > 0) {
        shareEntries.sort((a, b) => Number(b[1]) - Number(a[1]));
        topConsumer = shareEntries[0][0];
      }
    }

    return {
      load: normalizedLoad,
      topConsumer: topConsumer || 'No active usage yet',
      onlineDevices: onlineDevices || Number(summary.onlineDevices || 0),
      totalDevices,
    };
  }, [devices, replay, summary]);

  const cards = [
    {
      title: 'Global Network Load',
      value: `${computed.load.toFixed(1)}%`,
      subtitle: 'Moving 60s average',
      icon: <Activity size={24} color="var(--accent-color)" />,
      bg: 'rgba(99, 102, 241, 0.1)'
    },
    {
      title: 'Top Consumer',
      value: computed.topConsumer,
      subtitle: 'Highest single bandwidth',
      icon: <Users size={24} color="var(--color-medium)" />,
      bg: 'rgba(234, 179, 8, 0.1)'
    },
    {
      title: 'Active Devices',
      value: `${computed.onlineDevices} / ${computed.totalDevices}`,
      subtitle: 'Connected to network',
      icon: <Monitor size={24} color="var(--color-low)" />,
      bg: 'rgba(34, 197, 94, 0.1)'
    },
    {
      title: 'Anomaly Scans',
      value: 'Every 30s',
      subtitle: 'Z-Score active',
      icon: <ShieldAlert size={24} color="var(--color-info)" />,
      bg: 'rgba(59, 130, 246, 0.1)'
    }
  ];

  return (
    <div className="summary-cards-grid">
      {cards.map((card, i) => (
        <div key={i} className="glass-panel summary-card">
          <div style={{ background: card.bg, padding: '16px', borderRadius: '14px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {card.icon}
          </div>
          <div className="summary-card-content">
            <p style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)' }}>{card.title}</p>
            <h2 className="summary-card-value" style={{ fontSize: '24px', fontWeight: 700, margin: '4px 0 2px 0' }}>{card.value}</h2>
            <p style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>{card.subtitle}</p>
          </div>
        </div>
      ))}
    </div>
  );
};

export default SummaryCards;
