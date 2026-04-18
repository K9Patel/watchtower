import React from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

const PredictionBanner = ({ trend }) => {
  if (!trend || trend.dataPointCount < 5) return null;

  const getStyle = () => {
    switch (trend.trendLabel) {
      case 'RISING_FAST': 
      case 'RISING':
        return { color: 'var(--color-high)', bg: 'rgba(249, 115, 22, 0.1)', border: 'rgba(249, 115, 22, 0.3)', icon: <TrendingUp size={18} /> };
      case 'FALLING_FAST':
      case 'FALLING':
        return { color: 'var(--color-low)', bg: 'rgba(34, 197, 94, 0.1)', border: 'rgba(34, 197, 94, 0.3)', icon: <TrendingDown size={18} /> };
      default:
        return { color: 'var(--color-info)', bg: 'rgba(59, 130, 246, 0.1)', border: 'rgba(59, 130, 246, 0.3)', icon: <Minus size={18} /> };
    }
  };

  const style = getStyle();

  return (
    <div style={{ 
      background: style.bg, border: `1px solid ${style.border}`, borderRadius: '12px', 
      padding: '16px 20px', marginBottom: '24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' 
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{ color: style.color, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.2)', padding: '8px', borderRadius: '8px' }}>
          {style.icon}
        </div>
        <div>
          <h3 style={{ fontSize: '14px', margin: 0, color: 'var(--text-primary)' }}>AI Trend Analysis (Linear Regression)</h3>
          <p style={{ fontSize: '13px', margin: 0, color: 'var(--text-secondary)' }}>
            Based on the last 30 readings, bandwidth is <strong>{trend.trendLabel.replace('_', ' ')}</strong> at a rate of {trend.slope}% per second.
          </p>
        </div>
      </div>
      <div style={{ textAlign: 'right' }}>
        <p style={{ fontSize: '11px', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Predicted Next</p>
        <span style={{ fontSize: '20px', fontWeight: 700, color: style.color }}>{trend.predictedNext}%</span>
      </div>
    </div>
  );
};

export default PredictionBanner;
