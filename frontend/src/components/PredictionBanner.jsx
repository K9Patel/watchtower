import React from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

const PredictionBanner = ({ trend }) => {
  if (!trend) return null;

  const dataPointCount = Number(trend.dataPointCount || 0);
  const hasEnoughData = dataPointCount >= 5 && trend.trendLabel !== 'INSUFFICIENT_DATA';

  const getStyle = () => {
    if (!hasEnoughData) {
      return {
        color: 'var(--color-info)',
        bg: 'rgba(59, 130, 246, 0.1)',
        border: 'rgba(59, 130, 246, 0.3)',
        icon: <Minus size={18} />,
      };
    }

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
  const predictedNext = Number.isFinite(Number(trend.predictedNext)) ? Number(trend.predictedNext).toFixed(1) : '0.0';
  const slope = Number.isFinite(Number(trend.slope)) ? Number(trend.slope).toFixed(2) : '0.00';
  const confidence = Number.isFinite(Number(trend.confidence)) ? Number(trend.confidence).toFixed(1) : '0.0';

  return (
    <div className="prediction-banner" style={{ 
      background: style.bg, border: `1px solid ${style.border}`, borderRadius: '12px', 
      padding: '16px 20px', marginBottom: '24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' 
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{ color: style.color, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.2)', padding: '8px', borderRadius: '8px' }}>
          {style.icon}
        </div>
        <div>
          <h3 style={{ fontSize: '14px', margin: 0, color: 'var(--text-primary)' }}>AI Trend Analysis (Adaptive Hybrid)</h3>
          <p style={{ fontSize: '13px', margin: 0, color: 'var(--text-secondary)' }}>
            {hasEnoughData
              ? (
                <>
                  Based on the last {dataPointCount} readings, bandwidth is <strong>{trend.trendLabel.replaceAll('_', ' ')}</strong> at a rate of {slope}% per sample with {confidence}% confidence.
                </>
              )
              : (
                <>
                  Collecting baseline traffic data ({dataPointCount}/5 samples). Predictions will appear automatically once enough readings are captured.
                </>
              )}
          </p>
        </div>
      </div>
      <div style={{ textAlign: 'right', minWidth: '180px' }}>
        <p style={{ fontSize: '11px', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', margin: 0 }}>
          {hasEnoughData ? 'Predicted Next' : 'Status'}
        </p>
        <div style={{ fontSize: '20px', fontWeight: 700, color: style.color, marginBottom: '6px' }}>
          {hasEnoughData ? `${predictedNext}%` : 'Learning'}
        </div>

        <p style={{ fontSize: '11px', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', margin: 0 }}>
          Confidence
        </p>
        <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-primary)' }}>
          {hasEnoughData ? `${confidence}%` : 'Calibrating'}
        </div>
      </div>
    </div>
  );
};

export default PredictionBanner;
