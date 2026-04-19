import React from 'react';
import '../pages/Pages.css';

const LoadingSpinner = ({ text = "LOADING" }) => {
  return (
    <div className="loading-screen" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', width: '100%' }}>
      <div className="loading-card">
        <div className="text-loader">
          <p>{text}</p>
          <div className="text-loader-words">
            <span className="text-loader-word">SYSTEMS</span>
            <span className="text-loader-word">NETWORK</span>
            <span className="text-loader-word">DEVICES</span>
            <span className="text-loader-word">ALERTS</span>
            <span className="text-loader-word">MODULES</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoadingSpinner;
