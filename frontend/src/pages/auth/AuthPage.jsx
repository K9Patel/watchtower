import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { loginApi, signup } from '../../config/authApi';
import './AuthPages.css';

const AuthPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  
  // Track if we are in signup mode (flipped)
  const [isFlipped, setIsFlipped] = useState(location.pathname === '/signup');

  // Form states
  const [loginForm, setLoginForm] = useState({ email: '', password: '', remember: false });
  const [signupForm, setSignupForm] = useState({ name: '', email: '', password: '', passwordConfirmation: '' });
  
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [redirecting, setRedirecting] = useState(false);
  const [redirectMsg, setRedirectMsg] = useState('');

  // Sync state if user uses browser back/forward buttons
  useEffect(() => {
    setIsFlipped(location.pathname === '/signup');
  }, [location.pathname]);

  const handleToggle = () => {
    const newFlipped = !isFlipped;
    setIsFlipped(newFlipped);
    setError('');
    // Update URL without triggering a full remount (which breaks the CSS transition)
    window.history.replaceState(null, '', newFlipped ? '/signup' : '/login');
  };

  const handleLoginChange = (e) => {
    const { name, value, type, checked } = e.target;
    setLoginForm(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
  };

  const handleSignupChange = (e) => {
    setSignupForm(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

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

  const strength = getPasswordStrength(signupForm.password);

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await loginApi(loginForm);
      login(res.data.token, res.data.user);
      setRedirectMsg('Signing you in...');
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

  const handleSignupSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (signupForm.password !== signupForm.passwordConfirmation) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);
    try {
      await signup(signupForm);
      setRedirectMsg('Creating your account...');
      setRedirecting(true);
      setTimeout(() => {
        navigate('/verify-email', { state: { email: signupForm.email }, replace: true });
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
          <p className="auth-loading-text">{redirectMsg}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      {/* Animated Concentric Rings Background */}
      <div className="bg-anim-container">
        {[...Array(21)].map((_, i) => (
          <div key={i} className="bg-anim-item" style={{ '--i': i }}></div>
        ))}
      </div>

      <div className="auth-container">
        
        <div className="card-switch">
          <label className="switch">
            <input type="checkbox" className="toggle" checked={isFlipped} onChange={handleToggle} />
            <span className="slider"></span>
            <span className="card-side"></span>
          </label>
        </div>

        <div className={`flip-card ${isFlipped ? 'flipped' : ''}`}>
          <div className="flip-card__inner">
            
            {/* FRONT (LOGIN) */}
            <div className="flip-card__front">
              <div className="auth-card">
                <div className="auth-logo">
                  <h1>WatchTower</h1>
                </div>
                <p className="auth-subtitle">Sign in to your account</p>

                {error && !isFlipped && <div className="auth-alert auth-alert-error">⚠ {error}</div>}

                <form onSubmit={handleLoginSubmit}>
                  <div className="auth-form-group auth-float">
                    <input
                      id="login-email"
                      type="email"
                      name="email"
                      value={loginForm.email}
                      onChange={handleLoginChange}
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
                      value={loginForm.password}
                      onChange={handleLoginChange}
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
                        checked={loginForm.remember}
                        onChange={handleLoginChange}
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
                  Don't have an account? <a href="#" onClick={(e) => { e.preventDefault(); handleToggle(); }}>Sign up</a>
                </p>
              </div>
            </div>

            {/* BACK (SIGNUP) */}
            <div className="flip-card__back">
              <div className="auth-card">
                <div className="auth-logo">
                  <h1>WatchTower</h1>
                </div>
                <p className="auth-subtitle">Create your account</p>

                {error && isFlipped && <div className="auth-alert auth-alert-error">⚠ {error}</div>}

                <form onSubmit={handleSignupSubmit}>
                  <div className="auth-form-group auth-float">
                    <input
                      id="signup-name"
                      type="text"
                      name="name"
                      value={signupForm.name}
                      onChange={handleSignupChange}
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
                      value={signupForm.email}
                      onChange={handleSignupChange}
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
                      value={signupForm.password}
                      onChange={handleSignupChange}
                      placeholder=" "
                      autoComplete="new-password"
                      required
                      minLength={8}
                    />
                    <label htmlFor="signup-password">Password</label>
                    {signupForm.password && (
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

                  <div className="auth-form-group auth-float">
                    <input
                      id="signup-confirm"
                      type="password"
                      name="passwordConfirmation"
                      value={signupForm.passwordConfirmation}
                      onChange={handleSignupChange}
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
                    <span className="btn-text">{loading ? 'Creating...' : 'Create Account ->'}</span>
                  </button>
                </form>

                <p className="auth-footer">
                  Already have an account? <a href="#" onClick={(e) => { e.preventDefault(); handleToggle(); }}>Sign in</a>
                </p>
              </div>
            </div>

          </div>
        </div>

      </div>
    </div>
  );
};

export default AuthPage;
