/**
 * 封装注册、登录、当前身份和注销接口；登录返回的身份交由 auth store 管理，不在请求层自行推导业务权限。
 */
import { apiRequest } from './client'
import type { CurrentUser, LoginRequest, LoginResponse, RegisterRequest } from '../types/auth'

export function login(request: LoginRequest) {
  return apiRequest<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function register(request: RegisterRequest) {
  return apiRequest<LoginResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>('/auth/me')
}

export function logout() {
  return apiRequest<void>('/auth/logout', { method: 'POST' })
}
