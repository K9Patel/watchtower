import React, { useMemo } from 'react';
import { Activity, Users, Monitor, ShieldAlert } from 'lucide-react';

const SummaryCards = ({ summary, devices = [], replay = null, healthScore = null }) => {
  if (!summary) return null;

  const normalizedHealthScore = Number.isFinite(Number(healthScore))
    ? Math.max(0, Math.min(100, Number(healthScore)))
    : Number.isFinite(Number(summary.health_score))
      ? Math.max(0, Math.min(100, Number(summary.health_score)))
      : Number.isFinite(Number(summary.healthScore))
        ? Math.max(0, Math.min(100, Number(summary.healthScore)))
        : null;

  const isHealthCritical = normalizedHealthScore !== null && normalizedHealthScore < 50;

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
      icon: <Activity size={24} color="#f8fafc" />
    },
    {
      title: 'Top Consumer',
      value: computed.topConsumer,
      subtitle: 'Highest single bandwidth',
      icon: <Users size={24} color="#f8fafc" />
    },
    {
      title: 'Active Devices',
      value: `${computed.onlineDevices} / ${computed.totalDevices}`,
      subtitle: 'Connected to network',
      icon: <Monitor size={24} color="#f8fafc" />
    },
    {
      title: 'Anomaly Scans',
      value: 'Every 30s',
      subtitle: 'Z-Score active',
      icon: <ShieldAlert size={24} color="#f8fafc" />
    }
  ];

  return (
    <div className="summary-cards-grid">
      {cards.map((card, i) => (
        <div key={i} className="neon-card-outer summary-card" style={{ padding: '1px' }}>
          <div className="neon-card-dot"></div>
          <div className="neon-card-inner" style={{ padding: '24px' }}>
            <div className="neon-card-ray"></div>
            <div className="neon-card-line topl"></div>
            <div className="neon-card-line leftl"></div>
            <div className="neon-card-line bottoml"></div>
            <div className="neon-card-line rightl"></div>
            
            <div className="summary-card-icon-shell">
              {card.icon}
            </div>
            <div className="summary-card-content summary-card-content-themed" style={{ flex: 1, minWidth: 0, position: 'relative', zIndex: 2 }}>
              <p className="summary-card-title-themed">
                {card.title}
              </p>
              <h2 className="summary-card-value summary-card-value-themed" style={{ fontSize: '24px', fontWeight: 700, margin: '0 0 2px 0' }}>
                {card.value}
              </h2>
              <p className="summary-card-subtitle-themed">
                {card.subtitle}
              </p>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
};

export default SummaryCards;
