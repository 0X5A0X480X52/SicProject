import { useAuthStore } from '../stores/auth'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

export class ApiError extends Error {
  public readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const auth = useAuthStore()
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (auth.token) {
    headers.set('Authorization', `Bearer ${auth.token}`)
  }

  const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers })
  const payload = await response.json().catch(() => null)

  if (response.status === 401) {
    auth.clearSession()
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }
  if (!response.ok || payload?.success === false) {
    throw new ApiError(payload?.message ?? 'Request failed', response.status)
  }
  return payload.data as T
}
