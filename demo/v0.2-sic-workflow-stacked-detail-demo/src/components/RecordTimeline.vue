<template>
  <el-timeline class="record-timeline">
    <el-timeline-item
      v-for="record in records"
      :key="record.id"
      :timestamp="record.time"
      :type="recordType(record.action)"
      placement="top"
    >
      <div class="record-card">
        <div class="record-title-row">
          <strong>{{ record.nodeName }}</strong>
          <el-tag size="small" :type="recordType(record.action)" effect="light">
            {{ record.actionText }}
          </el-tag>
        </div>
        <div class="record-meta">{{ record.operator }} / {{ record.role }}</div>
        <p v-if="record.remark">{{ record.remark }}</p>
      </div>
    </el-timeline-item>
  </el-timeline>
</template>

<script setup>
defineProps({
  records: { type: Array, default: () => [] }
})

const recordType = (action) => {
  const map = {
    start: 'primary',
    submit: 'primary',
    approve: 'success',
    return: 'warning',
    reject: 'danger',
    arrive: 'info',
    transfer: 'info'
  }
  return map[action] || 'info'
}
</script>
