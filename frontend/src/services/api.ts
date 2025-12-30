import axios from 'axios'

const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000, // 15 seconds timeout (backend fails at 10s, so this gives buffer)
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor to add auth token if available
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor to handle errors with better user feedback
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Handle different error types
    if (error.response) {
      // Server responded with error status
      const status = error.response.status
      const message = error.response.data?.message || error.response.data?.error || 'An error occurred'
      
      switch (status) {
        case 401:
          // Token expired or invalid
          localStorage.removeItem('accessToken')
          localStorage.removeItem('user')
          // Don't redirect immediately - let the component handle it
          break
        case 403:
          console.error('Forbidden: ', message)
          break
        case 404:
          console.error('Not found: ', message)
          break
        case 429:
          console.error('Rate limited: ', message)
          break
        case 500:
        case 502:
        case 503:
          console.error('Server error: ', message)
          break
        default:
          console.error(`API error (${status}): `, message)
      }
    } else if (error.request) {
      // Request was made but no response received
      console.error('Network error: No response from server. Please check your connection.')
    } else {
      // Something else happened
      console.error('Request error: ', error.message)
    }
    
    return Promise.reject(error)
  }
)

export default api
