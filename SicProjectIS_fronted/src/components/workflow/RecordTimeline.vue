<script setup lang="ts">
import type { PropType } from 'vue'

export interface WorkflowDisplayRecord {
  id: string | number
  time?: string
  nodeName: string
  action: string
  actionText: string
  operator?: string
  role?: string
  remark?: string
}

defineProps({
  records: { type: Array as PropType<WorkflowDisplayRecord[]>, default: () => [] },
})

function recordType(action: string) {
  const text = action.toUpperCase()
  if (text.includes('APPROVE') || text.includes('PASS') || text.includes('DONE')) return 'success'
  if (text.includes('RETURN') || text.includes('退回')) return 'warning'
  if (text.includes('REJECT') || text.includes('FAIL') || text.includes('驳回')) return 'danger'
  if (text.includes('START') || text.includes('SUBMIT')) return 'primary'
  return 'info'
}
</script>

<template>
  <el-timeline class="workflow-detail-stack-record-timeline">
    <el-timeline-item
      v-for="record in records"
      :key="record.id"
      :timestamp="record.time"
      :type="recordType(record.action)"
      placement="top"
    >
      <div class="workflow-detail-stack-record-card">
        <div class="workflow-detail-stack-record-title-row">
          <strong>{{ record.nodeName }}</strong>
          <el-tag size="small" :type="recordType(record.action)" effect="light">
            {{ record.actionText }}
          </el-tag>
        </div>
        <div class="workflow-detail-stack-record-meta">
          {{ record.operator || '系统记录' }} / {{ record.role || '-' }}
        </div>
        <p v-if="record.remark">{{ record.remark }}</p>
      </div>
    </el-timeline-item>
  </el-timeline>
</template>
