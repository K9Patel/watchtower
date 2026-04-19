import React, { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { verifyEmail, resendVerification } from '../../config/authApi';
import './AuthPages.css';

const VerifyEmailPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const email = location.state?.email || '';

  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [redirecting, setRedirecting] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const inputRefs = useRef([]);

  // Redirect if no email
  useEffect(() => {
    if (!email) navigate('/signup', { replace: true });
  }, [email, navigate]);

  // Cooldown timer
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => setCooldown(c => c - 1), 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  const handleInput = (index, value) => {
    if (!/^\d?$/.test(value)) return; // Only digits
    const newCode = [...code];
    newCode[index] = value;
    setCode(newCode);

    // Auto-focus next
    if (value && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }

    // Auto-submit when all filled
    if (value && index === 5 && newCode.every(d => d !== '')) {
      handleVerify(newCode.join(''));
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (pasted.length === 6) {
      const newCode = pasted.split('');
      setCode(newCode);
      inputRefs.current[5]?.focus();
      handleVerify(pasted);
    }
  };

  const handleVerify = async (codeStr) => {
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      const res = await verifyEmail({ email, code: codeStr });
      login(res.data.token, res.data.user);
      setRedirecting(true);
      setTimeout(() => {
        navigate('/', { replace: true });
      }, 900);
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Verification failed.';
      setError(msg);
      setCode(['', '', '', '', '', '']);
      inputRefs.current[0]?.focus();
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    setError('');
    setSuccess('');
    try {
      await resendVerification({ email });
      setSuccess('A new verification code has been sent to your email.');
      setCooldown(60);
    } catch (err) {
      setError('Failed to resend code. Please try again.');
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const codeStr = code.join('');
    if (codeStr.length === 6) handleVerify(codeStr);
  };

  const renderOrbitItems = (prefix) => Array.from({ length: 21 }, (_, i) => (
    <div key={`${prefix}-${i}`} className="auth-orbit-item" style={{ '--i': i }}></div>
  ));

  if (redirecting) {
    return (
      <div className="auth-page">
        <div className="auth-orbit-container auth-orbit-container--left" aria-hidden="true">
          {renderOrbitItems('verify-left-loading')}
        </div>
        <div className="auth-orbit-container auth-orbit-container--right" aria-hidden="true">
          {renderOrbitItems('verify-right-loading')}
        </div>
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
          <p className="auth-loading-text">Verifying your email...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-orbit-container auth-orbit-container--left" aria-hidden="true">
        {renderOrbitItems('verify-left')}
      </div>
      <div className="auth-orbit-container auth-orbit-container--right" aria-hidden="true">
        {renderOrbitItems('verify-right')}
      </div>
      <div className="auth-container">
      <div className="auth-card auth-card-elevated" style={{ textAlign: 'center', maxWidth: '440px' }}>
        <div className="auth-logo" style={{ justifyContent: 'center' }}>
          <div>
            <h1>WatchTower</h1>
          </div>
        </div>
        <p className="auth-subtitle" style={{ marginBottom: '16px' }}>Verify your email address</p>

        <div className="auth-alert auth-alert-info auth-alert-stack">
          <span>We&apos;ve sent a 6-digit verification code to</span>
          <strong>{email}</strong>
        </div>

        {error && <div className="auth-alert auth-alert-error auth-alert-stack">{error}</div>}
        {success && <div className="auth-alert auth-alert-success auth-alert-stack">{success}</div>}

        <form onSubmit={handleSubmit}>
          <div className="otp-container" onPaste={handlePaste}>
            {code.map((digit, i) => (
              <input
                key={i}
                ref={el => inputRefs.current[i] = el}
                className="otp-input"
                type="text"
                inputMode="numeric"
                maxLength={1}
                value={digit}
                onChange={(e) => handleInput(i, e.target.value)}
                onKeyDown={(e) => handleKeyDown(i, e)}
                autoFocus={i === 0}
              />
            ))}
          </div>

          <button type="submit" className="auth-btn auth-btn-animated" disabled={loading || code.join('').length < 6}>
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-line" />
            <span className="btn-text">{loading ? 'Verifying...' : 'VERIFY EMAIL →'}</span>
          </button>
        </form>

        <div className="resend-timer">
          {cooldown > 0 ? (
            <span>Resend code in <strong>{cooldown}s</strong></span>
          ) : (
            <span>
              Didn't receive the code?{' '}
              <button onClick={handleResend} type="button">Resend Code</button>
            </span>
          )}
        </div>

        <p className="auth-footer">
          <Link to="/login">← Back to login</Link>
        </p>
      </div>
      </div>
    </div>
  );
};

export default VerifyEmailPage;
