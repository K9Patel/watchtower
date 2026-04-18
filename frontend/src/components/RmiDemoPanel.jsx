import React, { useState } from 'react';
import axios from 'axios';
import { Terminal } from 'lucide-react';
import { API_BASE_URL } from '../config/api';

const RmiDemoPanel = () => {
  const [rmiResult, setRmiResult] = useState('Standby for remote command execution...');
  const [loading, setLoading] = useState(false);

  const invokeRmi = async (endpoint, name) => {
    setLoading(true);
    setRmiResult('Invoking RMI: ' + name + '...');
    try {
      const res = await axios.get(`${API_BASE_URL}/rmi/${endpoint}`);
      setRmiResult(`[RMI Response for ${name}]\n\n${JSON.stringify(res.data, null, 2)}`);
    } catch (e) {
      setRmiResult(`[RMI Error]\n\n${e.response?.data?.error || e.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="glass-panel" style={{ marginTop: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h3 style={{ fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px', color: '#38bdf8' }}>
          <Terminal size={18} /> Distributed Node Controller
        </h3>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '24px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '8px', lineHeight: 1.5 }}>
            Establishing remote socket connections to distributed WatchTower nodes for real-time aggregated analysis.
          </p>
          <button onClick={() => invokeRmi('load', 'Query Global Network Load')} className="rmi-btn">Query Global Network Load</button>
          <button onClick={() => invokeRmi('alerts', 'Fetch Unresolved Global Alerts')} className="rmi-btn">Fetch Unresolved Global Alerts</button>
          <button onClick={() => invokeRmi('report', 'Retrieve Node Diagnosis Ledger')} className="rmi-btn">Retrieve Node Diagnosis Ledger</button>
        </div>

        <div style={{ 
          background: '#020617', border: '1px solid #1e293b', borderRadius: '8px', 
          padding: '16px', fontFamily: 'monospace', fontSize: '13px', color: '#a5b4fc', 
          overflowY: 'auto', maxHeight: '250px', whiteSpace: 'pre-wrap', position: 'relative'
        }}>
          {loading && <div style={{ position: 'absolute', top: '16px', right: '16px', width: '10px', height: '10px', background: '#38bdf8', borderRadius: '50%', animation: 'pulse 1s infinite' }}></div>}
          {rmiResult}
        </div>
      </div>

      <style>{`
        .rmi-btn {
          background: rgba(56, 189, 248, 0.1);
          color: #38bdf8;
          border: 1px solid rgba(56, 189, 248, 0.3);
          padding: 10px 16px;
          border-radius: 8px;
          text-align: left;
          font-family: inherit;
          font-weight: 600;
          font-size: 13px;
          transition: all 0.2s;
        }
        .rmi-btn:hover { background: rgba(56, 189, 248, 0.2); transform: translateX(5px); }
      `}</style>
    </div>
  );
};

export default RmiDemoPanel;
