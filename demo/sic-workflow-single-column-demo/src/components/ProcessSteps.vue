<template>
  <div class="process-steps-card">
    <div class="steps-scroll">
      <el-steps
        class="workflow-steps"
        :active="activeIndex"
        align-center
        finish-status="success"
        process-status="process"
      >
        <el-step
          v-for="(node, index) in nodes"
          :key="node.id"
          :status="elementStatus(node)"
        >
          <template #icon>
            <el-badge
              :value="node.roundCount"
              :hidden="!node.roundCount || node.roundCount <= 1"
              type="warning"
            >
              <button
                class="step-icon-button"
                :class="[node.status, { selected: selectedNodeId === node.id }]"
                type="button"
                @click.stop="$emit('select-node', node)"
              >
                <span v-if="node.status === 'done'">✓</span>
                <span v-else-if="node.status === 'returned'">↩</span>
                <span v-else-if="node.status === 'rejected'">×</span>
                <span v-else>{{ index + 1 }}</span>
              </button>
            </el-badge>
          </template>

          <template #title>
            <button
              class="step-title-button"
              :class="{ selected: selectedNodeId === node.id }"
              type="button"
              @click.stop="$emit('select-node', node)"
            >
              {{ node.name }}
            </button>
          </template>

          <template #description>
            <span class="step-desc" :class="node.status">{{ node.statusText }}</span>
          </template>
        </el-step>
      </el-steps>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  nodes: { type: Array, required: true },
  selectedNodeId: { type: String, default: '' }
})

defineEmits(['select-node'])

const activeIndex = computed(() => {
  const index = props.nodes.findIndex((node) => node.status === 'current')
  return index >= 0 ? index : 0
})

const elementStatus = (node) => {
  if (node.status === 'done') return 'success'
  if (node.status === 'current') return 'process'
  if (node.status === 'returned') return 'warning'
  if (node.status === 'rejected') return 'error'
  return 'wait'
}
</script>
