import { onBeforeUnmount } from 'vue'
import { useAuthStore } from '../stores/auth'
import type { ModuleStateChangedEvent } from '../types/workflow'

type Handler = (event: ModuleStateChangedEvent) => void

export function useWorkflowEvents(handler: Handler) {
  let source: EventSource | null = null

  function connect() {
    if (source || typeof EventSource === 'undefined') {
      return
    }
    const auth = useAuthStore()
    const token = auth.token ? `?token=${encodeURIComponent(auth.token)}` : ''
    source = new EventSource(`/api/sse/subscribe${token}`)
    source.addEventListener('MODULE_STATE_CHANGED', (event) => {
      try {
        handler(JSON.parse((event as MessageEvent).data) as ModuleStateChangedEvent)
      } catch {
        // Ignore malformed notification payloads; the next manual refresh remains authoritative.
      }
    })
    source.onerror = () => {
      source?.close()
      source = null
    }
  }

  function disconnect() {
    source?.close()
    source = null
  }

  onBeforeUnmount(disconnect)

  return { connect, disconnect }
}
