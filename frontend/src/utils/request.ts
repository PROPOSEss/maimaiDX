/**
 * 网络请求封装
 */

const BASE_URL = 'http://localhost:8080/api'

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
  showLoading?: boolean
  needAuth?: boolean
}

interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

function getToken(): string {
  return uni.getStorageSync('token') || ''
}

function getUserId(): string {
  return uni.getStorageSync('userId') || ''
}

/**
 * 通用请求方法
 */
export function request<T = any>(options: RequestOptions): Promise<T> {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    showLoading = false,
    needAuth = true,
  } = options

  if (showLoading) {
    uni.showLoading({ title: '加载中...' })
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header,
  }

  if (needAuth) {
    const token = getToken()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    const userId = getUserId()
    if (userId) {
      headers['X-User-Id'] = userId
    }
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}${url}`,
      method,
      data,
      header: headers,
      success: (res) => {
        const response = res.data as ApiResponse<T>
        if (response.code === 200) {
          resolve(response.data)
        } else if (response.code === 401) {
          // Token 过期，跳转登录
          clearAuth()
          uni.navigateTo({ url: '/pages/login/login' })
          reject(new Error('登录已过期，请重新登录'))
        } else {
          uni.showToast({ title: response.message || '请求失败', icon: 'none' })
          reject(new Error(response.message))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(err)
      },
      complete: () => {
        if (showLoading) {
          uni.hideLoading()
        }
      },
    })
  })
}

export function get<T = any>(url: string, data?: any) {
  return request<T>({ url, method: 'GET', data })
}

export function post<T = any>(url: string, data?: any) {
  return request<T>({ url, method: 'POST', data })
}

export function del<T = any>(url: string, data?: any) {
  return request<T>({ url, method: 'DELETE', data })
}

// ========== 登录状态管理 ==========

export function setAuth(token: string, userId: string) {
  uni.setStorageSync('token', token)
  uni.setStorageSync('userId', userId)
}

export function clearAuth() {
  uni.removeStorageSync('token')
  uni.removeStorageSync('userId')
}

export function isLoggedIn(): boolean {
  return !!getToken()
}
