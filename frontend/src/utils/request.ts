import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'cloud_token'
let redirecting = false

const request = axios.create({
  baseURL: '/api',
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 上传请求不设超时（大文件），其他请求默认 30s
  if (!config.timeout) {
    const isUpload = config.data instanceof FormData
    if (!isUpload) {
      config.timeout = 30_000
    }
  }
  return config
})

request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      if (redirecting) return Promise.reject(error)
      redirecting = true
      localStorage.removeItem(TOKEN_KEY)
      ElMessage.error('登录已过期，请重新登录')
      setTimeout(() => {
        window.location.href = '/login'
      }, 1500)
    }
    return Promise.reject(error)
  }
)

export default request
