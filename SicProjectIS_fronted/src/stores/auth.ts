/**
 * 管理当前 token 和身份的持久化、登录更新及退出清理。身份用于页面展示与入口提示，最终业务权限仍由服务端校验。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
} from '../api/auth'
import type { CurrentUser, RegisterRequest } from '../types/auth'

const tokenKey = 'sic.auth.token'
const userKey = 'sic.auth.user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(tokenKey))
  const user = ref<CurrentUser | null>(readStoredUser())
  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  async function login(username: string, password: string) {
    const response = await loginRequest({ username, password })
    applyLoginResponse(response.token, response.user)
  }

  async function register(request: RegisterRequest) {
    const response = await registerRequest(request)
    applyLoginResponse(response.token, response.user)
  }

  /**
   * 同时替换内存身份与 localStorage，使后续 API 和 SSE 使用同一新 token；角色变更后需要重新登录走此路径。
   */
  function applyLoginResponse(nextToken: string, nextUser: CurrentUser) {
    token.value = nextToken
    user.value = nextUser
    localStorage.setItem(tokenKey, nextToken)
    localStorage.setItem(userKey, JSON.stringify(nextUser))
  }

  async function refreshCurrentUser() {
    if (!token.value) {
      clearSession()
      return
    }
    user.value = await getCurrentUser()
    localStorage.setItem(userKey, JSON.stringify(user.value))
  }

  /**
   * 尝试撤销当前 token，无论接口成功与否都在 finally 清理本地会话。
   */
  async function logout() {
    try {
      if (token.value) {
        await logoutRequest()
      }
    } finally {
      clearSession()
    }
  }

  /**
   * 清空响应式身份及两个持久化键，触发页面与连接订阅更新。
   */
  function clearSession() {
    token.value = null
    user.value = null
    localStorage.removeItem(tokenKey)
    localStorage.removeItem(userKey)
  }

  return { token, user, isAuthenticated, login, register, refreshCurrentUser, logout, clearSession }
})

function readStoredUser(): CurrentUser | null {
  const raw = localStorage.getItem(userKey)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    localStorage.removeItem(userKey)
    return null
  }
}
