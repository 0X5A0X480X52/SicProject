<script setup lang="ts">
/**
 * 展示项目运行实例使用的 BPMN 并标记当前节点；流程图中的节点代码与业务状态、数据库节点 ID 分别处理。
 */
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

/* 从状态历史派生节点和连线集合。 */
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

/** 从可见历史识别退回或驳回的来源节点；当前实现不按 roundNo 额外过滤。 */
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

/** 依据历史记录中的来源与目标节点关联已走过的顺序流。 */
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

/* 导入并展示当前定义，处理普通和放大视图。 */
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

/* 重算节点状态前移除上一轮标记。 */
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

/* 将状态历史、当前节点和开放任务映射成展示标记。 */
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

    // 先统一淡化已知节点，再叠加已走过、当前及待办状态。
  visited.forEach((id) => {
    if (elementRegistry.get(id)) canvas.addMarker(id, 'node-dim')
  })

    // 历史已到达但不是当前节点的节点显示为已办理。
  for (const id of visited) {
    if (!elementRegistry.get(id)) continue
    if (id === currentId) continue
    canvas.removeMarker(id, 'node-dim')
    canvas.addMarker(id, 'node-completed')
  }

    // 当前运行节点单独高亮，不由图上的点击选择改变业务状态。
  if (currentId && elementRegistry.get(currentId)) {
    canvas.removeMarker(currentId, 'node-dim')
    canvas.addMarker(currentId, 'node-current')
      // 当前节点叠加状态提示。
    overlays.add(currentId, {
      position: { top: -14, right: -8 },
      html: '<div class="overlay-note green">当前</div>',
    })
  }

    // 历史退回来源节点使用退回标记。
  for (const id of returned) {
    if (!elementRegistry.get(id)) continue
    canvas.removeMarker(id, 'node-completed')
    canvas.addMarker(id, 'node-returned')
    overlays.add(id, {
      position: { top: -14, right: -8 },
      html: '<div class="overlay-note warn">退回</div>',
    })
  }

    // 开放任务节点标记待办；实际可操作性仍来自运行时权限。
  for (const id of tasks) {
    if (!elementRegistry.get(id)) continue
    if (id === currentId) continue
    overlays.add(id, {
      position: { bottom: -10, left: -4 },
      html: '<div class="overlay-note">待办</div>',
    })
  }

    // 历史走过的连线显示为已通过。
  for (const fid of flowsDone) {
    if (elementRegistry.get(fid)) canvas.addMarker(fid, 'flow-completed')
  }

    // 退回连线使用单独样式。
  for (const fid of flowsReturned) {
    if (elementRegistry.get(fid)) canvas.addMarker(fid, 'flow-returned')
  }

    // 用户选择仅增加浏览高亮，不改变当前办理节点。
  if (selectedId.value && elementRegistry.get(selectedId.value)) {
    canvas.addMarker(selectedId.value, 'node-selected')
  }
}

/* 放大视图的拖动与滚轮缩放。 */
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

    // 点击选择节点，供查看关联历史使用。
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

    // 悬停展示节点名称及状态提示。
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

/* 监听定义及运行时输入变化，重建展示标记。 */
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
