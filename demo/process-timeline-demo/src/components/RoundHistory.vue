<template>
  <el-card class="history-card" shadow="never">
    <template #header>
      <div class="panel-header">
        <span>历史轮次</span>
        <el-tag type="info">共 {{ rounds.length }} 轮历史</el-tag>
      </div>
    </template>

    <el-empty v-if="rounds.length === 0" description="暂无历史轮次" />

    <el-collapse v-else accordion>
      <el-collapse-item
        v-for="round in rounds"
        :key="round.roundNo"
        :name="round.roundNo"
      >
        <template #title>
          <div class="round-title">
            <span class="round-no">第 {{ round.roundNo }} 轮</span>
            <el-tag size="small" :type="round.result === 'passed' ? 'success' : 'warning'">
              {{ round.resultText }}
            </el-tag>
            <span class="round-summary">{{ round.summary }}</span>
            <span class="round-time">{{ round.startedAt }} - {{ round.endedAt }}</span>
          </div>
        </template>

        <el-timeline class="history-timeline">
          <el-timeline-item
            v-for="record in round.records"
            :key="record.id"
            :timestamp="record.createdAt"
            placement="top"
            :type="recordType(record.action)"
          >
            <div class="record-card compact">
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
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup>
defineProps({
  rounds: {
    type: Array,
    default: () => []
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
