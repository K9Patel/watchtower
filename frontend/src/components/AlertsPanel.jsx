import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { ShieldAlert, AlertTriangle, AlertCircle, CheckCircle, Info } from 'lucide-react';
import { API_BASE_URL } from '../config/api';

const AlertsPanel = ({ alerts, onRefresh }) => {
  const [recommendations, setRecommendations] = useState([]);

  useEffect(() => {
    const fetchRecs = async () => {
      try {
        const res = await axios.get(`${API_BASE_URL}/trend/recommend`);
        setRecommendations(res.data);
      } catch (e) {
        console.error(e);
      }
    };
    fetchRecs();
    const id = setInterval(fetchRecs, 10000);
    return () => clearInterval(id);
  }, []);

  const resolveAlert = async (id) => {
    try {
      await axios.put(`${API_BASE_URL}/alerts/${id}/resolve`);
      onRefresh();
    } catch (e) {
      console.error(e);
    }
  };

  const getSeverityStyles = (severity) => {
    switch (severity) {
      case 'CRITICAL': return { bg: 'rgba(239, 68, 68, 0.15)', border: 'rgba(239, 68, 68, 0.3)', color: '#ef4444', icon: <AlertCircle size={18} /> };
      case 'HIGH':     return { bg: 'rgba(249, 115, 22, 0.15)', border: 'rgba(249, 115, 22, 0.3)', color: '#f97316', icon: <AlertTriangle size={18} /> };
      case 'MEDIUM':   return { bg: 'rgba(234, 179, 8, 0.15)', border: 'rgba(234, 179, 8, 0.3)', color: '#eab308', icon: <Info size={18} /> };
      default:         return { bg: 'rgba(34, 197, 94, 0.15)', border: 'rgba(34, 197, 94, 0.3)', color: '#22c55e', icon: <CheckCircle size={18} /> };
    }
  };

  return (
    <>
      {/* Alerts Feed */}
      <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column', maxHeight: '450px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h3 style={{ fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldAlert size={18} color="#ef4444" /> Active Alerts ({alerts.length})
          </h3>
          {alerts.length > 0 && (
            <button 
              onClick={async () => {
                await axios.put(`${API_BASE_URL}/alerts/resolve-all`);
                onRefresh();
              }}
              style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '11px', textDecoration: 'underline' }}>
              Resolve All
            </button>
          )}
        </div>

        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px', paddingRight: '8px' }}>
          {alerts.map(a => {
            const style = getSeverityStyles(a.severity);
            return (
              <div key={a.id} style={{ 
                background: style.bg, border: `1px solid ${style.border}`, borderRadius: '10px', 
                padding: '16px', animation: 'fadeIn 0.3s ease' 
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: style.color, fontWeight: 700, fontSize: '13px' }}>
                    {style.icon} {a.alertType}
                  </div>
                  <button onClick={() => resolveAlert(a.id)} style={{ background: 'transparent', border: 'none', color: '#94a3b8' }}>
                    <CheckCircle size={18} />
                  </button>
                </div>
                <p style={{ fontSize: '13px', color: '#cbd5e1', lineHeight: 1.5 }}>{a.message}</p>
                <div style={{ marginTop: '12px', fontSize: '11px', color: '#64748b', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Device: {a.device?.deviceName || `#${a.device?.id || 'N/A'}`}</span>
                  <span>{new Date(a.createdAt).toLocaleTimeString()}</span>
                </div>
              </div>
            );
          })}
          
          {alerts.length === 0 && (
            <div style={{ textAlign: 'center', padding: '40px 20px', color: '#64748b' }}>
              <CheckCircle size={40} style={{ marginBottom: '16px', opacity: 0.5 }} />
              <p>No active alerts. Network is functioning nominally.</p>
            </div>
          )}
        </div>
      </div>

      {/* Recommendations Engine (Rule Map) */}
      <div className="glass-panel" style={{ maxHeight: '350px', display: 'flex', flexDirection: 'column' }}>
        <h3 style={{ fontSize: '16px', marginBottom: '20px', color: '#a5b4fc' }}>AI Remediation Advice</h3>
        <div style={{ overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {recommendations.map((rec, i) => (
            <div key={i} style={{ padding: '12px', background: 'rgba(0,0,0,0.2)', borderRadius: '8px', borderLeft: `3px solid ${getSeverityStyles(rec.severity).color}` }}>
              <div style={{ fontSize: '11px', fontWeight: 600, color: '#94a3b8', marginBottom: '4px', display: 'flex', justifyContent: 'space-between' }}>
                <span>{rec.alertType} • {rec.severity}</span>
                {rec.device && <span>{rec.device}</span>}
              </div>
              <div style={{ marginBottom: '6px' }}>
                <span style={{
                  fontSize: '10px',
                  fontWeight: 700,
                  letterSpacing: '0.06em',
                  color: rec.source === 'AI' ? '#7dd3fc' : '#cbd5e1',
                  background: rec.source === 'AI' ? 'rgba(14, 116, 144, 0.25)' : 'rgba(71, 85, 105, 0.35)',
                  border: rec.source === 'AI' ? '1px solid rgba(56, 189, 248, 0.35)' : '1px solid rgba(148, 163, 184, 0.25)',
                  borderRadius: '999px',
                  padding: '2px 8px',
                  textTransform: 'uppercase'
                }}>
                  {rec.source === 'AI' ? 'AI Generated' : 'Rule Fallback'}
                </span>
              </div>
              <p style={{ fontSize: '13px', color: '#e2e8f0', lineHeight: 1.4 }}>{rec.advice}</p>
            </div>
          ))}
        </div>
      </div>
    </>
  );
};

export default AlertsPanel;
