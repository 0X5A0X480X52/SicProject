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

  async function logout() {
    try {
      if (token.value) {
        await logoutRequest()
      }
    } finally {
      clearSession()
    }
  }

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
