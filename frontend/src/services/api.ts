import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 120000,
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const message = error.response.data?.message || error.response.statusText;
      return Promise.reject(new Error(message));
    }
    if (error.request) {
      return Promise.reject(new Error('网络连接失败，请检查后端服务是否运行'));
    }
    return Promise.reject(error);
  }
);

export default api;
