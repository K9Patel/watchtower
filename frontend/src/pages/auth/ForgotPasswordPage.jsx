import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../../config/authApi';
import './AuthPages.css';

const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await forgotPassword({ email });
      setSuccess(true);
    } catch (err) {
      setError('Something went wrong. Please try again.');
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
        <p className="auth-subtitle">Reset your password</p>

        {error && <div className="auth-alert auth-alert-error">⚠ {error}</div>}

        {success ? (
          <>
            <div className="auth-alert auth-alert-success">
              ✓ If your email is registered, you will receive a password reset code.
            </div>
            <p style={{ color: '#94a3b8', fontSize: '14px', marginBottom: '24px', lineHeight: '1.6' }}>
              Check your inbox for the 6-digit code, then{' '}
              <Link to="/reset-password" state={{ email }} className="auth-link" style={{ fontWeight: 600 }}>
                click here to reset your password
              </Link>.
            </p>
            <Link to="/login">
              <button type="button" className="auth-btn auth-btn-secondary">← Back to Login</button>
            </Link>
          </>
        ) : (
          <>
            <p style={{ color: '#94a3b8', fontSize: '14px', marginBottom: '24px', lineHeight: '1.6' }}>
              Enter the email address associated with your account and we'll send you a code to reset your password.
            </p>

            <form onSubmit={handleSubmit}>
              <div className="auth-form-group">
                <label htmlFor="forgot-email">Email Address</label>
                <input
                  id="forgot-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  autoComplete="email"
                  required
                />
              </div>

              <button type="submit" className="auth-btn" disabled={loading}>
                {loading ? 'Sending...' : 'Send Reset Code →'}
              </button>
            </form>
          </>
        )}

        <p className="auth-footer">
          Remember your password? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
