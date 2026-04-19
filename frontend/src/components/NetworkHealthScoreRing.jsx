import React from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

const getNumeric = (value, fallback = 0) => {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : fallback;
};

const clampScore = (value) => {
  const rounded = Math.round(value);
  if (rounded < 0) return 0;
  if (rounded > 100) return 100;
  return rounded;
};

const getHealthBand = (score) => {
  if (score >= 85) {
    return { key: 'green', color: '#22c55e', label: 'Healthy' };
  }

  if (score >= 50) {
    return { key: 'yellow', color: '#f59e0b', label: 'Watch' };
  }

  return { key: 'red', color: '#ef4444', label: 'Critical' };
};

const normalizeTrend = (trendValue) => {
  const trend = String(trendValue || 'stable').toLowerCase();
  if (trend.includes('improv')) return 'improving';
  if (trend.includes('degrad')) return 'degrading';
  return 'stable';
};

const NetworkHealthScoreRing = ({ summary }) => {
  if (!summary) return null;

  const healthScore = clampScore(
    getNumeric(summary.health_score, getNumeric(summary.healthScore, getNumeric(summary.systemHealth, 0)))
  );

  const bandwidthScore = clampScore(getNumeric(summary.bandwidth_score, getNumeric(summary.bandwidthScore, 0)));
  const latencyScore = clampScore(getNumeric(summary.latency_score, getNumeric(summary.latencyScore, 0)));
  const alertScore = clampScore(getNumeric(summary.alert_score, getNumeric(summary.alertScore, 100)));
  const uptimeScore = clampScore(getNumeric(summary.uptime_score, getNumeric(summary.uptimeScore, 100)));

  const trend = normalizeTrend(summary.trend || summary.healthTrend);
  const band = getHealthBand(healthScore);
  const ringAngle = Math.round((healthScore / 100) * 360);

  const TrendIcon = trend === 'improving'
    ? TrendingUp
    : trend === 'degrading'
      ? TrendingDown
      : Minus;

  const tooltip = `Bandwidth: ${bandwidthScore}, Latency: ${latencyScore}, Alerts: ${alertScore}, Uptime: ${uptimeScore}`;

  return (
    <div className="glass-panel health-score-panel" title={tooltip}>
      <div className="health-score-panel-header">
        <h2>Network Health Score</h2>
        <span className={`health-status-pill ${band.key}`}>{band.label}</span>
      </div>

      <div className="health-score-content">
        <div
          className="health-score-ring"
          style={{
            background: `conic-gradient(${band.color} ${ringAngle}deg, rgba(148, 163, 184, 0.22) ${ringAngle}deg 360deg)`
          }}
        >
          <div className="health-score-ring-core">
            <div className="health-score-value">{healthScore}</div>
            <div className="health-score-scale">/ 100</div>
          </div>
        </div>

        <div className="health-score-details">
          <div className={`health-trend-badge ${trend}`}>
            <TrendIcon size={16} />
            <span>{trend}</span>
          </div>
          <p className="health-score-caption">
            Composite score from bandwidth utilization, latency, active alerts, and device uptime.
          </p>
          <p className="health-score-breakdown">
            Bandwidth: <strong>{bandwidthScore}</strong> | Latency: <strong>{latencyScore}</strong> | Alerts: <strong>{alertScore}</strong> | Uptime: <strong>{uptimeScore}</strong>
          </p>
        </div>
      </div>
    </div>
  );
};

export default NetworkHealthScoreRing;
