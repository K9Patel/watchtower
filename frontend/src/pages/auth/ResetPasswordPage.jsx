import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { resetPassword } from '../../config/authApi';
import './AuthPages.css';

const ResetPasswordPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({
    email: location.state?.email || '',
    code: '',
    password: '',
    passwordConfirmation: ''
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (form.password !== form.passwordConfirmation) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);
    try {
      await resetPassword(form);
      setSuccess(true);
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Reset failed. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-logo">
          <div className="auth-logo-icon">🗼</div>
          <div>
            <h1>Watch<span>Tower</span></h1>
          </div>
        </div>
        <p className="auth-subtitle">Set your new password</p>

        {error && <div className="auth-alert auth-alert-error">⚠ {error}</div>}

        {success ? (
          <>
            <div className="auth-alert auth-alert-success">
              ✓ Password reset successfully! You can now sign in with your new password.
            </div>
            <button
              type="button"
              className="auth-btn"
              onClick={() => navigate('/login', { replace: true })}
            >
              Sign In →
            </button>
          </>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className="auth-form-group">
              <label htmlFor="reset-email">Email Address</label>
              <input
                id="reset-email"
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
                placeholder="you@example.com"
                autoComplete="email"
                required
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="reset-code">6-Digit Reset Code</label>
              <input
                id="reset-code"
                type="text"
                name="code"
                value={form.code}
                onChange={handleChange}
                placeholder="Enter the code from your email"
                maxLength={6}
                inputMode="numeric"
                autoComplete="one-time-code"
                required
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="reset-password">New Password</label>
              <input
                id="reset-password"
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="Min 8 chars, upper + lower + number + symbol"
                autoComplete="new-password"
                required
                minLength={8}
              />
            </div>

            <div className="auth-form-group">
              <label htmlFor="reset-confirm">Confirm New Password</label>
              <input
                id="reset-confirm"
                type="password"
                name="passwordConfirmation"
                value={form.passwordConfirmation}
                onChange={handleChange}
                placeholder="Re-enter your new password"
                autoComplete="new-password"
                required
                minLength={8}
              />
            </div>

            <button type="submit" className="auth-btn" disabled={loading}>
              {loading ? 'Resetting...' : 'Reset Password →'}
            </button>
          </form>
        )}

        <p className="auth-footer">
          <Link to="/login">← Back to login</Link>
        </p>
      </div>
    </div>
  );
};

export default ResetPasswordPage;
