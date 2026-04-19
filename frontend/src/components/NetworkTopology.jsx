import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import ForceGraph2D from 'react-force-graph-2d';
import { CircleMarker, MapContainer, Popup, TileLayer } from 'react-leaflet';
import { API_BASE_URL } from '../config/api';
import 'leaflet/dist/leaflet.css';

const riskColor = (node) => {
  if (node.type === 'GATEWAY') return '#94a3b8';
  if (node.status !== 'ONLINE') return '#475569';
  if (node.riskLevel === 'CRITICAL') return '#ef4444';
  if (node.riskLevel === 'HIGH_RISK') return '#f97316';
  if (node.riskLevel === 'SUSPICIOUS') return '#facc15';
  return '#22c55e';
};

const edgeColor = (edge) => (edge.status === 'ONLINE' ? '#22c55e' : '#64748b');

const NetworkTopology = () => {
  const navigate = useNavigate();
  const graphRef = useRef(null);
  const wrapRef = useRef(null);

  const [topology, setTopology] = useState(null);
  const [loading, setLoading] = useState(true);
  const [width, setWidth] = useState(960);

  const fetchTopology = async () => {
    try {
      const { data } = await axios.get(`${API_BASE_URL}/topology`);
      setTopology(data);
    } catch (_) {
      setTopology(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTopology();
    const id = setInterval(fetchTopology, 15000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    const updateWidth = () => {
      if (!wrapRef.current) return;
      setWidth(Math.max(320, Math.floor(wrapRef.current.clientWidth)));
    };
    updateWidth();
    window.addEventListener('resize', updateWidth);
    return () => window.removeEventListener('resize', updateWidth);
  }, []);

  const graphData = useMemo(() => {
    if (!topology) return { nodes: [], links: [] };

    const deviceNodes = (topology.nodes || []).filter((n) => n.type !== 'GATEWAY');
    const spreadRadius = 150;

    const nodes = (topology.nodes || []).map((n, idx) => {
      if (n.type === 'GATEWAY') {
        return {
          ...n,
          fx: 0,
          fy: 0,
          size: 34,
          color: riskColor(n),
        };
      }

      const ringIndex = Math.max(0, deviceNodes.findIndex((d) => d.id === n.id));
      const angle = (2 * Math.PI * ringIndex) / Math.max(1, deviceNodes.length);
      const currentMbps = Number(n.currentMbps || 0);
      return {
        ...n,
        x: Math.cos(angle) * spreadRadius,
        y: Math.sin(angle) * spreadRadius,
        size: Math.min(30, 8 + currentMbps * 2),
        color: riskColor(n),
      };
    });

    const links = (topology.edges || []).map((e, idx) => ({
      ...e,
      id: `edge-${idx}`,
      color: edgeColor(e),
      width: Math.min(8, 1 + Number(e.bandwidth || 0) / 5),
      dashed: e.status !== 'ONLINE',
      particles: Number(e.bandwidth || 0) > 5 ? 2 : 0,
    }));

    return { nodes, links };
  }, [topology]);

  useEffect(() => {
    if (!graphRef.current || !graphData.nodes.length) return;
    const timer = setTimeout(() => {
      graphRef.current.zoomToFit(600, 80);
    }, 300);
    return () => clearTimeout(timer);
  }, [graphData]);

  const gatewayCoords = useMemo(() => {
    if (!topology) return null;
    const lat = Number(topology.gatewayLat);
    const lng = Number(topology.gatewayLng);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
    return [lat, lng];
  }, [topology]);

  return (
    <div className="glass-panel topology-panel">
      <div className="topology-header">
        <div>
          <h3>Network Topology View</h3>
          <p>Logical network structure centered on your router, with live traffic and health coloring.</p>
        </div>
        <div className="topology-stats">
          <span>{topology?.totalDevices ?? 0} devices</span>
          <span>{topology?.onlineDevices ?? 0} online</span>
          <span>{Number(topology?.totalBandwidthMbps || 0).toFixed(1)} Mbps total</span>
        </div>
      </div>

      <div className="topology-canvas-wrap" ref={wrapRef}>
        {loading ? (
          <div className="attack-path-placeholder">Building network topology...</div>
        ) : (
          <ForceGraph2D
            ref={graphRef}
            graphData={graphData}
            width={width}
            height={470}
            backgroundColor="rgba(2, 6, 23, 0.92)"
            cooldownTicks={120}
            nodeRelSize={1}
            linkColor={(l) => l.color}
            linkWidth={(l) => l.width}
            linkLineDash={(l) => (l.dashed ? [7, 5] : null)}
            linkDirectionalParticles={(l) => l.particles}
            linkDirectionalParticleColor={(l) => l.color}
            linkDirectionalParticleWidth={2}
            onNodeClick={(node) => {
              if (node.type === 'GATEWAY') return;
              navigate(`/devices/${node.id}`);
            }}
            nodeCanvasObject={(node, ctx, globalScale) => {
              const fontSize = Math.max(9, 11 / globalScale);

              if (node.type === 'GATEWAY') {
                const r = node.size || 30;
                ctx.beginPath();
                for (let i = 0; i < 6; i += 1) {
                  const a = (Math.PI / 3) * i;
                  const x = node.x + r * Math.cos(a);
                  const y = node.y + r * Math.sin(a);
                  if (i === 0) ctx.moveTo(x, y);
                  else ctx.lineTo(x, y);
                }
                ctx.closePath();
                ctx.fillStyle = '#64748b';
                ctx.fill();
                ctx.lineWidth = 1.5;
                ctx.strokeStyle = '#cbd5e1';
                ctx.stroke();
              } else {
                const pulse = node.riskLevel === 'CRITICAL'
                  ? 1 + Math.abs(Math.sin(Date.now() / 260)) * 0.3
                  : 1;
                const radius = (node.size || 10) * pulse;
                ctx.beginPath();
                ctx.arc(node.x, node.y, radius, 0, 2 * Math.PI, false);
                ctx.fillStyle = node.color;
                ctx.fill();
                ctx.lineWidth = 1.2;
                ctx.strokeStyle = 'rgba(15, 23, 42, 0.95)';
                ctx.stroke();
              }

              const label = node.label || node.ip || String(node.id);
              ctx.font = `${fontSize}px sans-serif`;
              ctx.textAlign = 'center';
              ctx.textBaseline = 'top';
              ctx.fillStyle = '#e2e8f0';
              ctx.fillText(label, node.x, node.y + (node.size || 10) + 4);

              if (node.type !== 'GATEWAY') {
                const vendor = node.vendor || 'Unknown vendor';
                const mbps = `${Number(node.currentMbps || 0).toFixed(1)} Mbps`;
                ctx.font = `${Math.max(8, 9 / globalScale)}px sans-serif`;
                ctx.fillStyle = '#94a3b8';
                ctx.fillText(vendor, node.x, node.y + (node.size || 10) + 18);
                ctx.fillText(mbps, node.x, node.y + (node.size || 10) + 30);
              }
            }}
            nodeLabel={(node) => {
              if (node.type === 'GATEWAY') {
                return `Router/Gateway\nIP: ${node.ip || 'unknown'}`;
              }
              return `${node.label}\nIP: ${node.ip}\nRisk: ${node.riskLevel}\nTraffic: ${Number(node.currentMbps || 0).toFixed(2)} Mbps\nAlerts: ${node.alertCount || 0}`;
            }}
          />
        )}
      </div>

      <div className="router-map-panel">
        <div className="router-map-head">
          <strong>Gateway Map</strong>
          <span>{topology?.gatewayIp || 'unknown'} {topology?.gatewayLocation ? `• ${topology.gatewayLocation}` : ''}</span>
        </div>
        <div className="router-map-canvas-wrap">
          {gatewayCoords ? (
            <MapContainer center={gatewayCoords} zoom={15} scrollWheelZoom className="router-map-canvas">
              <TileLayer
                attribution="&copy; OpenStreetMap contributors"
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <CircleMarker center={gatewayCoords} radius={10} pathOptions={{ color: '#0ea5e9', fillColor: '#38bdf8', fillOpacity: 0.9 }}>
                <Popup>
                  <strong>Router / Gateway</strong>
                  <br />
                  {topology?.gatewayIp || 'unknown'}
                  <br />
                  {topology?.gatewayLocation || 'No location text'}
                </Popup>
              </CircleMarker>
            </MapContainer>
          ) : (
            <div className="attack-path-placeholder">Gateway location unavailable yet. Trigger re-locate on router device.</div>
          )}
        </div>
      </div>
    </div>
  );
};

export default NetworkTopology;
