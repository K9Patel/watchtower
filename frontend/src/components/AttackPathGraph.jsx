import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import ForceGraph2D from 'react-force-graph-2d';
import { API_BASE_URL } from '../config/api';

const RISK_COLORS = {
  SAFE: '#22c55e',
  SUSPICIOUS: '#facc15',
  HIGH_RISK: '#fb923c',
  CRITICAL: '#ef4444',
};

const edgeStyleFor = (label) => {
  if ((label || '').toLowerCase().includes('unknown device')) {
    return { color: '#ef4444', width: 2.8, dashed: false };
  }
  if (label === 'possible lateral movement') {
    return { color: '#fb923c', width: 2.2, dashed: false };
  }
  return { color: '#94a3b8', width: 1.6, dashed: true };
};

const AttackPathGraph = () => {
  const [graph, setGraph] = useState({ nodes: [], edges: [], summary: 'Loading attack path analysis...' });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const graphRef = useRef(null);
  const wrapRef = useRef(null);
  const [graphWidth, setGraphWidth] = useState(960);

  const fetchGraph = async () => {
    try {
      const { data } = await axios.get(`${API_BASE_URL}/graph/attack-path`);
      setGraph(data || { nodes: [], edges: [], summary: 'No graph data available.' });
      setError(null);
    } catch (e) {
      setError('Unable to load attack path graph.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchGraph();
    const id = setInterval(fetchGraph, 30000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (!graphRef.current || !graph.nodes?.length) return;

    const timer = setTimeout(() => {
      graphRef.current.zoomToFit(600, 70);
    }, 250);

    return () => clearTimeout(timer);
  }, [graph.nodes?.length, graph.edges?.length]);

  useEffect(() => {
    const updateWidth = () => {
      if (!wrapRef.current) return;
      setGraphWidth(Math.max(320, Math.floor(wrapRef.current.clientWidth)));
    };

    updateWidth();
    window.addEventListener('resize', updateWidth);
    return () => window.removeEventListener('resize', updateWidth);
  }, []);

  const forceData = useMemo(() => {
    const nodes = (graph.nodes || []).map((n) => ({
      ...n,
      size: 6 + (Number(n.riskScore || 0) / 100) * 18,
      color: RISK_COLORS[n.riskLevel] || '#64748b',
    }));

    const links = (graph.edges || []).map((e, idx) => {
      const style = edgeStyleFor(e.label);
      return {
        id: `edge-${idx}`,
        source: e.source,
        target: e.target,
        label: e.label,
        riskLevel: e.riskLevel,
        ...style,
      };
    });

    return { nodes, links };
  }, [graph.edges, graph.nodes]);

  return (
    <div className="glass-panel attack-path-panel">
      <div className="attack-path-header">
        <div>
          <h3>Attack Path Graph</h3>
          <p>Directed risk-flow analysis from alerts, anomalies, and correlated device activity.</p>
        </div>
        <span className="attack-path-badge">AUTO REFRESH 30s</span>
      </div>

      <div className="attack-path-legend">
        <span><i style={{ background: RISK_COLORS.SAFE }} />Safe</span>
        <span><i style={{ background: RISK_COLORS.SUSPICIOUS }} />Suspicious</span>
        <span><i style={{ background: RISK_COLORS.HIGH_RISK }} />High Risk</span>
        <span><i style={{ background: RISK_COLORS.CRITICAL }} />Critical</span>
      </div>

      <div className="attack-path-canvas-wrap" ref={wrapRef}>
        {loading ? (
          <div className="attack-path-placeholder">Building risk graph...</div>
        ) : error ? (
          <div className="attack-path-placeholder">{error}</div>
        ) : (
          <ForceGraph2D
            ref={graphRef}
            graphData={forceData}
            backgroundColor="rgba(2, 6, 23, 0.92)"
            width={graphWidth}
            height={420}
            cooldownTicks={100}
            linkDirectionalArrowLength={8}
            linkDirectionalArrowRelPos={1}
            linkColor={(link) => link.color}
            linkWidth={(link) => link.width}
            linkLineDash={(link) => (link.dashed ? [5, 4] : null)}
            nodeRelSize={1}
            nodeCanvasObject={(node, ctx, globalScale) => {
              const label = node.label || node.ip || String(node.id);
              const fontSize = Math.max(9, 12 / globalScale);
              const pulse = node.riskLevel === 'CRITICAL'
                ? 1 + Math.abs(Math.sin(Date.now() / 280)) * 0.35
                : 1;
              const radius = (node.size || 8) * pulse;

              // Outer glow for critical nodes
              if (node.riskLevel === 'CRITICAL') {
                ctx.beginPath();
                ctx.arc(node.x, node.y, radius + 8, 0, 2 * Math.PI, false);
                ctx.fillStyle = 'rgba(239, 68, 68, 0.16)';
                ctx.fill();
              }

              ctx.beginPath();
              ctx.arc(node.x, node.y, radius, 0, 2 * Math.PI, false);
              ctx.fillStyle = node.color;
              ctx.fill();

              ctx.lineWidth = 1.2;
              ctx.strokeStyle = 'rgba(15, 23, 42, 0.95)';
              ctx.stroke();

              ctx.font = `${fontSize}px sans-serif`;
              ctx.textAlign = 'center';
              ctx.textBaseline = 'top';
              ctx.fillStyle = '#e2e8f0';
              ctx.fillText(label, node.x, node.y + radius + 4);
            }}
            nodeLabel={(node) => {
              const score = Number(node.riskScore || 0);
              const risk = node.riskLevel || 'SAFE';
              const ip = node.ip || 'N/A';
              const vendor = node.vendor || 'Unknown';
              const os = node.osType || 'Unknown';
              return `${node.label}\nIP: ${ip}\nRisk: ${risk} (${score})\nVendor: ${vendor}\nOS: ${os}`;
            }}
          />
        )}
      </div>

      <div className="attack-path-summary">
        <strong>Likely attack path:</strong>
        <p>{graph.summary || 'No suspicious path identified in the current window.'}</p>
      </div>
    </div>
  );
};

export default AttackPathGraph;
