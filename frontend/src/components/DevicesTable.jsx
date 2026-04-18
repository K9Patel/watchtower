import React, { useState } from 'react';
import axios from 'axios';
import { ShieldAlert, Server, Trash2, Power } from 'lucide-react';
import { API_BASE_URL } from '../config/api';

const DevicesTable = ({ devices, clusters, onRefresh }) => {
  const [filter, setFilter] = useState('ALL');

  const getClusterStyles = (cluster) => {
    switch (cluster) {
      case 'HIGH_USAGE': return { bg: 'rgba(239, 68, 68, 0.2)', color: '#f87171' };
      case 'MED_USAGE':  return { bg: 'rgba(234, 179, 8, 0.2)', color: '#facc15' };
      case 'LOW_USAGE':  return { bg: 'rgba(34, 197, 94, 0.2)', color: '#4ade80' };
      default: return { bg: 'rgba(51, 65, 85, 0.3)', color: '#94a3b8' };
    }
  };

  const getStatusColor = (status) => status === 'ONLINE' ? '#4ade80' : '#f87171';

  const toggleStatus = async (id, currentStatus) => {
    const nextStatus = currentStatus === 'ONLINE' ? 'OFFLINE' : 'ONLINE';
    try {
      await axios.put(`${API_BASE_URL}/devices/${id}/status?status=${nextStatus}`);
      onRefresh();
    } catch (e) {
      console.error(e);
    }
  };

  const deleteDevice = async (id) => {
    try {
      await axios.delete(`${API_BASE_URL}/devices/${id}`);
      onRefresh();
    } catch (e) {
      console.error(e);
    }
  };

  // Filter out deactivated devices
  const activeDevices = devices.filter(d => d.isActive);
  const displayDevices = filter === 'ALL' ? activeDevices : activeDevices.filter(d => d.deviceType === filter);

  return (
    <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h3 style={{ fontSize: '18px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Server size={20} color="var(--accent-color)" /> Fleet Management ({activeDevices.length})
        </h3>
        <div style={{ display: 'flex', gap: '8px' }}>
          {['ALL', 'STUDENT', 'STAFF', 'ADMIN'].map(f => (
            <button key={f}
              onClick={() => setFilter(f)}
              style={{
                background: filter === f ? 'var(--accent-color)' : 'rgba(51, 65, 85, 0.4)',
                color: filter === f ? 'white' : 'var(--text-secondary)',
                border: 'none', padding: '6px 12px', borderRadius: '6px', fontSize: '11px', fontWeight: 600
              }}>
              {f}
            </button>
          ))}
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--panel-border)', color: 'var(--text-secondary)' }}>
              <th style={{ padding: '12px 8px' }}>Device</th>
              <th style={{ padding: '12px 8px' }}>Type</th>
              <th style={{ padding: '12px 8px' }}>IP / MAC</th>
              <th style={{ padding: '12px 8px' }}>K-Means Cluster</th>
              <th style={{ padding: '12px 8px', textAlign: 'center' }}>Status</th>
              <th style={{ padding: '12px 8px', textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {displayDevices.map(d => {
              const cluster = clusters[d.id] || 'N/A';
              const cStyle = getClusterStyles(cluster);
              
              return (
                <tr key={d.id} style={{ borderBottom: '1px solid rgba(51, 65, 85, 0.3)' }}>
                  <td style={{ padding: '12px 8px', fontWeight: 600 }}>{d.deviceName}</td>
                  <td style={{ padding: '12px 8px', color: 'var(--text-secondary)' }}>{d.deviceType}</td>
                  <td style={{ padding: '12px 8px', fontFamily: 'monospace', fontSize: '12px' }}>
                    {d.ipAddress} <br /> 
                    <span style={{ color: 'var(--text-secondary)' }}>{d.macAddress}</span>
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <span style={{ 
                      background: cStyle.bg, color: cStyle.color, padding: '4px 8px', 
                      borderRadius: '4px', fontSize: '11px', fontWeight: 600 
                    }}>
                      {cluster.replace('_', ' ')}
                    </span>
                  </td>
                  <td style={{ padding: '12px 8px', textAlign: 'center' }}>
                    <span style={{ 
                      display: 'inline-block', width: '10px', height: '10px', borderRadius: '50%',
                      background: getStatusColor(d.status), boxShadow: `0 0 8px ${getStatusColor(d.status)}`
                    }}></span>
                  </td>
                  <td style={{ padding: '12px 8px', textAlign: 'right' }}>
                    <button onClick={() => toggleStatus(d.id, d.status)} style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', marginRight: '12px' }}>
                      <Power size={16} />
                    </button>
                    <button onClick={() => deleteDevice(d.id)} style={{ background: 'transparent', border: 'none', color: '#ef4444' }}>
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              );
            })}
            
            {displayDevices.length === 0 && (
               <tr>
                 <td colSpan="6" style={{ textAlign: 'center', padding: '32px', color: 'var(--text-secondary)' }}>
                   No devices found matching this filter.
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
