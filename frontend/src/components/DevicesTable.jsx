import React, { useState } from 'react';
import { Server, Brain } from 'lucide-react';
import TrafficBadge from './TrafficBadge';
import useDpiSocket from '../hooks/useDpiSocket';

// Removed vendor emoji and color mapping per user request

const DevicesTable = ({ devices, clusters }) => {
  const [filter, setFilter] = useState('ALL');
  const { dpiByDevice } = useDpiSocket();

  const getClusterStyles = (cluster) => {
    switch (cluster) {
      case 'HIGH_USAGE': return { bg: 'rgba(175, 139, 78, 0.18)', color: '#d8b277' };
      case 'MED_USAGE':  return { bg: 'rgba(139, 117, 81, 0.18)', color: '#c7ab80' };
      case 'LOW_USAGE':  return { bg: 'rgba(90, 86, 78, 0.22)', color: '#b8b2a7' };
      default: return { bg: 'rgba(70, 70, 70, 0.3)', color: '#9ca3af' };
    }
  };

  const normalizeStatus = (status) => String(status || 'OFFLINE').toUpperCase();
  const getStatusColor = (status) => normalizeStatus(status) === 'ONLINE' ? '#d8b277' : '#7d8188';

  const resolveTraffic = (device) => {
    const live = dpiByDevice?.[device.id];
    return {
      service: live?.serviceName || device.currentService || 'UNKNOWN',
      category: live?.trafficCategory || device.currentCategory || 'UNKNOWN',
      confidence: live?.confidence,
    };
  };

  // Filter options: ALL, ONLINE, OFFLINE, AUTO
  const activeDevices = devices.filter((d) => d?.isActive !== false);
  const displayDevices = filter === 'ALL'     ? activeDevices
    : filter === 'ONLINE'  ? activeDevices.filter((d) => normalizeStatus(d.status) === 'ONLINE')
    : filter === 'OFFLINE' ? activeDevices.filter((d) => normalizeStatus(d.status) !== 'ONLINE')
    : filter === 'AUTO'    ? activeDevices.filter(d => d.isAutoDiscovered)
    : activeDevices;

  return (
    <div className="glass-panel" style={{ display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h3 style={{ fontSize: '18px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Server size={20} color="#d8b277" />
          Discovered Devices ({activeDevices.length})
        </h3>
        <div style={{ display: 'flex', gap: '8px' }}>
          {['ALL', 'ONLINE', 'OFFLINE', 'AUTO'].map(f => (
            <button key={f}
              onClick={() => setFilter(f)}
              style={{
                background: filter === f ? 'rgba(165, 132, 77, 0.3)' : 'rgba(36, 39, 44, 0.72)',
                color: filter === f ? '#f3e5c6' : '#9ca3af',
                border: `1px solid ${filter === f ? 'rgba(216, 178, 119, 0.45)' : 'rgba(115, 120, 130, 0.28)'}`,
                padding: '6px 12px', borderRadius: '6px',
                fontSize: '11px', fontWeight: 600, cursor: 'pointer',
              }}>
              {f}
            </button>
          ))}
        </div>
      </div>

      <div className="devices-table-wrapper" style={{ overflowY: 'auto' }}>
        <table className="devices-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--panel-border)', color: 'var(--text-secondary)' }}>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>Device</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>Vendor</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>IP Address</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>MAC Address</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>Traffic</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>K-Means Cluster</th>
              <th style={{ padding: '12px 8px', textAlign: 'center', whiteSpace: 'nowrap' }}>Baseline</th>
              <th style={{ padding: '12px 8px', textAlign: 'center', whiteSpace: 'nowrap' }}>Status</th>
            </tr>
          </thead>
          <tbody>
            {displayDevices.map(d => {
              const cluster = clusters?.[d.id] || 'N/A';
              const cStyle  = getClusterStyles(cluster);
              const status = normalizeStatus(d.status);
              const traffic = resolveTraffic(d);
              return (
                <tr key={d.id} style={{ borderBottom: '1px solid rgba(51, 65, 85, 0.3)', position: 'relative' }}>
                  <td style={{ padding: '12px 8px', fontWeight: 600 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      {d.isAutoDiscovered && (
                        <span style={{
                          fontSize: '9px', fontWeight: 700,
                          background: 'rgba(116, 106, 88, 0.26)',
                          color: '#d0b78c', padding: '2px 6px', borderRadius: '10px',
                          border: '1px solid rgba(208, 183, 140, 0.24)',
                          letterSpacing: '0.06em', flexShrink: 0,
                        }}>AUTO</span>
                      )}
                      {d.deviceName || 'Unnamed device'}
                    </div>
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <div style={{ fontSize: '13px', color: 'var(--text-secondary)', maxWidth: '180px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={d.vendorName}>
                      {d.vendorName || 'Unknown'}
                    </div>
                  </td>
                  <td style={{ padding: '12px 8px', fontFamily: 'monospace', fontSize: '12px' }}>
                    {d.ipAddress}
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <span
                      style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--text-secondary)', cursor: 'default' }}
                      title={`Full MAC: ${d.macAddress || 'N/A'}`}
                    >
                      {d.macAddress || '—'}
                    </span>
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <TrafficBadge
                      service={traffic.service}
                      category={traffic.category}
                      confidence={traffic.confidence}
                    />
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <span style={{
                      background: cStyle.bg, color: cStyle.color,
                      padding: '4px 8px', borderRadius: '4px',
                      fontSize: '11px', fontWeight: 600,
                    }}>
                      {String(cluster).replaceAll('_', ' ')}
                    </span>
                  </td>
                  <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                    {d.baselineReady ? (
                      <span style={{
                        fontSize: '10px', fontWeight: 700,
                        background: 'rgba(112, 98, 75, 0.22)', color: '#d6b885',
                        padding: '3px 8px', borderRadius: '10px',
                        border: '1px solid rgba(214, 184, 133, 0.28)',
                      }}>✓ Ready</span>
                    ) : (
                      <span style={{
                        fontSize: '10px', fontWeight: 700,
                        background: 'rgba(67, 70, 75, 0.34)', color: '#a7adb6',
                        border: '1px solid rgba(145, 152, 164, 0.26)',
                        padding: '3px 8px', borderRadius: '10px',
                        display: 'inline-flex', alignItems: 'center', gap: '3px',
                        animation: 'learningPulse 2.5s ease-in-out infinite',
                      }}>
                        <Brain size={10} />
                        Learning…
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '11px', fontWeight: 600, color: 'var(--text-secondary)' }}>
                      <span style={{
                        display: 'inline-block', width: '10px', height: '10px', borderRadius: '50%',
                        background: getStatusColor(status),
                        boxShadow: `0 0 8px ${getStatusColor(status)}`,
                      }} />
                      {status}
                    </span>
                  </td>
                </tr>
              );
            })}

            {displayDevices.length === 0 && (
              <tr>
                <td colSpan="8" style={{ textAlign: 'center', padding: '32px', color: 'var(--text-secondary)' }}>
                  No devices found for this filter.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default DevicesTable;
