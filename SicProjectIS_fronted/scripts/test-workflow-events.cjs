const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

function fixture() {
  const auth = { token: 'fixture-session' }
  const sources = [], timers = new Map(), intervals = new Map(), windowEvents = new Map(), documentEvents = new Map()
  let cleanup, changed, now = 1000, nextId = 0
  const changes = []
  class Source {
    constructor(url) { this.url = url; this.handlers = new Map(); this.closed = false; sources.push(this) }
    addEventListener(name, fn) { this.handlers.set(name, fn) }
    close() { this.closed = true }
    emit(name, data) { this.handlers.get(name)?.({ data: JSON.stringify(data) }) }
  }
  const doc = { visibilityState: 'visible', addEventListener: (key, fn) => documentEvents.set(key, fn), removeEventListener: key => documentEvents.delete(key) }
  const output = { exports: {} }
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/composables/useWorkflowEvents.ts'), 'utf8')
    .replace('import.meta.env', '({ VITE_API_BASE_URL: "/api" })'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  vm.runInNewContext(code, {
    exports: output.exports,
    require: name => name === 'vue' ? { onBeforeUnmount: fn => cleanup = fn, watch: (_, fn) => { changed = fn; return () => {} } } : { useAuthStore: () => auth },
    EventSource: Source, document: doc, Date: { now: () => now },
    window: { addEventListener: (key, fn) => windowEvents.set(key, fn), removeEventListener: key => windowEvents.delete(key) },
    setTimeout: (fn, delay) => { timers.set(++nextId, { fn, delay }); return nextId }, clearTimeout: id => timers.delete(id),
    setInterval: fn => { intervals.set(++nextId, fn); return nextId }, clearInterval: id => intervals.delete(id),
  })
  const hook = output.exports.useWorkflowEvents(event => changes.push(event.type))
  return { auth, sources, timers, intervals, windowEvents, documentEvents, doc, changes, hook,
    cleanup: () => cleanup(), changeToken: token => { auth.token = token; changed() }, tick: () => { now += 2000 } }
}

test('SSE errors schedule a reconnect and a new connection reloads authoritative data', () => {
  const f = fixture(); f.hook.connect(); f.sources[0].emit('CONNECTED')
  assert.deepEqual(f.changes, ['WORKBENCH_INVALIDATED'])
  f.sources[0].onerror(); assert.equal(f.sources[0].closed, true)
  const timer = [...f.timers.values()][0]; assert.equal(timer.delay, 1000)
  timer.fn(); assert.equal(f.sources.length, 2)
  f.tick(); f.sources[1].emit('CONNECTED'); assert.equal(f.changes.length, 2)
  f.cleanup()
})
test('role change login replaces the stream and stale stream errors cannot close it', () => {
  const f = fixture(); f.hook.connect(); const previous = f.sources[0]
  f.changeToken('new-session'); assert.equal(previous.closed, true)
  assert.equal(f.sources.length, 2); assert.ok(f.sources[1].url.includes('new-session'))
  previous.onerror(); assert.equal(f.sources[1].closed, false)
  f.changeToken(null); assert.equal(f.sources[1].closed, true); assert.equal(f.timers.size, 0)
  f.cleanup()
})
test('focus and polling recover missed notifications and hidden pages avoid polling', () => {
  const f = fixture(); f.hook.connect(); f.windowEvents.get('focus')()
  assert.equal(f.changes.length, 1)
  f.doc.visibilityState = 'hidden'; f.tick(); [...f.intervals.values()][0]()
  assert.equal(f.changes.length, 1)
  f.doc.visibilityState = 'visible'; f.documentEvents.get('visibilitychange')()
  assert.equal(f.changes.length, 2)
  f.cleanup()
})
test('unmount cancels reconnection, polling and event listeners', () => {
  const f = fixture(); f.hook.connect(); f.sources[0].onerror(); f.cleanup()
  assert.equal(f.timers.size, 0); assert.equal(f.intervals.size, 0)
  assert.equal(f.windowEvents.size, 0); assert.equal(f.documentEvents.size, 0)
})
test('normal workflow changes and targeted authorization hints both reach the subscriber', () => {
  const f = fixture(); f.hook.connect()
  f.sources[0].emit('MODULE_STATE_CHANGED', { type: 'MODULE_STATE_CHANGED', moduleInstanceId: 1 })
  f.sources[0].emit('MODULE_STATE_CHANGED', { type: 'WORKBENCH_INVALIDATED', projectId: 1 })
  assert.deepEqual(f.changes, ['MODULE_STATE_CHANGED', 'WORKBENCH_INVALIDATED'])
  f.cleanup()
})
