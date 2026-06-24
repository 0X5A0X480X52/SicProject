<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentAdd, Plus, Refresh } from '@element-plus/icons-vue'
import AppShell from '../layouts/AppShell.vue'
import { listProjects, startProjectApplication } from '../api/projects'
import { startModuleInstance } from '../api/stateMachine'
import { listWorkflowWorkbenchItems } from '../api/workflowWorkbench'
import { useWorkflowEvents } from '../composables/useWorkflowEvents'
import { useAuthStore } from '../stores/auth'
import type { ProjectSummary, StartProjectApplicationRequest } from '../types/project'
import type { WorkflowWorkbenchItem } from '../types/workflow'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const starting = ref(false)
const applying = ref(false)
const startDialogOpen = ref(false)
const applicationDialogOpen = ref(false)
const items = ref<WorkflowWorkbenchItem[]>([])
const projects = ref<ProjectSummary[]>([])
const filters = reactive({ moduleType: 'ALL', keyword: '', status: 'ALL' })
const page = reactive({ current: 1, size: 10 })
const startForm = reactive<{ projectId: number | null; moduleType: string }>({
  projectId: null,
  moduleType: 'CONTRACT',
})
const applicationForm = reactive<StartProjectApplicationRequest>({
  projectCode: '',
  projectName: '',
  projectType: '',
  projectLevel: '',
  approvedAmount: null,
  startDate: null,
  endDate: null,
  applicationTitle: '',
  isLimitedProject: false,
  applicationSummary: '',
})

const moduleOptions = [
  { label: '全部业务', value: 'ALL' },
  { label: '项目申报', value: 'APPLICATION' },
  { label: '纵向合同', value: 'CONTRACT' },
  { label: '项目结题', value: 'ACCEPTANCE' },
]

const startModuleOptions = [
  { label: '纵向合同', value: 'CONTRACT' },
  { label: '项目结题', value: 'ACCEPTANCE' },
]

const moduleLabels: Record<string, string> = {
  APPLICATION: '项目申报',
  CONTRACT: '纵向合同',
  ACCEPTANCE: '项目结题',
}

const canStartWorkflow = computed(() => {
  const roles = auth.user?.roleCodes || []
  return roles.includes('SCIENCE_ADMIN') || roles.includes('SYSTEM_ADMIN')
})

const filteredItems = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return items.value.filter((item) => {
    if (filters.moduleType !== 'ALL' && item.moduleType !== filters.moduleType) return false
    if (filters.status === 'TODO' && !item.todo) return false
    if (filters.status === 'FINISHED' && !item.finished) return false
    if (filters.status === 'RUNNING' && item.finished) return false
    if (!keyword) return true
    return [item.projectCode, item.projectName, item.currentNodeName, item.currentState]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword))
  })
})

const pagedItems = computed(() => {
  const start = (page.current - 1) * page.size
  return filteredItems.value.slice(start, start + page.size)
})

const stats = computed(() => [
  { label: '全部事项', value: items.value.length, type: '' },
  { label: '我的待办', value: items.value.filter((item) => item.todo).length, type: 'warning' },
  { label: '运行中', value: items.value.filter((item) => !item.finished).length, type: 'primary' },
  { label: '已完成', value: items.value.filter((item) => item.finished).length, type: 'success' },
])

function moduleLabel(moduleType: string) {
  return moduleLabels[moduleType] || moduleType
}

function statusTag(item: WorkflowWorkbenchItem) {
  if (item.finished) return { label: '已完成', type: 'success' }
  if (item.todo) return { label: '待办理', type: 'warning' }
  return { label: '运行中', type: 'primary' }
}

async function loadItems(showMessage = false) {
  loading.value = true
  try {
    items.value = await listWorkflowWorkbenchItems()
    if (showMessage) ElMessage.success('业务事项已刷新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '业务事项加载失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    projects.value = await listProjects()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '项目列表加载失败')
  }
}

function openApplicationDialog() {
  applicationDialogOpen.value = true
}

function openStartDialog() {
  if (!canStartWorkflow.value) {
    ElMessage.warning('只有科技处管理员可以发起合同或结题流程')
    return
  }
  startDialogOpen.value = true
}

function cleanApplicationRequest(): StartProjectApplicationRequest {
  return {
    ...applicationForm,
    projectCode: applicationForm.projectCode || null,
    projectType: applicationForm.projectType || null,
    projectLevel: applicationForm.projectLevel || null,
    approvedAmount: applicationForm.approvedAmount ?? null,
    startDate: applicationForm.startDate || null,
    endDate: applicationForm.endDate || null,
    applicationTitle: applicationForm.applicationTitle || applicationForm.projectName,
    applicationSummary: applicationForm.applicationSummary || null,
  }
}

async function submitApplicationStart() {
  if (!applicationForm.projectName?.trim()) {
    ElMessage.warning('请填写项目名称')
    return
  }
  applying.value = true
  try {
    const response = await startProjectApplication(cleanApplicationRequest())
    applicationDialogOpen.value = false
    ElMessage.success('项目申请已创建')
    await loadItems()
    await loadProjects()
    router.push({ name: 'workflow-detail', params: { moduleInstanceId: response.moduleInstanceId } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '项目申请创建失败')
  } finally {
    applying.value = false
  }
}

async function submitStart() {
  if (!startForm.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  starting.value = true
  try {
    const response = await startModuleInstance(startForm.projectId, startForm.moduleType)
    startDialogOpen.value = false
    ElMessage.success('流程已发起')
    await loadItems()
    router.push({ name: 'workflow-detail', params: { moduleInstanceId: response.stateRecord.moduleInstanceId } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '流程发起失败')
  } finally {
    starting.value = false
  }
}

function openDetail(item: WorkflowWorkbenchItem) {
  router.push({ name: 'workflow-detail', params: { moduleInstanceId: item.moduleInstanceId } })
}

const workflowEvents = useWorkflowEvents(() => loadItems())

onMounted(() => {
  loadItems()
  loadProjects()
  workflowEvents.connect()
})
</script>

<template>
  <AppShell>
    <section class="workflow-page-head">
      <div>
        <p class="eyebrow">Workflow</p>
        <h2>流程办理工作台</h2>
        <p class="helper-text">按后端运行时视图展示业务事项、当前节点和待办状态。</p>
      </div>
      <div class="workflow-head-actions">
        <el-button type="primary" :icon="DocumentAdd" @click="openApplicationDialog">
          发起项目申请
        </el-button>
        <el-button :icon="Plus" :disabled="!canStartWorkflow" @click="openStartDialog">
          发起合同/结题
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadItems(true)">刷新</el-button>
      </div>
    </section>

    <section class="workflow-stat-grid">
      <el-card v-for="card in stats" :key="card.label" shadow="never">
        <div class="stat-card-inline">
          <span>{{ card.label }}</span>
          <el-tag :type="card.type as any" effect="light">{{ card.value }}</el-tag>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="workflow-card">
      <el-form class="workflow-filter-form" :inline="true">
        <el-form-item label="业务模块">
          <el-select v-model="filters.moduleType" style="width: 160px">
            <el-option v-for="option in moduleOptions" :key="option.value" v-bind="option" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" style="width: 140px">
            <el-option label="全部" value="ALL" />
            <el-option label="我的待办" value="TODO" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="FINISHED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" clearable placeholder="项目 / 节点 / 状态" style="width: 260px" />
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="pagedItems" border stripe empty-text="暂无业务事项">
        <el-table-column prop="projectCode" label="项目编号" min-width="130" />
        <el-table-column prop="projectName" label="项目名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="业务模块" width="120">
          <template #default="{ row }">
            <el-tag>{{ moduleLabel(row.moduleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.currentNodeName || row.currentNodeId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row).type">{{ statusTag(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="候选角色" width="150" prop="candidateRoleCode" />
        <el-table-column label="轮次" width="80" align="center">
          <template #default="{ row }">{{ row.currentRoundNo || 1 }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180" prop="lastTransitionTime" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">进入办理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="workflow-pagination">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          background
          layout="total, sizes, prev, pager, next"
          :total="filteredItems.length"
          :page-sizes="[10, 20, 50]"
        />
      </div>
    </el-card>

    <el-dialog v-model="applicationDialogOpen" title="发起项目申请" width="680px">
      <el-form label-width="110px">
        <el-form-item label="项目名称" required>
          <el-input v-model="applicationForm.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目编号">
          <el-input v-model="applicationForm.projectCode" placeholder="留空则自动生成" />
        </el-form-item>
        <el-form-item label="申请标题">
          <el-input v-model="applicationForm.applicationTitle" placeholder="默认使用项目名称" />
        </el-form-item>
        <el-form-item label="项目类型">
          <el-input v-model="applicationForm.projectType" placeholder="如纵向项目、校级项目" />
        </el-form-item>
        <el-form-item label="项目级别">
          <el-input v-model="applicationForm.projectLevel" placeholder="如国家级、省部级、校级" />
        </el-form-item>
        <el-form-item label="起止时间">
          <div class="workflow-inline-fields">
            <el-date-picker v-model="applicationForm.startDate" value-format="YYYY-MM-DD" type="date" placeholder="开始日期" />
            <el-date-picker v-model="applicationForm.endDate" value-format="YYYY-MM-DD" type="date" placeholder="结束日期" />
          </div>
        </el-form-item>
        <el-form-item label="是否限项">
          <el-switch v-model="applicationForm.isLimitedProject" />
        </el-form-item>
        <el-form-item label="申请摘要">
          <el-input v-model="applicationForm.applicationSummary" type="textarea" :rows="4" placeholder="简要说明项目内容" />
        </el-form-item>
        <el-alert
          title="提交后系统会创建项目、绑定当前用户为负责人，并进入项目申请填报节点。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button @click="applicationDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApplicationStart">创建并进入填报</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="startDialogOpen" title="发起合同/结题流程" width="520px">
      <el-form label-width="96px">
        <el-form-item label="项目">
          <el-select v-model="startForm.projectId" filterable clearable placeholder="请选择已有项目" style="width: 100%">
            <el-option
              v-for="project in projects"
              :key="project.projectId"
              :label="`${project.projectCode} · ${project.projectName}`"
              :value="project.projectId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模块类型">
          <el-segmented v-model="startForm.moduleType" :options="startModuleOptions" />
        </el-form-item>
        <el-alert
          title="项目申报请使用“发起项目申请”；此入口仅用于已有项目的合同或结题流程。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button @click="startDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="submitStart">发起</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>
