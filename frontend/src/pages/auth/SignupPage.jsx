import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup } from '../../config/authApi';
import './AuthPages.css';

const SignupPage = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', passwordConfirmation: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [redirecting, setRedirecting] = useState(false);

  const handleChange = (e) => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

  // Password strength calculation
  const getPasswordStrength = (pwd) => {
    if (!pwd) return { score: 0, label: '' };
    let score = 0;
    if (pwd.length >= 8) score++;
    if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++;
    if (/\d/.test(pwd)) score++;
    if (/[@$!%*?&#^()\-_+=]/.test(pwd)) score++;
    const labels = ['', 'Weak', 'Medium', 'Strong', 'Very Strong'];
    const classes = ['', 'weak', 'medium', 'strong', 'very-strong'];
    return { score, label: labels[score], className: classes[score] };
  };

  const strength = getPasswordStrength(form.password);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (form.password !== form.passwordConfirmation) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);
    try {
      await signup(form);
      setRedirecting(true);
      setTimeout(() => {
        navigate('/verify-email', { state: { email: form.email }, replace: true });
      }, 900);
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Signup failed. Please try again.';
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
          <p className="auth-loading-text">Creating your account...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-orbit-container auth-orbit-container--left" aria-hidden="true">
        {Array.from({ length: 21 }, (_, i) => (
          <div key={`signup-orbit-left-${i}`} className="auth-orbit-item" style={{ '--i': i }}></div>
        ))}
      </div>
      <div className="auth-orbit-container auth-orbit-container--right" aria-hidden="true">
        {Array.from({ length: 21 }, (_, i) => (
          <div key={`signup-orbit-right-${i}`} className="auth-orbit-item" style={{ '--i': i }}></div>
        ))}
      </div>
      <div className="auth-container">
        <div className="card-switch">
          <label className="switch">
            <input type="checkbox" className="toggle" checked={true} onChange={() => navigate('/login')} />
            <span className="slider"></span>
            <span className="card-side"></span>
          </label>
        </div>
        <div className="auth-card auth-card-elevated">
        <div className="auth-logo">
          <h1>WatchTower</h1>
        </div>
        <p className="auth-subtitle">Create your account</p>

        <div className="auth-feedback-slot">
          {error ? <div className="auth-alert auth-alert-error">⚠ {error}</div> : <div className="auth-feedback-placeholder" aria-hidden="true"></div>}
        </div>

        <form onSubmit={handleSubmit}>
          <div className="auth-form-group auth-float">
            <input
              id="signup-name"
              type="text"
              name="name"
              value={form.name}
              onChange={handleChange}
              placeholder=" "
              autoComplete="name"
              required
            />
            <label htmlFor="signup-name">Full Name</label>
          </div>

          <div className="auth-form-group auth-float">
            <input
              id="signup-email"
              type="email"
              name="email"
              value={form.email}
              onChange={handleChange}
              placeholder=" "
              autoComplete="email"
              required
            />
            <label htmlFor="signup-email">Email Address</label>
          </div>

          <div className="auth-form-group auth-float">
            <input
              id="signup-password"
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              placeholder=" "
              autoComplete="new-password"
              required
              minLength={8}
            />
            <label htmlFor="signup-password">Password</label>
            <div className={`password-strength-wrap ${form.password ? 'is-visible' : ''}`}>
              <div className="password-strength">
                {[1,2,3,4].map(i => (
                  <div key={i} className={`strength-bar ${i <= strength.score ? `active ${strength.className}` : ''}`} />
                ))}
              </div>
              <div className="strength-text">{form.password ? strength.label : ' '}</div>
            </div>
          </div>

          <div className="auth-form-group auth-float">
            <input
              id="signup-confirm"
              type="password"
              name="passwordConfirmation"
              value={form.passwordConfirmation}
              onChange={handleChange}
              placeholder=" "
              autoComplete="new-password"
              required
              minLength={8}
            />
            <label htmlFor="signup-confirm">Confirm Password</label>
          </div>

          <button type="submit" className="auth-btn auth-btn-animated" disabled={loading}>
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-text">{loading ? 'Creating account...' : 'Create Account ->'}</span>
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
      </div>
    </div>
  );
};

export default SignupPage;
