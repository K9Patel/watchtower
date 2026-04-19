import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import axios from 'axios';
import ForceGraph2D from 'react-force-graph-2d';
import {
  Shield, ShieldAlert, ShieldX, AlertTriangle,
  RefreshCw, Target, Crosshair, Activity
} from 'lucide-react';
import { API_BASE_URL } from '../config/api';
import './Pages.css';

/* ─────────────────────────────────────────────────────────────
   Risk level → color mapping
───────────────────────────────────────────────────────────── */
const RISK_COLORS = {
  SAFE:       { fill: '#22c55e', glow: 'rgba(34,197,94,0.4)',  bg: 'rgba(34,197,94,0.12)' },
  SUSPICIOUS: { fill: '#eab308', glow: 'rgba(234,179,8,0.4)',  bg: 'rgba(234,179,8,0.12)' },
  HIGH_RISK:  { fill: '#f97316', glow: 'rgba(249,115,22,0.4)', bg: 'rgba(249,115,22,0.12)' },
  CRITICAL:   { fill: '#ef4444', glow: 'rgba(239,68,68,0.5)',  bg: 'rgba(239,68,68,0.15)' },
};

const EDGE_STYLES = {
  'correlated activity':                { color: '#64748b', dash: [5, 5], width: 1.5 },
  'possible lateral movement':          { color: '#f97316', dash: null,   width: 2   },
  'unknown device → high-value target': { color: '#ef4444', dash: null,   width: 2.5 },
};

/* ─────────────────────────────────────────────────────────────
   Stats bar
───────────────────────────────────────────────────────────── */
function StatsBar({ stats }) {
  if (!stats) return null;
  const items = [
    { label: 'Total Nodes',    value: stats.totalNodes,     color: '#6366f1', icon: <Target size={15}/> },
    { label: 'Total Edges',    value: stats.totalEdges,     color: '#8b5cf6', icon: <Activity size={15}/> },
    { label: 'Critical',       value: stats.criticalDevices,color: '#ef4444', icon: <ShieldX size={15}/> },
    { label: 'High Risk',      value: stats.highRiskDevices,color: '#f97316', icon: <ShieldAlert size={15}/> },
    { label: 'Safe',           value: stats.safeDevices,    color: '#22c55e', icon: <Shield size={15}/> },
  ];

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(130px,1fr))', gap: '12px', marginBottom: '20px' }}>
      {items.map(({ label, value, color, icon }) => (
        <div key={label} className="glass-panel" style={{ padding: '14px 16px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{ width: 32, height: 32, borderRadius: '8px', background: `${color}18`, color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {icon}
          </div>
          <div>
            <div style={{ fontSize: '18px', fontWeight: 800, color }}>{value}</div>
            <div style={{ fontSize: '9px', color: 'var(--text-secondary)', fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase' }}>{label}</div>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────
   Legend
───────────────────────────────────────────────────────────── */
function Legend() {
  return (
    <div className="glass-panel" style={{ padding: '16px 20px', marginBottom: '20px' }}>
      <div style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--text-secondary)', marginBottom: '12px' }}>
        Legend
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px' }}>
        {/* Node legend */}
        {Object.entries(RISK_COLORS).map(([level, colors]) => (
          <div key={level} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: 12, height: 12, borderRadius: '50%', background: colors.fill, boxShadow: `0 0 6px ${colors.glow}` }} />
            <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-secondary)' }}>
              {level.replace('_', ' ')}
            </span>
          </div>
        ))}

        {/* Edge legend */}
        <div style={{ borderLeft: '1px solid var(--panel-border)', paddingLeft: '16px', display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
          {Object.entries(EDGE_STYLES).map(([label, style]) => (
            <div key={label} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <div style={{ width: 20, height: 2, background: style.color, borderStyle: style.dash ? 'dashed' : 'solid' }} />
              <span style={{ fontSize: '10px', fontWeight: 600, color: 'var(--text-secondary)' }}>{label}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────
   Main page component
───────────────────────────────────────────────────────────── */
const AttackPathPage = () => {
  const [graphData, setGraphData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastRefresh, setLastRefresh] = useState(null);
  const [hoveredNode, setHoveredNode] = useState(null);
  const graphRef = useRef();
  const containerRef = useRef();
  const [dimensions, setDimensions] = useState({ width: 800, height: 500 });

  const fetchGraph = useCallback(async () => {
    try {
      const res = await axios.get(`${API_BASE_URL}/graph/attack-path`);
      setGraphData(res.data);
      setLastRefresh(new Date());
      setError(null);
      setLoading(false);
    } catch (err) {
      console.error('AttackPathPage fetch error:', err);
      setError('Failed to load attack path graph. Is the backend running?');
      setLoading(false);
    }
  }, []);

  // Initial fetch + 30s polling
  useEffect(() => {
    fetchGraph();
    const id = setInterval(fetchGraph, 30000);
    return () => clearInterval(id);
  }, [fetchGraph]);

  // Responsive container sizing
  useEffect(() => {
    const measure = () => {
      if (containerRef.current) {
        const rect = containerRef.current.getBoundingClientRect();
        setDimensions({ width: rect.width, height: Math.max(500, window.innerHeight - 420) });
      }
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [loading]);

  // Transform API data → react-force-graph format
  const forceGraphData = useMemo(() => {
    if (!graphData) return { nodes: [], links: [] };

    const criticalSet = new Set(graphData.criticalPath || []);

    return {
      nodes: graphData.nodes.map(n => ({
        id: n.id,
        label: n.label,
        ip: n.ip,
        vendor: n.vendor,
        osType: n.osType,
        riskScore: n.riskScore,
        riskLevel: n.riskLevel,
        alertCount: n.alertCount,
        isAnomaly: n.isAnomaly,
        isBigConsumer: n.isBigConsumer,
        bandwidthShare: n.bandwidthShare,
        isCriticalPath: criticalSet.has(n.id),
      })),
      links: graphData.edges.map((e, i) => ({
        source: e.source,
        target: e.target,
        label: e.label,
        riskLevel: e.riskLevel,
        id: `edge-${i}`,
      })),
    };
  }, [graphData]);

  // ── Canvas render callbacks ──────────────────────────────────

  const paintNode = useCallback((node, ctx, globalScale) => {
    const riskColors = RISK_COLORS[node.riskLevel] || RISK_COLORS.SAFE;
    const size = 5 + (node.riskScore / 100) * 12;
    const isHovered = hoveredNode === node.id;

    // Glow for critical / hovered nodes
    if (node.riskLevel === 'CRITICAL' || isHovered) {
      ctx.beginPath();
      ctx.arc(node.x, node.y, size + 5, 0, 2 * Math.PI);
      ctx.fillStyle = riskColors.glow;
      ctx.fill();
    }

    // Critical path ring
    if (node.isCriticalPath) {
      ctx.beginPath();
      ctx.arc(node.x, node.y, size + 3, 0, 2 * Math.PI);
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 1.5;
      ctx.setLineDash([3, 3]);
      ctx.stroke();
      ctx.setLineDash([]);
    }

    // Main circle
    ctx.beginPath();
    ctx.arc(node.x, node.y, size, 0, 2 * Math.PI);
    ctx.fillStyle = riskColors.fill;
    ctx.fill();

    // Inner highlight
    ctx.beginPath();
    ctx.arc(node.x - size * 0.25, node.y - size * 0.25, size * 0.35, 0, 2 * Math.PI);
    ctx.fillStyle = 'rgba(255,255,255,0.25)';
    ctx.fill();

    // Label
    if (globalScale > 0.6 || isHovered) {
      const label = node.label || node.ip;
      const fontSize = Math.max(10, 12 / globalScale);
      ctx.font = `600 ${fontSize}px Inter, system-ui, sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'top';

      // Background for label
      const textWidth = ctx.measureText(label).width;
      ctx.fillStyle = 'rgba(15,23,42,0.85)';
      ctx.fillRect(node.x - textWidth / 2 - 3, node.y + size + 2, textWidth + 6, fontSize + 4);

      ctx.fillStyle = '#e2e8f0';
      ctx.fillText(label, node.x, node.y + size + 4);
    }
  }, [hoveredNode]);

  const paintLink = useCallback((link, ctx) => {
    const style = EDGE_STYLES[link.label] || { color: '#475569', dash: null, width: 1 };

    ctx.beginPath();
    ctx.moveTo(link.source.x, link.source.y);
    ctx.lineTo(link.target.x, link.target.y);
    ctx.strokeStyle = style.color;
    ctx.lineWidth = style.width;
    if (style.dash) ctx.setLineDash(style.dash);
    else ctx.setLineDash([]);
    ctx.stroke();
    ctx.setLineDash([]);

    // Arrow head
    const dx = link.target.x - link.source.x;
    const dy = link.target.y - link.source.y;
    const angle = Math.atan2(dy, dx);
    const arrowLen = 8;
    const targetSize = 5 + ((link.target.riskScore || 0) / 100) * 12;
    const endX = link.target.x - Math.cos(angle) * (targetSize + 2);
    const endY = link.target.y - Math.sin(angle) * (targetSize + 2);

    ctx.beginPath();
    ctx.moveTo(endX, endY);
    ctx.lineTo(endX - arrowLen * Math.cos(angle - Math.PI / 6), endY - arrowLen * Math.sin(angle - Math.PI / 6));
    ctx.lineTo(endX - arrowLen * Math.cos(angle + Math.PI / 6), endY - arrowLen * Math.sin(angle + Math.PI / 6));
    ctx.fillStyle = style.color;
    ctx.fill();
  }, []);

  // ── Render ───────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="loading-screen">
        <div style={{ width: 56, height: 56, borderRadius: '50%', border: '3px solid rgba(239,68,68,0.2)', borderTopColor: '#ef4444', animation: 'spin 1s linear infinite' }} />
        <p style={{ color: 'var(--text-secondary)' }}>Analyzing attack paths…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-container">
        <div className="glass-panel" style={{ textAlign: 'center', padding: '40px', background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)' }}>
          <ShieldX size={48} style={{ color: '#ef4444', marginBottom: '16px' }} />
          <p style={{ color: '#ef4444', marginBottom: '12px' }}>{error}</p>
          <button onClick={fetchGraph} className="btn-primary">Retry</button>
        </div>
      </div>
    );
  }

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <Crosshair size={30} color="#ef4444" />
            Attack Path Graph
          </h1>
          <p className="page-subtitle">
            Directed risk graph · Lateral movement analysis · Real-time anomaly correlation
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {lastRefresh && (
            <span style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
              Updated {lastRefresh.toLocaleTimeString()}
            </span>
          )}
          <button onClick={fetchGraph} className="btn-primary" style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px' }}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      {/* Stats */}
      <StatsBar stats={graphData?.stats} />

      {/* Legend */}
      <Legend />

      {/* Graph canvas */}
      <div
        ref={containerRef}
        className="glass-panel"
        style={{ padding: '0', overflow: 'hidden', position: 'relative', marginBottom: '20px' }}
      >
        {forceGraphData.nodes.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '80px 24px' }}>
            <Shield size={56} style={{ color: '#22c55e', marginBottom: '16px', opacity: 0.5 }} />
            <p style={{ fontSize: '16px', fontWeight: 600, marginBottom: '8px' }}>Network is Clean</p>
            <p style={{ color: 'var(--text-secondary)', fontSize: '13px' }}>No online devices or risk paths detected.</p>
          </div>
        ) : (
          <ForceGraph2D
            ref={graphRef}
            graphData={forceGraphData}
            width={dimensions.width}
            height={dimensions.height}
            backgroundColor="transparent"
            nodeCanvasObject={paintNode}
            linkCanvasObject={paintLink}
            nodePointerAreaPaint={(node, color, ctx) => {
              const size = 5 + (node.riskScore / 100) * 12;
              ctx.beginPath();
              ctx.arc(node.x, node.y, size + 5, 0, 2 * Math.PI);
              ctx.fillStyle = color;
              ctx.fill();
            }}
            onNodeHover={node => setHoveredNode(node ? node.id : null)}
            linkDirectionalArrowLength={0}
            d3AlphaDecay={0.03}
            d3VelocityDecay={0.3}
            cooldownTicks={100}
            warmupTicks={50}
            enableZoomInteraction={true}
            enablePanInteraction={true}
          />
        )}

        {/* Hovered node tooltip */}
        {hoveredNode && forceGraphData.nodes.length > 0 && (() => {
          const node = forceGraphData.nodes.find(n => n.id === hoveredNode);
          if (!node) return null;
          const riskColors = RISK_COLORS[node.riskLevel] || RISK_COLORS.SAFE;
          return (
            <div style={{
              position: 'absolute', top: '16px', right: '16px',
              background: 'rgba(15,23,42,0.95)', backdropFilter: 'blur(12px)',
              border: `1px solid ${riskColors.fill}40`,
              borderRadius: '12px', padding: '16px 20px', minWidth: '220px',
              zIndex: 10, boxShadow: `0 8px 32px rgba(0,0,0,0.4), 0 0 0 1px ${riskColors.fill}20`,
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px' }}>
                <div style={{ width: 10, height: 10, borderRadius: '50%', background: riskColors.fill, boxShadow: `0 0 8px ${riskColors.glow}` }} />
                <span style={{ fontWeight: 700, fontSize: '14px' }}>{node.label}</span>
              </div>
              <div style={{ display: 'grid', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>IP</span><span style={{ fontFamily: 'monospace', color: 'var(--text-primary)' }}>{node.ip}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Vendor</span><span style={{ color: 'var(--text-primary)' }}>{node.vendor || 'Unknown'}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Risk Score</span>
                  <span style={{ color: riskColors.fill, fontWeight: 700 }}>{node.riskScore}/100</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Risk Level</span>
                  <span style={{
                    background: riskColors.bg, color: riskColors.fill,
                    padding: '1px 8px', borderRadius: '4px', fontWeight: 700, fontSize: '10px',
                  }}>{node.riskLevel.replace('_', ' ')}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Active Alerts</span><span style={{ color: 'var(--text-primary)' }}>{node.alertCount}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Bandwidth</span><span style={{ color: 'var(--text-primary)' }}>{node.bandwidthShare}%</span>
                </div>
                {node.isAnomaly && (
                  <div style={{ color: '#f97316', fontWeight: 600, marginTop: '4px' }}>⚡ Anomalous behavior detected</div>
                )}
                {node.isBigConsumer && (
                  <div style={{ color: '#eab308', fontWeight: 600 }}>📊 High bandwidth consumer</div>
                )}
              </div>
            </div>
          );
        })()}
      </div>

      {/* Summary box */}
      {graphData?.summary && (
        <div className="glass-panel" style={{
          padding: '20px 24px',
          borderLeft: graphData.stats?.criticalDevices > 0
            ? '3px solid #ef4444'
            : graphData.stats?.highRiskDevices > 0
              ? '3px solid #f97316'
              : '3px solid #22c55e',
          marginBottom: '20px',
        }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
            <AlertTriangle
              size={20}
              style={{
                color: graphData.stats?.criticalDevices > 0 ? '#ef4444'
                  : graphData.stats?.highRiskDevices > 0 ? '#f97316' : '#22c55e',
                flexShrink: 0, marginTop: '2px',
              }}
            />
            <div>
              <div style={{ fontWeight: 700, fontSize: '14px', marginBottom: '4px' }}>
                Attack Path Summary
              </div>
              <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                {graphData.summary}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Critical path breakdown */}
      {graphData?.criticalPath?.length > 1 && (
        <div className="glass-panel" style={{ padding: '20px 24px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Target size={16} color="#ef4444" /> Critical Path Breakdown
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            {graphData.criticalPath.map((nodeId, idx) => {
              const node = graphData.nodes.find(n => n.id === nodeId);
              if (!node) return null;
              const riskColors = RISK_COLORS[node.riskLevel] || RISK_COLORS.SAFE;
              return (
                <React.Fragment key={nodeId}>
                  <div style={{
                    display: 'flex', alignItems: 'center', gap: '8px',
                    background: riskColors.bg,
                    border: `1px solid ${riskColors.fill}40`,
                    padding: '8px 14px', borderRadius: '8px',
                  }}>
                    <div style={{ width: 8, height: 8, borderRadius: '50%', background: riskColors.fill }} />
                    <div>
                      <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--text-primary)' }}>{node.label}</div>
                      <div style={{ fontSize: '10px', fontFamily: 'monospace', color: 'var(--text-secondary)' }}>{node.ip}</div>
                    </div>
                    <span style={{
                      fontSize: '10px', fontWeight: 800,
                      background: riskColors.fill, color: 'white',
                      padding: '2px 6px', borderRadius: '4px',
                    }}>{node.riskScore}</span>
                  </div>
                  {idx < graphData.criticalPath.length - 1 && (
                    <span style={{ color: '#ef4444', fontWeight: 800, fontSize: '16px' }}>→</span>
                  )}
                </React.Fragment>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default AttackPathPage;
