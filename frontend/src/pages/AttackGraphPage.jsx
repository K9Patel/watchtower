import React from 'react';
import AttackPathGraph from '../components/AttackPathGraph';
import './Pages.css';

const AttackGraphPage = () => {
  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1>Attack Graph</h1>
          <p className="page-subtitle">Lateral movement and risk-flow visualization across active devices.</p>
        </div>
      </div>

      <AttackPathGraph />
    </div>
  );
};

export default AttackGraphPage;
