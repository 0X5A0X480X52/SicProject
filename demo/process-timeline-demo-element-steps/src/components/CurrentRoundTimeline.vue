<template>
  <el-card class="panel-card" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <span>本轮流转记录</span>
          <p class="panel-desc">{{ round.summary }}</p>
        </div>
        <el-tag>第 {{ round.roundNo }} 轮</el-tag>
      </div>
    </template>

    <el-timeline class="flow-timeline">
      <el-timeline-item
        v-for="record in round.records"
        :key="record.id"
        :timestamp="record.createdAt"
        placement="top"
        :type="recordType(record.action)"
      >
        <div class="record-card">
          <div class="record-title">
            {{ record.nodeName }} · {{ record.actionText }}
          </div>
          <div class="record-meta">
            {{ record.operatorName }} / {{ record.roleName }}
          </div>
          <div v-if="record.remark" class="record-remark">
            {{ record.remark }}
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </el-card>
</template>

<script setup>
defineProps({
  round: {
    type: Object,
    required: true
  }
})

function recordType(action) {
  const map = {
    submit: 'primary',
    resubmit: 'primary',
    approve: 'success',
    return: 'warning',
    reject: 'danger',
    arrive: 'info',
    transfer: 'info'
  }
  return map[action] || 'info'
}
</script>
