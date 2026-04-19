import React from 'react';

const toneForService = (service) => {
  const normalized = String(service || 'UNKNOWN').toUpperCase();
  switch (normalized) {
    case 'YOUTUBE':
      return { bg: 'rgba(239, 68, 68, 0.18)', color: '#f87171', border: 'rgba(239, 68, 68, 0.35)' };
    case 'NETFLIX':
      return { bg: 'rgba(225, 29, 72, 0.2)', color: '#fb7185', border: 'rgba(225, 29, 72, 0.38)' };
    case 'WHATSAPP':
      return { bg: 'rgba(34, 197, 94, 0.18)', color: '#4ade80', border: 'rgba(34, 197, 94, 0.35)' };
    case 'BGMI':
      return { bg: 'rgba(234, 179, 8, 0.2)', color: '#facc15', border: 'rgba(234, 179, 8, 0.35)' };
    case 'ZOOM':
      return { bg: 'rgba(59, 130, 246, 0.2)', color: '#60a5fa', border: 'rgba(59, 130, 246, 0.35)' };
    default:
      return { bg: 'rgba(100, 116, 139, 0.22)', color: '#cbd5e1', border: 'rgba(100, 116, 139, 0.4)' };
  }
};

const formatService = (service) => {
  if (!service) return 'Unknown';
  const normalized = String(service).toUpperCase();
  if (normalized === 'YOUTUBE') return 'YouTube';
  if (normalized === 'NETFLIX') return 'Netflix';
  if (normalized === 'WHATSAPP') return 'WhatsApp';
  if (normalized === 'BGMI') return 'BGMI';
  if (normalized === 'ZOOM') return 'Zoom';
  return service.replaceAll('_', ' ');
};

const TrafficBadge = ({ service, category, confidence }) => {
  const tone = toneForService(service);
  const label = formatService(service);
  const subtitle = category ? `${category}${Number.isFinite(Number(confidence)) ? ` • ${confidence}%` : ''}` : null;

  return (
    <span
      title={subtitle ? `${label} (${subtitle})` : label}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: '4px 9px',
        borderRadius: '999px',
        border: `1px solid ${tone.border}`,
        background: tone.bg,
        color: tone.color,
        fontSize: '11px',
        fontWeight: 700,
        letterSpacing: '0.02em',
      }}
    >
      <span
        style={{
          width: '7px',
          height: '7px',
          borderRadius: '50%',
          background: tone.color,
          boxShadow: `0 0 8px ${tone.color}`,
        }}
      />
      {label}
    </span>
  );
};

export default TrafficBadge;
