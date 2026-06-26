<template>
  <el-card class="panel-card" shadow="never">
    <template #header>
      <div class="panel-header">
        <span>节点信息</span>
        <el-tag :type="tagType(node.status)">{{ node.statusText }}</el-tag>
      </div>
    </template>

    <el-descriptions :column="1" border>
      <el-descriptions-item label="节点名称">
        {{ node.name }}
      </el-descriptions-item>
      <el-descriptions-item label="处理角色">
        {{ node.roleName || '暂无' }}
      </el-descriptions-item>
      <el-descriptions-item label="当前处理人">
        {{ node.assignee || '待分配' }}
      </el-descriptions-item>
      <el-descriptions-item label="到达时间">
        {{ node.arrivedAt || '暂未到达' }}
      </el-descriptions-item>
      <el-descriptions-item label="截止时间">
        <el-tag v-if="node.deadline" type="warning" effect="plain">
          {{ node.deadline }}
        </el-tag>
        <span v-else>暂无</span>
      </el-descriptions-item>
      <el-descriptions-item label="材料数量">
        {{ node.materialCount || 0 }} 份
      </el-descriptions-item>
      <el-descriptions-item label="节点说明">
        {{ node.remark || '暂无说明' }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="button-row">
      <el-button type="primary" :disabled="node.status !== 'current'">处理任务</el-button>
      <el-button>查看材料</el-button>
      <el-button type="warning" plain :disabled="node.status !== 'current'">退回补正</el-button>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  node: {
    type: Object,
    required: true
  }
})

function tagType(status) {
  const map = {
    done: 'success',
    current: 'primary',
    waiting: 'info',
    returned: 'warning',
    rejected: 'danger'
  }
  return map[status] || 'info'
}
</script>
