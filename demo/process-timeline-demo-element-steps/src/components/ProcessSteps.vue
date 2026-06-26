<template>
  <section class="steps-wrap">
    <div class="steps-scroll">
      <el-steps
        class="component-steps"
        :active="activeIndex"
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
              class="step-badge"
            >
              <button
                type="button"
                class="step-icon-btn"
                :class="[node.status, { selected: selectedNodeId === node.id }]"
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
              type="button"
              class="step-title-btn"
              :class="{ selected: selectedNodeId === node.id }"
              @click.stop="$emit('select-node', node)"
            >
              {{ node.name }}
            </button>
          </template>

          <template #description>
            <span class="step-desc" :class="node.status">
              {{ node.statusText }}
            </span>
          </template>
        </el-step>
      </el-steps>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  nodes: {
    type: Array,
    required: true
  },
  selectedNodeId: {
    type: String,
    default: ''
  }
})

defineEmits(['select-node'])

const activeIndex = computed(() => {
  const index = props.nodes.findIndex((node) => node.status === 'current')
  return index >= 0 ? index : 0
})

function elementStepStatus(node) {
  const map = {
    done: 'success',
    current: 'process',
    waiting: 'wait',
    rejected: 'error',
    returned: 'finish',
    skipped: 'wait'
  }
  return map[node.status] || 'wait'
}
</script>
