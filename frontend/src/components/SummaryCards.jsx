import React from 'react';
import { Activity, Users, Monitor, ShieldAlert } from 'lucide-react';

const SummaryCards = ({ summary }) => {
  if (!summary) return null;

  const cards = [
    {
      title: 'Global Network Load',
      value: `${summary.totalLoadPercent.toFixed(1)}%`,
      subtitle: 'Moving 60s average',
      icon: <Activity size={24} color="var(--accent-color)" />,
      bg: 'rgba(99, 102, 241, 0.1)'
    },
    {
      title: 'Top Consumer',
      value: summary.topConsumer,
      subtitle: 'Highest single bandwidth',
      icon: <Users size={24} color="var(--color-medium)" />,
      bg: 'rgba(234, 179, 8, 0.1)'
    },
    {
      title: 'Active Devices',
      value: `${summary.onlineDevices} / ${summary.totalDevices}`,
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
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '20px', marginBottom: '24px' }}>
      {cards.map((card, i) => (
        <div key={i} className="glass-panel" style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ background: card.bg, padding: '16px', borderRadius: '14px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {card.icon}
          </div>
          <div>
            <p style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)' }}>{card.title}</p>
            <h2 style={{ fontSize: '24px', fontWeight: 700, margin: '4px 0 2px 0' }}>{card.value}</h2>
            <p style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>{card.subtitle}</p>
          </div>
        </div>
      ))}
    </div>
  );
};

export default SummaryCards;
