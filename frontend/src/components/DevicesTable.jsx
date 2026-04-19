import React, { useState } from 'react';
import { Server, Brain } from 'lucide-react';

// Removed vendor emoji and color mapping per user request

const DevicesTable = ({ devices, clusters }) => {
  const [filter, setFilter] = useState('ALL');

  const getClusterStyles = (cluster) => {
    switch (cluster) {
      case 'HIGH_USAGE': return { bg: 'rgba(239, 68, 68, 0.2)', color: '#f87171' };
      case 'MED_USAGE':  return { bg: 'rgba(234, 179, 8, 0.2)', color: '#facc15' };
      case 'LOW_USAGE':  return { bg: 'rgba(34, 197, 94, 0.2)', color: '#4ade80' };
      default: return { bg: 'rgba(51, 65, 85, 0.3)', color: '#94a3b8' };
    }
  };

  const normalizeStatus = (status) => String(status || 'OFFLINE').toUpperCase();
  const getStatusColor = (status) => normalizeStatus(status) === 'ONLINE' ? '#4ade80' : '#f87171';

  // Filter options: ALL, ONLINE, OFFLINE, AUTO
  const activeDevices = devices.filter((d) => d?.isActive !== false);
  const displayDevices = filter === 'ALL'     ? activeDevices
    : filter === 'ONLINE'  ? activeDevices.filter((d) => normalizeStatus(d.status) === 'ONLINE')
    : filter === 'OFFLINE' ? activeDevices.filter((d) => normalizeStatus(d.status) !== 'ONLINE')
    : filter === 'AUTO'    ? activeDevices.filter(d => d.isAutoDiscovered)
    : activeDevices;

  return (
    <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h3 style={{ fontSize: '18px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Server size={20} color="var(--accent-color)" />
          Discovered Devices ({activeDevices.length})
        </h3>
        <div style={{ display: 'flex', gap: '8px' }}>
          {['ALL', 'ONLINE', 'OFFLINE', 'AUTO'].map(f => (
            <button key={f}
              onClick={() => setFilter(f)}
              style={{
                background: filter === f ? 'var(--accent-color)' : 'rgba(51, 65, 85, 0.4)',
                color: filter === f ? 'white' : 'var(--text-secondary)',
                border: 'none', padding: '6px 12px', borderRadius: '6px',
                fontSize: '11px', fontWeight: 600, cursor: 'pointer',
              }}>
              {f}
            </button>
          ))}
        </div>
      </div>

      <div className="devices-table-wrapper" style={{ flex: 1, overflowY: 'auto' }}>
        <table className="devices-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--panel-border)', color: 'var(--text-secondary)' }}>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>Device</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>Vendor</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>IP Address</th>
              <th style={{ padding: '12px 8px', whiteSpace: 'nowrap' }}>MAC Address</th>
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
              return (
                <tr key={d.id} style={{ borderBottom: '1px solid rgba(51, 65, 85, 0.3)', position: 'relative' }}>
                  <td style={{ padding: '12px 8px', fontWeight: 600 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      {d.isAutoDiscovered && (
                        <span style={{
                          fontSize: '9px', fontWeight: 700,
                          background: 'rgba(99,102,241,0.15)',
                          color: '#818cf8', padding: '2px 6px', borderRadius: '10px',
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
                        background: 'rgba(34,197,94,0.15)', color: '#4ade80',
                        padding: '3px 8px', borderRadius: '10px',
                      }}>✓ Ready</span>
                    ) : (
                      <span style={{
                        fontSize: '10px', fontWeight: 700,
                        background: 'rgba(168,85,247,0.15)', color: '#c084fc',
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
                <td colSpan="7" style={{ textAlign: 'center', padding: '32px', color: 'var(--text-secondary)' }}>
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
