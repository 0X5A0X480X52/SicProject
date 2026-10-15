<script setup lang="ts">
/**
 * 按当前权限分页展示可见流程、待办、办理历史和已结束模块。取消旧请求防止过期响应覆盖，新申请要求所属部门，SSE 只触发重查。
 */
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentAdd, Refresh } from '@element-plus/icons-vue'
import AppShell from '../layouts/AppShell.vue'
import { startProjectApplication } from '../api/projects'
import { queryWorkflowWorkbench } from '../api/workflowWorkbench'
import { useAuthStore } from '../stores/auth'
import { useWorkflowEvents } from '../composables/useWorkflowEvents'
import type { StartProjectApplicationRequest } from '../types/project'
import type { WorkflowWorkbenchItem } from '../types/workflow'
import { lifecycleLabel, moduleTypeLabel, roleLabel } from '../utils/displayLabels'

const router = useRouter()
const auth = useAuthStore()
const canApply = computed(() => auth.user?.deptId != null)
const hasMore = ref(false)
const loading = ref(false)
const applying = ref(false)
const applicationDialogOpen = ref(false)
const items = ref<WorkflowWorkbenchItem[]>([])
const filters = reactive({ moduleType: 'ALL', keyword: '', status: 'VISIBLE' })
const page = reactive({ current: 1, size: 10 })

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



const pagedItems = computed(() => items.value)

const stats = computed(() => [
  { label: '本页事项', value: items.value.length, type: '' },
  { label: '本页我的待办', value: items.value.filter((item) => item.todo).length, type: 'warning' },
  { label: '本页运行中模块', value: items.value.filter((item) => !item.finished).length, type: 'primary' },
  { label: '本页已结束模块', value: items.value.filter((item) => item.finished).length, type: 'success' },
])

function moduleLabel(moduleType: string) {
  return moduleTypeLabel(moduleType)
}

function statusTag(item: WorkflowWorkbenchItem) {
  if (item.finished) return { label: '模块已结束', type: 'success' }
  if (item.todo) return { label: '待办理', type: 'warning' }
  return { label: '运行中', type: 'primary' }
}

let abort: AbortController | null = null
/**
 * 取消上一轮列表读取，并只应用尚未取消请求的授权分页结果，防止快速切换筛选后旧响应覆盖新页面。
 */
async function loadItems(showMessage = false) {
  abort?.abort(); abort = new AbortController()
  const signal = abort.signal
  loading.value = true
  try {
    const response = await queryWorkflowWorkbench(filters.status, filters.moduleType, filters.keyword, page.current, page.size, signal)
    if (signal.aborted) return
    items.value = response.items; hasMore.value = response.hasMore
    if (showMessage) ElMessage.success('业务事项已刷新')
  } catch (error) {
    if (signal.aborted) return
    ElMessage.error(error instanceof Error ? error.message : '业务事项加载失败')
  } finally {
    if (!signal.aborted) loading.value = false
  }
}


function openApplicationDialog() {
  if (!canApply.value) { ElMessage.warning('请先联系管理员分配部门'); return }
  applicationDialogOpen.value = true
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

/**
 * 检查当前显示身份的部门及项目名后发起申请；成功跳转返回的首个模块，最终部门资格由 Project 再验证。
 */
async function submitApplicationStart() {
  if (!canApply.value) { ElMessage.warning('请先联系管理员分配部门'); return }
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
    router.push({ name: 'workflow-detail', params: { moduleInstanceId: response.moduleInstanceId } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '项目申请创建失败')
  } finally {
    applying.value = false
  }
}


function openDetail(item: WorkflowWorkbenchItem) {
  router.push({ name: 'workflow-detail', params: { moduleInstanceId: item.moduleInstanceId } })
}

const workflowEvents = useWorkflowEvents(() => loadItems())
let filterTimer: ReturnType<typeof setTimeout> | null = null
watch(() => [filters.status, filters.moduleType, filters.keyword, page.size], () => {
  page.current = 1
  if (filterTimer) clearTimeout(filterTimer)
  filterTimer = setTimeout(() => loadItems(), 250)
})
watch(() => page.current, () => loadItems())
onBeforeUnmount(() => { abort?.abort(); if (filterTimer) clearTimeout(filterTimer) })

onMounted(() => {
  loadItems()
  workflowEvents.connect()
})
</script>

<template>
  <AppShell>
    <section class="workflow-page-head">
      <div>
        <p class="eyebrow">Workflow</p>
        <h2>流程办理工作台</h2>
        <p class="helper-text">查看可见流程、我的待办和办理历史。模块结束后仍可查阅详情。</p>
        <p v-if="!canApply" class="helper-text">请先联系管理员分配部门，再发起项目申请。</p>
      </div>
      <div class="workflow-head-actions">
        <el-button type="primary" :icon="DocumentAdd" :disabled="!canApply" @click="openApplicationDialog">
          发起项目申请
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
            <el-option label="可见流程" value="VISIBLE" />
            <el-option label="我的待办" value="TODO" />
            <el-option label="我已办理" value="OPERATED" />
            <el-option label="已结束流程" value="FINISHED" />
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
        <el-table-column label="候选角色" width="150">
          <template #default="{ row }">{{ roleLabel(row.candidateRoleCode) }}</template>
        </el-table-column>
        <el-table-column label="项目生命周期" min-width="130">
          <template #default="{ row }">{{ lifecycleLabel(row.lifecycleStage) }}</template>
        </el-table-column>
        <el-table-column label="轮次" width="80" align="center">
          <template #default="{ row }">{{ row.currentRoundNo || 1 }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180" prop="lastTransitionTime" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">{{ row.canOperate ? '进入办理' : '查看详情' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="workflow-pagination">
        <el-button :disabled="loading || page.current === 1" @click="page.current--">上一页</el-button>
        <span>第 {{ page.current }} 页</span>
        <el-select v-model="page.size" style="width: 110px"><el-option v-for="size in [10,20,50]" :key="size" :value="size" :label="`${size} 条/页`" /></el-select>
        <el-button :disabled="loading || !hasMore" @click="page.current++">下一页</el-button>
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

  </AppShell>
</template>



