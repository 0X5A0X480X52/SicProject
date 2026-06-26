<template>
  <section class="rail-wrap">
    <div class="rail-scroll">
      <div
        v-for="(node, index) in nodes"
        :key="node.id"
        class="rail-item"
        :class="{ selected: selectedNodeId === node.id }"
        @click="$emit('select-node', node)"
      >
        <div
          v-if="index !== 0"
          class="rail-line"
          :class="{ finished: isLineFinished(index) }"
        />

        <div class="node-box">
          <el-badge
            :value="node.roundCount"
            :hidden="!node.roundCount || node.roundCount <= 1"
            class="node-badge"
          >
            <div class="node-dot" :class="node.status">
              <span v-if="node.status === 'done'">✓</span>
              <span v-else-if="node.status === 'returned'">↩</span>
              <span v-else-if="node.status === 'rejected'">×</span>
              <span v-else>{{ index + 1 }}</span>
            </div>
          </el-badge>

          <div class="node-name">{{ node.name }}</div>
          <div class="node-status" :class="node.status">{{ node.statusText }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
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

function isLineFinished(index) {
  const prev = props.nodes[index - 1]
  const current = props.nodes[index]
  return prev?.status === 'done' && ['done', 'current'].includes(current?.status)
}
</script>
