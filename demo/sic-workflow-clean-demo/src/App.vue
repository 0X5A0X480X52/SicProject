<template>
  <main class="clean-page">
    <section class="hero-card">
      <div class="hero-main">
        <el-button link class="back-btn">返回工作台</el-button>
        <div class="title-line">
          <h1>{{ vm.project.name }}</h1>
          <el-tag :type="statusTagType(vm.module.status)" effect="dark">
            {{ vm.module.statusText }}
          </el-tag>
          <el-tag effect="plain">{{ vm.module.moduleTypeText }}</el-tag>
        </div>
        <p class="hero-subtitle">
          {{ vm.module.moduleName }} · 当前节点：{{ vm.module.currentNodeName }} · 最近更新：{{ vm.module.updatedAt }}
        </p>
      </div>

      <div class="hero-actions">
        <el-button>刷新</el-button>
        <el-button>查看 BPMN</el-button>
        <el-button type="primary" :disabled="!vm.permission.canHandle">
          {{ vm.permission.canHandle ? '办理当前任务' : '只读查看' }}
        </el-button>
      </div>
    </section>

    <section class="summary-strip">
      <div v-for="item in summaryItems" :key="item.label" class="summary-item">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </section>

    <section class="main-grid">
      <section class="content-column">
        <el-card class="clean-card timeline-card" shadow="never">
          <template #header>
            <div class="card-title-row">
              <div>
                <strong>流程办理时间线</strong>
                <p>点击节点查看该步骤的办理结果与时间</p>
              </div>
              <el-tag size="small" type="info">{{ vm.nodes.length }} 步</el-tag>
            </div>
          </template>

          <ProcessSteps
            :nodes="vm.nodes"
            :selected-node-id="selectedNodeId"
            @select-node="handleSelectNode"
          />

          <div class="selected-step-panel">
            <div>
              <span class="eyebrow">选中节点</span>
              <h3>{{ selectedNode.name }}</h3>
              <p>{{ selectedNode.eventText || selectedNode.remark || '暂无补充说明' }}</p>
            </div>
            <div class="step-meta-mini">
              <div>
                <span>办理状态</span>
                <el-tag :type="stepTagType(selectedNode.status)" size="small">
                  {{ selectedNode.statusText }}
                </el-tag>
              </div>
              <div>
                <span>办理时间</span>
                <strong>{{ selectedNode.finishedAt || selectedNode.arrivedAt || '暂未到达' }}</strong>
              </div>
              <div>
                <span>办理人</span>
                <strong>{{ selectedNode.assignee || '系统/待分配' }}</strong>
              </div>
            </div>
          </div>
        </el-card>

        <section class="two-col-grid">
          <el-card class="clean-card" shadow="never">
            <template #header>
              <div class="card-title-row compact">
                <strong>当前办理任务</strong>
                <el-tag :type="vm.permission.canHandle ? 'primary' : 'info'" size="small">
                  {{ vm.permission.canHandle ? '可办理' : '只读' }}
                </el-tag>
              </div>
            </template>

            <div class="task-status">
              <div class="status-dot" :class="vm.module.status"></div>
              <div>
                <h3>{{ vm.currentTask.title }}</h3>
                <p>{{ vm.currentTask.notice }}</p>
              </div>
            </div>

            <div class="meta-grid slim">
              <div>
                <span>责任角色</span>
                <strong>{{ vm.currentTask.role }}</strong>
              </div>
              <div>
                <span>责任人</span>
                <strong>{{ vm.currentTask.assignee }}</strong>
              </div>
              <div>
                <span>办理模式</span>
                <strong>{{ vm.currentTask.mode }}</strong>
              </div>
              <div>
                <span>当前轮次</span>
                <strong>第 {{ vm.module.roundNo }} 轮</strong>
              </div>
            </div>

            <div class="button-row">
              <el-button type="primary" :disabled="!vm.permission.canHandle">处理任务</el-button>
              <el-button :disabled="vm.materials.length === 0">查看材料</el-button>
            </div>
          </el-card>

          <el-card class="clean-card" shadow="never">
            <template #header>
              <div class="card-title-row compact">
                <strong>关键材料</strong>
                <el-button link type="primary">全部材料</el-button>
              </div>
            </template>

            <div v-if="vm.materials.length" class="material-list">
              <div v-for="item in vm.materials" :key="item.id" class="material-item">
                <div>
                  <strong>{{ item.name }}</strong>
                  <span>{{ item.version }} · {{ item.updatedAt }}</span>
                </div>
                <el-tag :type="item.status === '已提交' ? 'success' : 'warning'" size="small">
                  {{ item.status }}
                </el-tag>
              </div>
            </div>
            <el-empty v-else description="当前节点暂无材料要求" :image-size="72" />
          </el-card>
        </section>

        <el-card class="clean-card" shadow="never">
          <template #header>
            <div class="card-title-row compact">
              <strong>最近流转记录</strong>
              <el-button link type="primary">查看全部</el-button>
            </div>
          </template>

          <el-timeline class="recent-timeline">
            <el-timeline-item
              v-for="record in vm.recentRecords"
              :key="record.id"
              :timestamp="record.time"
              placement="top"
              :type="record.type"
            >
              <div class="record-line">
                <strong>{{ record.title }}</strong>
                <span>{{ record.summary }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </section>

      <aside class="side-column">
        <el-card class="clean-card project-card" shadow="never">
          <template #header>
            <div class="card-title-row compact">
              <strong>项目摘要</strong>
              <el-tag size="small" type="info">{{ vm.module.moduleType }}</el-tag>
            </div>
          </template>
          <div class="project-title">{{ vm.project.name }}</div>
          <div class="kv-list">
            <div><span>项目编号</span><strong>{{ vm.project.code }}</strong></div>
            <div><span>负责人</span><strong>{{ vm.project.leader }}</strong></div>
            <div><span>所属部门</span><strong>{{ vm.project.department }}</strong></div>
            <div><span>生命周期</span><strong>{{ vm.project.lifecycle }}</strong></div>
          </div>
        </el-card>

        <el-card class="clean-card" shadow="never">
          <template #header>
            <div class="card-title-row compact">
              <strong>辅助查看</strong>
            </div>
          </template>
          <el-collapse class="helper-collapse">
            <el-collapse-item title="BPMN 流程图" name="bpmn">
              <div class="bpmn-placeholder">
                <span>BPMN</span>
                <p>默认折叠，必要时展开查看完整泳道与分支。</p>
              </div>
            </el-collapse-item>
            <el-collapse-item title="历史退回/补正" name="history">
              <div class="history-mini" v-for="round in vm.rounds" :key="round.roundNo">
                <strong>第 {{ round.roundNo }} 轮</strong>
                <span>{{ round.summary }}</span>
              </div>
              <el-empty v-if="vm.rounds.length === 0" description="暂无退回记录" :image-size="60" />
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import ProcessSteps from './components/ProcessSteps.vue'

const selectedNodeId = ref('accepted_end')

const vm = ref({
  project: {
    name: 'Demo02',
    code: 'APP-20260625165339-1',
    leader: 'System Administrator',
    department: '-',
    lifecycle: 'ACCEPTANCE_FINISHED'
  },
  module: {
    moduleName: '科研项目结题验收流程',
    moduleType: 'ACCEPTANCE',
    moduleTypeText: '结题验收',
    status: 'completed',
    statusText: '已完成',
    currentNodeName: '结题验收通过',
    roundNo: 1,
    seq: 14,
    updatedAt: '2026-06-25 19:40:19'
  },
  permission: {
    canHandle: false
  },
  currentTask: {
    title: '流程已结束',
    notice: '当前流程已完成，页面仅保留归档查看、材料查看和流程追踪能力。',
    role: '系统',
    assignee: '系统',
    mode: 'SYSTEM_AUTO'
  },
  nodes: [
    { id: 'publish_notice', name: '发布通知', status: 'done', statusText: '已完成', assignee: '科技处', finishedAt: '2026-06-25 18:31:01', eventText: 'Module started' },
    { id: 'notify_leader', name: '通知负责人', status: 'done', statusText: '已完成', assignee: '二级单位', finishedAt: '2026-06-25 19:25:46', eventText: 'ACCEPTANCE_NOTICE_PUBLISHED' },
    { id: 'finance_settlement', name: '经费决算', status: 'done', statusText: '已完成', assignee: '财务处', finishedAt: '2026-06-25 19:25:56', eventText: 'DEPT_LEADER_NOTIFIED' },
    { id: 'submit_materials', name: '填报材料', status: 'done', statusText: '已完成', assignee: '项目负责人', finishedAt: '2026-06-25 19:38:24', eventText: 'FINANCIAL_SETTLEMENT_COMPLETED' },
    { id: 'dept_review', name: '二级单位审核', status: 'done', statusText: '已完成', assignee: '二级单位', finishedAt: '2026-06-25 19:38:39', eventText: 'ACCEPTANCE_CONFIRMED_SUBMIT' },
    { id: 'science_review', name: '科技处审核', status: 'done', statusText: '已完成', assignee: '科技处', finishedAt: '2026-06-25 19:38:50', eventText: 'DEPT_ACCEPTANCE_REVIEW_FINISHED' },
    { id: 'authority_review', name: '主管部门审核', status: 'done', statusText: '已完成', assignee: '主管部门', finishedAt: '2026-06-25 19:38:55', eventText: 'SCIENCE_ACCEPTANCE_REVIEW_FINISHED' },
    { id: 'sign_seal', name: '签字盖章', status: 'done', statusText: '已完成', assignee: '科技处', finishedAt: '2026-06-25 19:39:02', eventText: 'AUTHORITY_ACCEPTANCE_REVIEW_FINISHED' },
    { id: 'final_materials', name: '提交终稿', status: 'done', statusText: '已完成', assignee: '项目负责人', finishedAt: '2026-06-25 19:39:10', eventText: 'ACCEPTANCE_SIGN_SEAL_COMPLETED' },
    { id: 'expert_assign', name: '专家分配', status: 'done', statusText: '已完成', assignee: '科技处', finishedAt: '2026-06-25 19:39:19', eventText: 'ACCEPTANCE_FINAL_MATERIALS_SUBMITTED' },
    { id: 'expert_review', name: '专家评审', status: 'done', statusText: '已完成', assignee: '评审专家', finishedAt: '2026-06-25 19:39:34', eventText: 'EXPERT_ACCEPTANCE_ASSIGNED' },
    { id: 'expert_summary', name: '专家汇总', status: 'done', statusText: '已完成', assignee: '科技处', finishedAt: '2026-06-25 19:39:55', eventText: 'EXPERT_ACCEPTANCE_REVIEW_SUBMITTED' },
    { id: 'issue_certificate', name: '发放证书', status: 'done', statusText: '已完成', assignee: '科技处', finishedAt: '2026-06-25 19:40:04', eventText: 'EXPERT_ACCEPTANCE_REVIEW_FINISHED' },
    { id: 'accepted_end', name: '验收通过', status: 'done', statusText: '已完成', assignee: '系统', finishedAt: '2026-06-25 19:40:19', eventText: 'ACCEPTANCE_CERTIFICATE_ISSUED' }
  ],
  materials: [
    { id: 1, name: '结题验收申请书', version: 'v3', updatedAt: '2026-06-25 19:38', status: '已提交' },
    { id: 2, name: '经费决算表', version: 'v1', updatedAt: '2026-06-25 19:26', status: '已提交' },
    { id: 3, name: '验收证书', version: 'v1', updatedAt: '2026-06-25 19:40', status: '已归档' }
  ],
  recentRecords: [
    { id: 14, title: '验收通过 · PROCESS_COMPLETED', summary: '流程结束并进入归档查看状态', time: '2026-06-25 19:40:19', type: 'success' },
    { id: 13, title: '发放证书 · REVIEW_FINISHED', summary: '专家评审结果确认完成', time: '2026-06-25 19:40:04', type: 'success' },
    { id: 12, title: '专家汇总 · REVIEW_SUBMITTED', summary: '专家意见已提交汇总', time: '2026-06-25 19:39:55', type: 'primary' },
    { id: 11, title: '专家评审 · ASSIGNED', summary: '评审任务已分配', time: '2026-06-25 19:39:34', type: 'info' }
  ],
  rounds: []
})

const selectedNode = computed(() => {
  return vm.value.nodes.find((node) => node.id === selectedNodeId.value) || vm.value.nodes[0]
})

const summaryItems = computed(() => [
  { label: '项目编号', value: vm.value.project.code },
  { label: '负责人', value: vm.value.project.leader },
  { label: '当前状态', value: vm.value.module.statusText },
  { label: '当前序号', value: `${vm.value.module.seq} / ${vm.value.nodes.length}` },
  { label: '当前轮次', value: `第 ${vm.value.module.roundNo} 轮` }
])

function handleSelectNode(node) {
  selectedNodeId.value = node.id
}

function statusTagType(status) {
  const map = { processing: 'primary', completed: 'success', rejected: 'danger' }
  return map[status] || 'info'
}

function stepTagType(status) {
  const map = { done: 'success', current: 'primary', waiting: 'info', returned: 'warning', rejected: 'danger' }
  return map[status] || 'info'
}
</script>
