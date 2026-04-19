import React from 'react';
import AttackPathGraph from '../components/AttackPathGraph';
import NetworkTopology from '../components/NetworkTopology';
import './Pages.css';

const AttackGraphPage = () => {
  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 className="btn-amber-text" data-text="Attack Graph" style={{ margin: 0, textTransform: 'none' }}>
            <span className="actual-text">Attack Graph</span>
            <span aria-hidden="true" className="hover-text">Attack Graph</span>
          </h1>
          <p className="page-subtitle">Lateral movement and risk-flow visualization across active devices.</p>
        </div>
      </div>

      <AttackPathGraph />
      <NetworkTopology />
    </div>
  );
};

export default AttackGraphPage;
