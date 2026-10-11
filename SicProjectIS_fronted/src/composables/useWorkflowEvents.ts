import { onBeforeUnmount, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import type { ModuleStateChangedEvent } from '../types/workflow'

export function useWorkflowEvents(handler: (event: ModuleStateChangedEvent) => void) {
  const auth = useAuthStore()
  let source: EventSource | null = null
  let active = false
  let retry: ReturnType<typeof setTimeout> | null = null
  let poll: ReturnType<typeof setInterval> | null = null
  let delay = 1000
  let lastRefresh = 0
  function refresh() {
    if (!active || !auth.token || document.visibilityState === 'hidden' || Date.now() - lastRefresh < 1000) return
    lastRefresh = Date.now()
    handler({ type: 'WORKBENCH_INVALIDATED' })
  }
  function close() { source?.close(); source = null; if (retry) clearTimeout(retry); retry = null }
  function open() {
    if (!active || source || !auth.token || typeof EventSource === 'undefined') return
    const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
    source = new EventSource(`${base}/sse/subscribe?token=${encodeURIComponent(auth.token)}`)
    const connection = source
    source.addEventListener('CONNECTED', () => { delay = 1000; refresh() })
    source.addEventListener('MODULE_STATE_CHANGED', (event) => {
      try { handler(JSON.parse((event as MessageEvent).data) as ModuleStateChangedEvent) } catch { refresh() }
    })
    source.onerror = () => {
      if (source !== connection) return
      close()
      if (active && auth.token) {
        retry = setTimeout(() => { retry = null; open() }, delay)
        delay = Math.min(delay * 2, 30000)
      }
    }
  }
  function wake() { if (active) { open(); refresh() } }
  function connect() {
    if (active) return
    active = true; open()
    window.addEventListener('focus', wake); window.addEventListener('online', wake)
    document.addEventListener('visibilitychange', wake)
    poll = setInterval(refresh, 30000)
  }
  function disconnect() {
    active = false; close()
    if (poll) clearInterval(poll); poll = null
    window.removeEventListener('focus', wake); window.removeEventListener('online', wake)
    document.removeEventListener('visibilitychange', wake)
  }
  const stop = watch(() => auth.token, () => { close(); delay = 1000; if (active) open() })
  onBeforeUnmount(() => { disconnect(); stop() })
  return { connect, disconnect }
}
