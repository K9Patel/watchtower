import axios from 'axios';

const API = axios.create({
  baseURL: '/api/v1/auth',
  headers: { 'Content-Type': 'application/json' },
});

// Attach token to every request if available
API.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const signup = (data) => API.post('/signup', data);
export const verifyEmail = (data) => API.post('/verify-email', data);
export const resendVerification = (data) => API.post('/resend-verification', data);
export const loginApi = (data) => API.post('/login', data);
export const forgotPassword = (data) => API.post('/forgot-password', data);
export const resetPassword = (data) => API.post('/reset-password', data);
export const logoutApi = () => API.post('/logout');
export const getUser = (token) =>
  API.get('/user', { headers: { Authorization: `Bearer ${token}` } });

export default API;
