/**
 * 管理页面实时刷新通道：SSE 重连、token 切换、焦点与网络恢复、可见页定时刷新及卸载清理。事件仅提示重查，不能替代业务接口鉴权。
 */
import { onBeforeUnmount, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import type { ModuleStateChangedEvent } from '../types/workflow'

/**
 * 为使用页面提供 connect/disconnect，统一管理实时连接及补偿刷新；事件回调负责从业务接口重查，不能直接相信推送中的权限信息。
 */
export function useWorkflowEvents(handler: (event: ModuleStateChangedEvent) => void) {
  const auth = useAuthStore()
  let source: EventSource | null = null
  let active = false
  let retry: ReturnType<typeof setTimeout> | null = null
  let poll: ReturnType<typeof setInterval> | null = null
  let delay = 1000
  let lastRefresh = 0
  /**
   * 可见且已登录时发出重查提示，限制短时间内重复刷新；普通业务事件仍由各自回调处理。
   */
  function refresh() {
    if (!active || !auth.token || document.visibilityState === 'hidden' || Date.now() - lastRefresh < 1000) return
    lastRefresh = Date.now()
    handler({ type: 'WORKBENCH_INVALIDATED' })
  }
  function close() { source?.close(); source = null; if (retry) clearTimeout(retry); retry = null }
  /**
   * 使用当前 token 打开 SSE，CONNECTED 触发重查；断线按一至三十秒退避重连，防止故障时持续密集连接。
   */
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
      // token 切换后旧连接仍可能回调；只让当前连接关闭自己，避免误关新会话连接。
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
  /**
   * 关闭连接，取消重连及轮询，并移除页面焦点、网络与可见性监听，避免卸载页面继续刷新。
   */
  function disconnect() {
    active = false; close()
    if (poll) clearInterval(poll); poll = null
    window.removeEventListener('focus', wake); window.removeEventListener('online', wake)
    document.removeEventListener('visibilitychange', wake)
  }
  // EventSource 的 URL 固定了 token，身份更新后必须替换连接，不能只更新 auth store。
  const stop = watch(() => auth.token, () => { close(); delay = 1000; if (active) open() })
  onBeforeUnmount(() => { disconnect(); stop() })
  return { connect, disconnect }
}
