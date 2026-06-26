<template>
  <main class="page">
    <section class="process-card">
      <header class="process-header">
        <div>
          <div class="title-row">
            <h2>{{ process.moduleName }}</h2>
            <el-tag type="primary" effect="light">{{ process.statusText }}</el-tag>
            <el-tag type="warning" effect="light">第 {{ process.currentRoundNo }} 轮</el-tag>
          </div>
          <p class="sub-title">
            当前节点：{{ process.currentNode.name }} ｜ 当前处理人：{{ process.currentNode.assignee || '待分配' }} ｜ 最近更新：{{ process.updatedAt }}
          </p>
        </div>
        <div class="header-actions">
          <el-button>查看 BPMN</el-button>
          <el-button type="primary">处理当前任务</el-button>
        </div>
      </header>

      <ProcessRail
        :nodes="process.nodes"
        :selected-node-id="selectedNodeId"
        @select-node="handleSelectNode"
      />

      <section class="detail-grid">
        <CurrentNodeCard :node="selectedNode || process.currentNode" />
        <CurrentRoundTimeline :round="process.currentRound" />
      </section>

      <RoundHistory :rounds="process.rounds" />
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import ProcessRail from './components/ProcessRail.vue'
import CurrentNodeCard from './components/CurrentNodeCard.vue'
import CurrentRoundTimeline from './components/CurrentRoundTimeline.vue'
import RoundHistory from './components/RoundHistory.vue'

const selectedNodeId = ref('expert_review')

const process = ref({
  moduleName: '项目申报流程',
  status: 'processing',
  statusText: '进行中',
  currentRoundNo: 3,
  updatedAt: '2026-06-25 14:30',
  currentNode: {
    id: 'expert_review',
    name: '专家评审',
    status: 'current',
    statusText: '处理中',
    roleName: '专家',
    assignee: '张老师',
    arrivedAt: '2026-06-25 14:30',
    deadline: '2026-06-28 18:00',
    remark: '请对项目创新性、研究基础和经费合理性进行评审。',
    materialCount: 6,
    roundCount: 1
  },
  nodes: [
    {
      id: 'submit',
      name: '提交申请',
      status: 'done',
      statusText: '已完成',
      roleName: '项目负责人',
      assignee: '王同学',
      arrivedAt: '2026-06-20 09:10',
      deadline: '2026-06-20 18:00',
      remark: '项目负责人提交申报材料。',
      materialCount: 6,
      roundCount: 1
    },
    {
      id: 'dept_review',
      name: '二级单位审核',
      status: 'done',
      statusText: '已完成',
      roleName: '二级单位管理员',
      assignee: '刘老师',
      arrivedAt: '2026-06-21 10:25',
      deadline: '2026-06-22 18:00',
      remark: '已审核申请人信息和学院意见。',
      materialCount: 6,
      roundCount: 2
    },
    {
      id: 'science_review',
      name: '科技处审核',
      status: 'done',
      statusText: '已完成',
      roleName: '科技处',
      assignee: '李老师',
      arrivedAt: '2026-06-23 15:40',
      deadline: '2026-06-25 18:00',
      remark: '材料已补正，进入专家评审。',
      materialCount: 6,
      roundCount: 3
    },
    {
      id: 'expert_review',
      name: '专家评审',
      status: 'current',
      statusText: '处理中',
      roleName: '专家',
      assignee: '张老师',
      arrivedAt: '2026-06-25 14:30',
      deadline: '2026-06-28 18:00',
      remark: '请对项目创新性、研究基础和经费合理性进行评审。',
      materialCount: 6,
      roundCount: 1
    },
    {
      id: 'finance_record',
      name: '财务备案',
      status: 'waiting',
      statusText: '待进行',
      roleName: '财务处',
      assignee: null,
      arrivedAt: null,
      deadline: null,
      remark: '专家评审通过后进入财务备案。',
      materialCount: 0,
      roundCount: 0
    },
    {
      id: 'finish',
      name: '流程完成',
      status: 'waiting',
      statusText: '待进行',
      roleName: '系统',
      assignee: null,
      arrivedAt: null,
      deadline: null,
      remark: '所有节点完成后自动归档。',
      materialCount: 0,
      roundCount: 0
    }
  ],
  currentRound: {
    roundNo: 3,
    summary: '材料补正后重新流转，目前到达专家评审节点。',
    records: [
      {
        id: 301,
        nodeName: '项目负责人',
        action: 'resubmit',
        actionText: '重新提交',
        operatorName: '王同学',
        roleName: '项目负责人',
        createdAt: '2026-06-25 09:30',
        remark: '已补充预算明细表和合作单位说明。'
      },
      {
        id: 302,
        nodeName: '科技处审核',
        action: 'approve',
        actionText: '审核通过',
        operatorName: '李老师',
        roleName: '科技处',
        createdAt: '2026-06-25 10:20',
        remark: '材料已补正，进入专家评审。'
      },
      {
        id: 303,
        nodeName: '专家评审',
        action: 'arrive',
        actionText: '任务到达',
        operatorName: '系统',
        roleName: '系统',
        createdAt: '2026-06-25 14:30',
        remark: '系统已分配给专家张老师。'
      }
    ]
  },
  rounds: [
    {
      roundNo: 2,
      result: 'returned',
      resultText: '已退回',
      summary: '科技处退回：预算材料不完整。',
      startedAt: '2026-06-23 15:40',
      endedAt: '2026-06-24 18:10',
      records: [
        {
          id: 201,
          nodeName: '二级单位审核',
          action: 'approve',
          actionText: '审核通过',
          operatorName: '刘老师',
          roleName: '二级单位管理员',
          createdAt: '2026-06-23 15:40',
          remark: '学院意见已补充。'
        },
        {
          id: 202,
          nodeName: '科技处审核',
          action: 'return',
          actionText: '退回补正',
          operatorName: '李老师',
          roleName: '科技处',
          createdAt: '2026-06-24 18:10',
          remark: '预算明细表缺少设备费用说明，请补充后重新提交。'
        }
      ]
    },
    {
      roundNo: 1,
      result: 'returned',
      resultText: '已退回',
      summary: '二级单位退回：缺少申报附件。',
      startedAt: '2026-06-20 09:10',
      endedAt: '2026-06-21 16:35',
      records: [
        {
          id: 101,
          nodeName: '提交申请',
          action: 'submit',
          actionText: '提交申请',
          operatorName: '王同学',
          roleName: '项目负责人',
          createdAt: '2026-06-20 09:10',
          remark: '首次提交项目申报材料。'
        },
        {
          id: 102,
          nodeName: '二级单位审核',
          action: 'return',
          actionText: '退回补正',
          operatorName: '刘老师',
          roleName: '二级单位管理员',
          createdAt: '2026-06-21 16:35',
          remark: '缺少合作单位盖章附件，请补充。'
        }
      ]
    }
  ]
})

const selectedNode = computed(() => {
  return process.value.nodes.find((item) => item.id === selectedNodeId.value)
})

function handleSelectNode(node) {
  selectedNodeId.value = node.id
}
</script>
