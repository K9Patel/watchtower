import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup } from '../../config/authApi';
import './AuthPages.css';

const SignupPage = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', passwordConfirmation: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

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
      navigate('/verify-email', { state: { email: form.email }, replace: true });
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Signup failed. Please try again.';
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
        <p className="auth-subtitle">Create your account</p>

        {error && <div className="auth-alert auth-alert-error">⚠ {error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="auth-form-group">
            <label htmlFor="signup-name">Full Name</label>
            <input
              id="signup-name"
              type="text"
              name="name"
              value={form.name}
              onChange={handleChange}
              placeholder="John Doe"
              autoComplete="name"
              required
            />
          </div>

          <div className="auth-form-group">
            <label htmlFor="signup-email">Email Address</label>
            <input
              id="signup-email"
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
            <label htmlFor="signup-password">Password</label>
            <input
              id="signup-password"
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              placeholder="Min 8 chars, upper + lower + number + symbol"
              autoComplete="new-password"
              required
              minLength={8}
            />
            {form.password && (
              <>
                <div className="password-strength">
                  {[1,2,3,4].map(i => (
                    <div key={i} className={`strength-bar ${i <= strength.score ? `active ${strength.className}` : ''}`} />
                  ))}
                </div>
                <div className="strength-text">{strength.label}</div>
              </>
            )}
          </div>

          <div className="auth-form-group">
            <label htmlFor="signup-confirm">Confirm Password</label>
            <input
              id="signup-confirm"
              type="password"
              name="passwordConfirmation"
              value={form.passwordConfirmation}
              onChange={handleChange}
              placeholder="Re-enter your password"
              autoComplete="new-password"
              required
              minLength={8}
            />
          </div>

          <button type="submit" className="auth-btn" disabled={loading}>
            {loading ? 'Creating account...' : 'Create Account →'}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
};

export default SignupPage;
