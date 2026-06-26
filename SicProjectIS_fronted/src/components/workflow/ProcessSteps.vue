<script setup lang="ts">
import { computed, type PropType } from 'vue'

export type WorkflowProcessStepStatus = 'done' | 'current' | 'returned' | 'rejected' | 'waiting'

export interface WorkflowProcessStep {
  id: string
  name: string
  status: WorkflowProcessStepStatus
  statusText: string
  desc?: string
  actor?: string
  finishedAt?: string
  roundCount?: number
}

const props = defineProps({
  nodes: { type: Array as PropType<WorkflowProcessStep[]>, required: true },
  selectedNodeId: { type: String, default: '' },
})

defineEmits<{
  'select-node': [node: WorkflowProcessStep]
}>()

const currentIndex = computed(() => {
  const index = props.nodes.findIndex((node) => node.status === 'current')
  if (index >= 0) return index
  for (let i = props.nodes.length - 1; i >= 0; i -= 1) {
    if (props.nodes[i].status !== 'waiting') return i
  }
  return 0
})

function stepLabel(index: number, node: WorkflowProcessStep) {
  if (node.status === 'done') return '✓'
  if (node.status === 'returned') return '↩'
  if (node.status === 'rejected') return '×'
  return String(index + 1)
}

function elementStepStatus(node: WorkflowProcessStep) {
  if (node.status === 'done') return 'success'
  if (node.status === 'current') return 'process'
  if (node.status === 'returned') return 'warning'
  if (node.status === 'rejected') return 'error'
  return 'wait'
}
</script>

<template>
  <div class="workflow-detail-stack-steps-card">
    <div class="workflow-detail-stack-steps-scroll">
      <el-steps
        class="workflow-detail-stack-steps"
        :active="currentIndex"
        align-center
        finish-status="success"
        process-status="process"
      >
        <el-step
          v-for="(node, index) in nodes"
          :key="node.id"
          :status="elementStepStatus(node)"
        >
          <template #icon>
            <el-badge
              :value="node.roundCount"
              :hidden="!node.roundCount || node.roundCount <= 1"
              type="warning"
            >
              <button
                class="workflow-detail-stack-step-icon-button"
                :class="[node.status, { selected: selectedNodeId === node.id }]"
                type="button"
                @click.stop="$emit('select-node', node)"
              >
                {{ stepLabel(index, node) }}
              </button>
            </el-badge>
          </template>

          <template #title>
            <button
              class="workflow-detail-stack-step-title-button"
              :class="{ selected: selectedNodeId === node.id }"
              type="button"
              @click.stop="$emit('select-node', node)"
            >
              {{ node.name }}
            </button>
          </template>

          <template #description>
            <span class="workflow-detail-stack-step-desc" :class="node.status">
              {{ node.statusText }}
            </span>
          </template>
        </el-step>
      </el-steps>
    </div>
  </div>
</template>
