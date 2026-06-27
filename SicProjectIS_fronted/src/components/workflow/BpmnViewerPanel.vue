<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/diagram-js.css'
import type { ModuleStateRecord, TaskInstance } from '../../types/nodeForms'

const props = defineProps<{
  bpmnXml: string
  currentNodeId?: string | null
  history: ModuleStateRecord[]
  openTasks: TaskInstance[]
}>()

const containerRef = ref<HTMLElement | null>(null)
const largeRef = ref<HTMLElement | null>(null)
const largeOpen = ref(false)
const tooltip = ref({ visible: false, x: 0, y: 0, title: '', subtitle: '' })
const selectedId = ref<string | null>(null)
let viewer: any = null
let largeViewer: any = null

/* ── history-derived node sets ── */
function visitedNodeIds(): Set<string> {
  const ids = new Set<string>()
  for (const r of props.history) {
    if (r.fromNodeId) ids.add(r.fromNodeId)
    if (r.toNodeId) ids.add(r.toNodeId)
  }
  return ids
}

function taskNodeIds(): Set<string> {
  return new Set(props.openTasks.map((t) => t.nodeId).filter(Boolean) as string[])
}

/** Nodes that were returned/rejected in the current round */
function returnedNodeIds(): Set<string> {
  const ids = new Set<string>()
  for (const r of props.history) {
    const evt = (r.eventType ?? '').toUpperCase()
    const res = (r.result ?? '').toUpperCase()
    if (evt.includes('RETURN') || evt.includes('REJECT') || res.includes('RETURN')) {
      if (r.fromNodeId) ids.add(r.fromNodeId)
    }
  }
  return ids
}

/** Sequence flows that were traversed */
function completedFlowIds(): Set<string> {
  return new Set(
    props.history
      .filter((r) => {
        const evt = (r.eventType ?? '').toUpperCase()
        const res = (r.result ?? '').toUpperCase()
        return !evt.includes('RETURN') && !evt.includes('REJECT') && !res.includes('RETURN')
      })
      .map((r) => `Flow_${r.fromNodeId}_${r.toNodeId}`)
      .filter(Boolean),
  )
}

function returnedFlowIds(): Set<string> {
  return new Set(
    props.history
      .filter((r) => {
        const evt = (r.eventType ?? '').toUpperCase()
        const res = (r.result ?? '').toUpperCase()
        return evt.includes('RETURN') || evt.includes('REJECT') || res.includes('RETURN')
      })
      .map((r) => `Flow_${r.fromNodeId}_${r.toNodeId}`)
      .filter(Boolean),
  )
}

/* ── render ── */
async function render(target: HTMLElement, existing: any, kind: 'thumb' | 'large') {
  if (!props.bpmnXml) return existing
  const instance = existing ?? new BpmnViewer({ container: target })
  await instance.importXML(props.bpmnXml)
  const canvas = instance.get('canvas')
  canvas.zoom('fit-viewport', 'auto')

  clearViewer(instance)
  applyViewerState(instance)

  if (kind === 'large' && !existing) {
    bindLargeInteractions(instance)
  }

  return instance
}

/* ── clear all markers ── */
function clearViewer(instance: any) {
  try {
    const registry = instance.get('elementRegistry')
    const overlays = instance.get('overlays')
    registry.getAll().forEach((el: any) => {
      ['node-completed', 'node-current', 'node-returned', 'node-dim', 'node-selected', 'flow-completed', 'flow-returned'].forEach((m) => {
        try { instance.get('canvas').removeMarker(el.id, m) } catch (_) { /* ok */ }
      })
    })
    overlays.clear()
  } catch (_) { /* ok */ }
}

/* ── state → markers ── */
function applyViewerState(instance: any) {
  const canvas = instance.get('canvas')
  const elementRegistry = instance.get('elementRegistry')
  const overlays = instance.get('overlays')

  const visited = visitedNodeIds()
  const tasks = taskNodeIds()
  const returned = returnedNodeIds()
  const flowsDone = completedFlowIds()
  const flowsReturned = returnedFlowIds()
  const currentId = props.currentNodeId

  // First dim ALL known nodes
  visited.forEach((id) => {
    if (elementRegistry.get(id)) canvas.addMarker(id, 'node-dim')
  })

  // Completed nodes (visited but not current)
  for (const id of visited) {
    if (!elementRegistry.get(id)) continue
    if (id === currentId) continue
    canvas.removeMarker(id, 'node-dim')
    canvas.addMarker(id, 'node-completed')
  }

  // Current node
  if (currentId && elementRegistry.get(currentId)) {
    canvas.removeMarker(currentId, 'node-dim')
    canvas.addMarker(currentId, 'node-current')
    // overlay
    overlays.add(currentId, {
      position: { top: -14, right: -8 },
      html: '<div class="overlay-note green">当前</div>',
    })
  }

  // Returned nodes
  for (const id of returned) {
    if (!elementRegistry.get(id)) continue
    canvas.removeMarker(id, 'node-completed')
    canvas.addMarker(id, 'node-returned')
    overlays.add(id, {
      position: { top: -14, right: -8 },
      html: '<div class="overlay-note warn">退回</div>',
    })
  }

  // Node with open task (pending action from the user)
  for (const id of tasks) {
    if (!elementRegistry.get(id)) continue
    if (id === currentId) continue
    overlays.add(id, {
      position: { bottom: -10, left: -4 },
      html: '<div class="overlay-note">待办</div>',
    })
  }

  // Completed flows
  for (const fid of flowsDone) {
    if (elementRegistry.get(fid)) canvas.addMarker(fid, 'flow-completed')
  }

  // Returned flows
  for (const fid of flowsReturned) {
    if (elementRegistry.get(fid)) canvas.addMarker(fid, 'flow-returned')
  }

  // Selected highlight
  if (selectedId.value && elementRegistry.get(selectedId.value)) {
    canvas.addMarker(selectedId.value, 'node-selected')
  }
}

/* ── drag + wheel zoom for large viewer ── */
function bindLargeInteractions(instance: any) {
  const wrap = largeRef.value
  if (!wrap || wrap.dataset.bpmnBound === '1') return
  wrap.dataset.bpmnBound = '1'

  let dragging = false
  let lastX = 0
  let lastY = 0

  wrap.addEventListener('mousedown', (event: MouseEvent) => {
    if (event.button !== 0) return
    dragging = true
    lastX = event.clientX
    lastY = event.clientY
    wrap.classList.add('bpmn-dragging')
  })

  window.addEventListener('mouseup', () => {
    dragging = false
    wrap.classList.remove('bpmn-dragging')
  })

  window.addEventListener('mousemove', (event: MouseEvent) => {
    if (!dragging) return
    const dx = event.clientX - lastX
    const dy = event.clientY - lastY
    lastX = event.clientX
    lastY = event.clientY
    try { instance.get('canvas').scroll({ dx, dy }) } catch (_) { /* ok */ }
  })

  wrap.addEventListener('wheel', (event: WheelEvent) => {
    event.preventDefault()
    const canvas = instance.get('canvas')
    const current = canvas.zoom()
    const next = Math.max(0.2, Math.min(2.8, current + (event.deltaY < 0 ? 0.08 : -0.08)))
    canvas.zoom(next, 'auto')
  }, { passive: false })

  // click to select
  const eventBus = instance.get('eventBus')
  eventBus.on('element.click', 1500, (event: any) => {
    const el = event.element
    if (!el || !el.id) return
    if (el.type && String(el.type).includes('SequenceFlow')) return
    selectedId.value = selectedId.value === el.id ? null : el.id
    applyViewerState(instance)
    if (selectedId.value) {
      focusElement(instance, el)
    }
  })

  // hover tooltip
  eventBus.on('element.hover', 1500, (event: any) => {
    const el = event.element
    if (!el || !el.id) return
    if (el.type && String(el.type).includes('SequenceFlow')) return
    const original = event.originalEvent || window.event || { clientX: 0, clientY: 0 }
    tooltip.value = {
      visible: true,
      x: Math.min((original as MouseEvent).clientX + 14, window.innerWidth - 260),
      y: Math.min((original as MouseEvent).clientY + 14, window.innerHeight - 120),
      title: el.name || el.id,
      subtitle: el.type || '',
    }
  })
  eventBus.on('element.out', 1500, () => {
    tooltip.value.visible = false
  })
}

function focusElement(instance: any, element: any) {
  const canvas = instance.get('canvas')
  try {
    if (canvas.scrollToElement) {
      canvas.scrollToElement(element)
    } else {
      canvas.zoom(Math.max(canvas.zoom(), 1.0), {
        x: element.x + element.width / 2,
        y: element.y + element.height / 2,
      })
    }
  } catch (_) { /* ok */ }
}

/* ── render triggers ── */
async function renderMain() {
  await nextTick()
  if (containerRef.value) {
    viewer = await render(containerRef.value, viewer, 'thumb')
  }
}

async function openLarge() {
  largeOpen.value = true
  await nextTick()
  if (largeRef.value) {
    largeViewer = await render(largeRef.value, largeViewer, 'large')
  }
}

function zoom(delta: number) {
  if (!largeViewer) return
  const canvas = largeViewer.get('canvas')
  canvas.zoom(Math.max(0.2, Math.min(2.8, canvas.zoom() + delta)))
}

function fitLarge() {
  largeViewer?.get('canvas').zoom('fit-viewport', 'auto')
}

watch(() => [props.bpmnXml, props.currentNodeId, props.history.length, props.openTasks.length], renderMain, {
  immediate: true,
})

watch(largeOpen, async (open) => {
  if (open) await openLarge()
})

onBeforeUnmount(() => {
  viewer?.destroy()
  largeViewer?.destroy()
})

defineExpose({ openLarge })
</script>

<template>
  <el-card class="workflow-card bpmn-card" shadow="never">
    <template #header>
      <div class="card-header-row">
        <span>BPMN 流程图</span>
        <el-button size="small" @click="openLarge">展开大图</el-button>
      </div>
    </template>
    <div ref="containerRef" class="bpmn-canvas" />
  </el-card>

  <el-dialog v-model="largeOpen" title="完整 BPMN 流程图" width="92vw" top="4vh" destroy-on-close>
    <div class="bpmn-toolbar">
      <div>
        <el-tag v-if="selectedId" size="small" type="info" closable @close="selectedId = null; largeViewer && applyViewerState(largeViewer)">
          已选中: {{ selectedId }}
        </el-tag>
      </div>
      <div>
        <el-button-group>
          <el-button @click="zoom(0.15)">放大</el-button>
          <el-button @click="zoom(-0.15)">缩小</el-button>
          <el-button @click="fitLarge">适应窗口</el-button>
        </el-button-group>
      </div>
    </div>
    <div ref="largeRef" class="bpmn-canvas large" />
    <!-- tooltip -->
    <div
      v-show="tooltip.visible"
      class="bpmn-tooltip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
    >
      <strong>{{ tooltip.title }}</strong>
      <span>{{ tooltip.subtitle }}</span>
    </div>
  </el-dialog>
</template>
