<script setup lang="ts">
import { computed } from 'vue'
import { eventLabel, resultLabel, roleLabel, stateLabel } from '../../utils/displayLabels'
import type { AvailableTransition, RuntimeViewResponse } from '../../types/nodeForms'

const props = defineProps<{ view: RuntimeViewResponse }>()
const emit = defineEmits<{ action: [transition: AvailableTransition] }>()

const context = computed(() => props.view.context)
const canOperate = computed(() => props.view.canOperate && props.view.availableTransitions.length > 0)
</script>

<template>
  <el-card class="workflow-card" shadow="never">
    <template #header>当前办理节点</template>
    <el-alert
      v-if="canOperate"
      title="当前账号可办理该节点"
      type="success"
      show-icon
      :closable="false"
    />
    <el-alert
      v-else
      title="当前节点只读或暂无可执行动作"
      type="info"
      show-icon
      :closable="false"
    />

    <el-descriptions class="node-descriptions" :column="1" border size="small">
      <el-descriptions-item label="节点">{{ context.currentNodeName || context.currentNodeId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ stateLabel(context.currentState) }}</el-descriptions-item>
      <el-descriptions-item label="候选角色">{{ roleLabel(context.currentCandidateRoleCode) }}</el-descriptions-item>
      <el-descriptions-item label="轮次">第 {{ context.currentRoundNo || 1 }} 轮</el-descriptions-item>
      <el-descriptions-item label="序号">{{ context.currentSeq ?? '-' }}</el-descriptions-item>
    </el-descriptions>

    <div class="action-list">
      <el-button
        v-for="transition in view.availableTransitions"
        :key="transition.transitionId || transition.eventType"
        type="primary"
        plain
        :disabled="!canOperate"
        @click="emit('action', transition)"
      >
        {{ resultLabel(transition.result) !== '-' ? resultLabel(transition.result) : eventLabel(transition.eventType) }}
      </el-button>
    </div>
  </el-card>
</template>

