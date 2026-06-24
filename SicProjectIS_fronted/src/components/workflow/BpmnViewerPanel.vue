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
let viewer: any = null
let largeViewer: any = null

function nodeIds() {
  return new Set([
    ...props.history.map((record) => record.fromNodeId).filter(Boolean),
    ...props.history.map((record) => record.toNodeId).filter(Boolean),
  ] as string[])
}

function taskIds() {
  return new Set(props.openTasks.map((task) => task.nodeId).filter(Boolean))
}

async function render(target: HTMLElement, existing: any) {
  if (!props.bpmnXml) return existing
  const instance = existing ?? new BpmnViewer({ container: target })
  await instance.importXML(props.bpmnXml)
  const canvas = instance.get('canvas')
  canvas.zoom('fit-viewport', 'auto')
  nodeIds().forEach((id) => canvas.addMarker(id, 'bpmn-node-completed'))
  taskIds().forEach((id) => canvas.addMarker(id, 'bpmn-node-task'))
  if (props.currentNodeId) {
    canvas.addMarker(props.currentNodeId, 'bpmn-node-current')
  }
  return instance
}

async function renderMain() {
  await nextTick()
  if (containerRef.value) {
    viewer = await render(containerRef.value, viewer)
  }
}

async function openLarge() {
  largeOpen.value = true
  await nextTick()
  if (largeRef.value) {
    largeViewer = await render(largeRef.value, largeViewer)
  }
}

function zoom(delta: number) {
  if (!largeViewer) return
  const canvas = largeViewer.get('canvas')
  const current = canvas.zoom()
  canvas.zoom(Math.max(0.25, Math.min(2.5, current + delta)))
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
      <el-button-group>
        <el-button @click="zoom(0.15)">放大</el-button>
        <el-button @click="zoom(-0.15)">缩小</el-button>
        <el-button @click="fitLarge">适应窗口</el-button>
      </el-button-group>
    </div>
    <div ref="largeRef" class="bpmn-canvas large" />
  </el-dialog>
</template>
