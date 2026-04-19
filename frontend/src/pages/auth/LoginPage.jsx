import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { loginApi } from '../../config/authApi';
import './AuthPages.css';

const LoginPage = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [form, setForm] = useState({ email: '', password: '', remember: false });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [redirecting, setRedirecting] = useState(false);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await loginApi(form);
      login(res.data.token, res.data.user);
      setRedirecting(true);
      setTimeout(() => {
        navigate('/', { replace: true });
      }, 900);
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Login failed. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  if (redirecting) {
    return (
      <div className="auth-page">
        <div className="auth-loading-overlay">
          <div className="banter-loader" aria-hidden="true">
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
            <div className="banter-loader__box"></div>
          </div>
          <p className="auth-loading-text">Signing you in...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-orbit-container auth-orbit-container--left" aria-hidden="true">
        {Array.from({ length: 21 }, (_, i) => (
          <div key={`login-orbit-left-${i}`} className="auth-orbit-item" style={{ '--i': i }}></div>
        ))}
      </div>
      <div className="auth-orbit-container auth-orbit-container--right" aria-hidden="true">
        {Array.from({ length: 21 }, (_, i) => (
          <div key={`login-orbit-right-${i}`} className="auth-orbit-item" style={{ '--i': i }}></div>
        ))}
      </div>
      <div className="auth-container">
        <div className="card-switch">
          <label className="switch">
            <input type="checkbox" className="toggle" checked={false} onChange={() => navigate('/signup')} />
            <span className="slider"></span>
            <span className="card-side"></span>
          </label>
        </div>
        <div className="auth-card auth-card-elevated">
        <div className="auth-logo">
          <h1>WatchTower</h1>
        </div>
        <p className="auth-subtitle">Sign in to your account</p>


        {error && <div className="auth-alert auth-alert-error">⚠ {error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="auth-form-group auth-float">
            <input
              id="login-email"
              type="email"
              name="email"
              value={form.email}
              onChange={handleChange}
              placeholder=" "
              autoComplete="email"
              required
            />
            <label htmlFor="login-email">Email Address</label>
          </div>

          <div className="auth-form-group auth-float">
            <input
              id="login-password"
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              placeholder=" "
              autoComplete="current-password"
              required
            />
            <label htmlFor="login-password">Password</label>
          </div>

          <div className="auth-checkbox-row">
            <label>
              <input
                type="checkbox"
                name="remember"
                checked={form.remember}
                onChange={handleChange}
              />
              Remember me
            </label>
            <Link to="/forgot-password" className="auth-link">Forgot password?</Link>
          </div>

          <button type="submit" className="auth-btn auth-btn-animated" disabled={loading}>
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-text">{loading ? 'Signing in...' : 'Sign In ->'}</span>
          </button>
        </form>

        <p className="auth-footer">
          Don't have an account? <Link to="/signup">Sign up</Link>
        </p>
      </div>
      </div>
    </div>
  );
};

export default LoginPage;
