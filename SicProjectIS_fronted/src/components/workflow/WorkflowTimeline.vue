<script setup lang="ts">
import type { ModuleStateRecord } from '../../types/nodeForms'

defineProps<{ history: ModuleStateRecord[] }>()

function itemType(result?: string | null) {
  if (!result) return 'primary'
  const upper = result.toUpperCase()
  if (upper.includes('REJECT') || upper.includes('RETURN')) return 'warning'
  if (upper.includes('ERROR') || upper.includes('FAIL')) return 'danger'
  return 'success'
}
</script>

<template>
  <el-card class="workflow-card" shadow="never">
    <template #header>流程时间线</template>
    <el-empty v-if="!history.length" description="暂无状态记录" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="record in history"
        :key="record.stateRecordId"
        :timestamp="record.createdAt"
        :type="itemType(record.result)"
      >
        <div class="timeline-title">{{ record.toState || record.toNodeId }}</div>
        <div class="timeline-meta">
          #{{ record.seq }} · 第 {{ record.roundNo || 1 }} 轮 · {{ record.eventType }}
        </div>
        <p v-if="record.summary" class="timeline-summary">{{ record.summary }}</p>
      </el-timeline-item>
    </el-timeline>
  </el-card>
</template>
