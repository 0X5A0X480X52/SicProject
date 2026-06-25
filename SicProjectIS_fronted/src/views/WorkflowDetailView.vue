<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { ArrowLeft, Document, FullScreen, Refresh } from '@element-plus/icons-vue'
import AppShell from '../layouts/AppShell.vue'
import { listProjectMaterials } from '../api/materials'
import { listProjects } from '../api/projects'
import { getRuntimeView, getWorkflowBpmn, submitStateTransition } from '../api/stateMachine'
import { useWorkflowEvents } from '../composables/useWorkflowEvents'
import BpmnViewerPanel from '../components/workflow/BpmnViewerPanel.vue'
import WorkflowNodeWorkPanel from '../components/workflow/WorkflowNodeWorkPanel.vue'
import type {
  AvailableTransition,
  MaterialContextView,
  MaterialRequirementView,
  ModuleStateRecord,
  NodeFormSaveRequest,
  RuntimeViewResponse,
  StateTransitionRequest,
} from '../types/nodeForms'
import type { ProjectSummary } from '../types/project'

type WorkflowTimelineStatus = 'completed' | 'current' | 'returned'

type WorkflowNodeDraftKey = string

interface WorkflowTimelineStep {
  id: string
  nodeId?: string
  state?: string
  name: string
  status: WorkflowTimelineStatus
  seq?: number
  roundNo?: number
  actor?: string
  time?: string
  eventType?: string
  result?: string
  summary?: string
}

interface MaterialSummaryItem {
  key: string
  name: string
  required: boolean
  count: number
  latest?: MaterialContextView
  requirement: MaterialRequirementView
}

interface WorkflowNodeFormModel {
  approved?: boolean
  title?: string
  summary?: string
  remark?: string
  externalActorCode?: string
  externalActorName?: string
  resultDocumentNo?: string
  operationType?: string
  operationTarget?: string
  archiveType?: string
  settlementAmount?: number | null
  scoreValue?: number | null
  reviewResult?: string
}

interface WorkflowNodeSubmitPayload {
  formCode?: string
  data?: NodeFormSaveRequest
  result?: string
  remark?: string
}

interface WorkflowNodeDraft {
  key: WorkflowNodeDraftKey
  transitionKey: string
  remark: string
  formModel: WorkflowNodeFormModel
  selectedMaterialVersionIds: number[]
  payload: WorkflowNodeSubmitPayload
}

const route = useRoute()
const router = useRouter()
const moduleInstanceId = computed(() => Number(route.params.moduleInstanceId))
const loading = ref(false)
const submitting = ref(false)
const runtimeView = ref<RuntimeViewResponse | null>(null)
const projectSummary = ref<ProjectSummary | null>(null)
const materials = ref<MaterialContextView[]>([])
const bpmnXml = ref('')
const formDrawerOpen = ref(false)
const selectedStepId = ref('')
const bpmnPanelRef = ref<InstanceType<typeof BpmnViewerPanel> | null>(null)
const draftCache = new Map<WorkflowNodeDraftKey, WorkflowNodeDraft>()
const workflowDraft = ref<WorkflowNodeDraft>(createEmptyDraft('__empty__'))

const context = computed(() => runtimeView.value?.context)

const pageTitle = computed(() => {
  if (projectSummary.value?.projectName) return projectSummary.value.projectName
  const ctx = context.value
  if (!ctx) return '流程办理详情'
  return `项目 ${ctx.projectId}`
})

const moduleTitle = computed(() => {
  const ctx = context.value
  if (!ctx) return '流程模块'
  return `${ctx.moduleType} · ${ctx.currentNodeName || ctx.currentNodeId || '当前节点'}`
})

const statusTag = computed(() => {
  const ctx = context.value
  if (!ctx) return { text: '加载中', type: 'info' as const }
  if (ctx.finishedAt) return { text: '已完成', type: 'success' as const }
  if (runtimeView.value?.canOperate) return { text: '待我办理', type: 'warning' as const }
  return { text: '流转中', type: 'primary' as const }
})

const currentTask = computed(() => {
  const ctx = context.value
  if (!ctx) return null
  return runtimeView.value?.openTasks.find((task) => task.nodeId === ctx.currentNodeId) ?? runtimeView.value?.openTasks[0] ?? null
})

const transitionOptions = computed(() => runtimeView.value?.availableTransitions ?? [])

const selectedTransition = computed(() => {
  const transitions = transitionOptions.value
  if (!transitions.length) return null
  return transitions.find((transition) => transitionKey(transition) === workflowDraft.value.transitionKey) ?? transitions[0]
})

const canSubmit = computed(() => Boolean(runtimeView.value?.canOperate && selectedTransition.value))

const timelineSteps = computed<WorkflowTimelineStep[]>(() => {
  const view = runtimeView.value
  if (!view) return []

  const steps = view.history.map((record) => historyToStep(record))
  const ctx = view.context
  const currentId = ctx.currentNodeId || ctx.currentState || 'current'
  const alreadyCurrent = steps.some((step) => step.nodeId === ctx.currentNodeId && step.state === ctx.currentState)

  if (!ctx.finishedAt && !alreadyCurrent) {
    steps.push({
      id: `current-${currentId}`,
      nodeId: ctx.currentNodeId ?? undefined,
      state: ctx.currentState ?? undefined,
      name: ctx.currentNodeName || ctx.currentNodeId || ctx.currentState || '当前办理节点',
      status: 'current',
      seq: ctx.currentSeq,
      roundNo: ctx.currentRoundNo,
      actor: ctx.currentResponsibleActorName || ctx.currentCandidateRoleCode || undefined,
      time: ctx.lastTransitionTime,
      eventType: ctx.lastEventType,
      result: ctx.lastResult,
      summary: ctx.lastSummary,
    })
  }

  if (!steps.length) {
    steps.push({
      id: 'current-empty',
      nodeId: ctx.currentNodeId ?? undefined,
      state: ctx.currentState ?? undefined,
      name: ctx.currentNodeName || '当前办理节点',
      status: ctx.finishedAt ? 'completed' : 'current',
      seq: ctx.currentSeq,
      roundNo: ctx.currentRoundNo,
    })
  }

  return steps.sort((a, b) => (a.seq ?? 9999) - (b.seq ?? 9999))
})

const selectedStep = computed(() => {
  const steps = timelineSteps.value
  return steps.find((step) => step.id === selectedStepId.value) ?? steps[steps.length - 1] ?? null
})

const recentRecords = computed(() => [...(runtimeView.value?.history ?? [])].reverse().slice(0, 4))

const returnedRecords = computed(() => (runtimeView.value?.history ?? []).filter(isReturnedRecord).slice(-3).reverse())

const materialSummaries = computed<MaterialSummaryItem[]>(() => {
  const requirements = runtimeView.value?.materialRequirements ?? []
  return requirements.map((requirement) => {
    const code = requirement.materialTypeCode ?? `requirement-${requirement.requirementId}`
    const versions = materials.value.filter((item) => item.materialTypeCode === requirement.materialTypeCode)
    const selected = versions.find((item) => workflowDraft.value.selectedMaterialVersionIds.includes(item.materialVersionId))
    const latest = selected ?? versions.find((item) => item.isCurrent) ?? versions[0]
    return {
      key: code,
      name: requirement.materialTypeName || requirement.materialTypeCode || '材料',
      required: Boolean(requirement.required),
      count: versions.length,
      latest,
      requirement,
    }
  })
})

const currentNodeMeta = computed(() => {
  const ctx = context.value
  if (!ctx) return []
  return [
    { label: '当前状态', value: ctx.currentState || '-' },
    { label: '候选角色', value: ctx.currentCandidateRoleCode || currentTask.value?.candidateRoleCode || '-' },
    { label: '办理模式', value: ctx.currentOperationMode || '-' },
    { label: '责任人', value: ctx.currentResponsibleActorName || ctx.currentRepresentedActorName || '-' },
    { label: '当前序号', value: ctx.currentSeq ?? '-' },
    { label: '当前轮次', value: ctx.currentRoundNo ?? '-' },
  ]
})

function transitionKey(transition: AvailableTransition) {
  return `${transition.transitionId || transition.eventType}-${transition.result || ''}-${transition.targetRef}`
}

function createEmptyDraft(key: WorkflowNodeDraftKey, view?: RuntimeViewResponse): WorkflowNodeDraft {
  const firstTransition = view?.availableTransitions?.[0]
  const firstForm = view?.nodeForms?.find((item) => item.writeMode !== 'READ_ONLY') ?? view?.nodeForms?.[0]
  return {
    key,
    transitionKey: firstTransition ? transitionKey(firstTransition) : '',
    remark: '',
    formModel: {
      approved: true,
      title: firstForm?.title || view?.context.currentNodeName || '',
      summary: '',
      externalActorName: '主管部门/第三方机构',
      reviewResult: 'PASSED',
      settlementAmount: null,
    },
    selectedMaterialVersionIds: [],
    payload: {},
  }
}

function buildDraftKey(view: RuntimeViewResponse): WorkflowNodeDraftKey {
  const ctx = view.context
  return [ctx.moduleInstanceId, ctx.currentSeq ?? 'seq', ctx.currentNodeId || ctx.currentState || 'node'].join(':')
}

function ensureDraft(view: RuntimeViewResponse, reset = false) {
  const key = buildDraftKey(view)
  if (reset || !draftCache.has(key)) {
    draftCache.set(key, createEmptyDraft(key, view))
  }
  const draft = draftCache.get(key)!
  if (!draft.transitionKey && view.availableTransitions[0]) {
    draft.transitionKey = transitionKey(view.availableTransitions[0])
  }
  workflowDraft.value = draft
}

function updateDraftPayload(payload: WorkflowNodeSubmitPayload) {
  workflowDraft.value.payload = payload
}

function saveDraft() {
  draftCache.set(workflowDraft.value.key, workflowDraft.value)
  ElMessage.success('本页草稿已保留')
}

function discardDraft() {
  const view = runtimeView.value
  if (!view) return
  ensureDraft(view, true)
  ElMessage.success('已放弃当前节点未提交修改')
}

function historyToStep(record: ModuleStateRecord): WorkflowTimelineStep {
  const returned = isReturnedRecord(record)
  return {
    id: `history-${record.stateRecordId}`,
    nodeId: record.toNodeId || record.fromNodeId,
    state: record.toState,
    name: record.toNodeId || record.toState || record.eventType || `记录 ${record.seq}`,
    status: returned ? 'returned' : 'completed',
    seq: record.seq,
    roundNo: record.roundNo,
    time: record.createdAt,
    eventType: record.eventType,
    result: record.result,
    summary: record.summary,
  }
}

function isReturnedRecord(record: ModuleStateRecord) {
  const text = `${record.eventType ?? ''} ${record.result ?? ''} ${record.summary ?? ''}`.toUpperCase()
  return text.includes('RETURN') || text.includes('REJECT') || text.includes('退回') || text.includes('驳回')
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function resultType(value?: string) {
  if (!value) return 'info'
  const text = value.toUpperCase()
  if (text.includes('PASS') || text.includes('APPROVE')) return 'success'
  if (text.includes('RETURN') || text.includes('REJECT') || text.includes('FAIL')) return 'warning'
  return 'info'
}

function openBpmnLarge() {
  bpmnPanelRef.value?.openLarge()
}

function selectStep(step: WorkflowTimelineStep) {
  selectedStepId.value = step.id
}

async function loadProjectSummary(projectId: number) {
  try {
    const projects = await listProjects()
    projectSummary.value = projects.find((project) => project.projectId === projectId) ?? null
  } catch {
    projectSummary.value = null
  }
}

async function loadMaterials(projectId: number) {
  try {
    materials.value = await listProjectMaterials(projectId)
  } catch {
    materials.value = []
  }
}

async function loadDetail(showMessage = false) {
  if (!Number.isFinite(moduleInstanceId.value)) {
    ElMessage.error('模块实例 ID 无效')
    return
  }
  loading.value = true
  try {
    const view = await getRuntimeView(moduleInstanceId.value)
    runtimeView.value = view
    ensureDraft(view)
    selectedStepId.value = `current-${view.context.currentNodeId || view.context.currentState || 'current'}`

    await Promise.all([loadProjectSummary(view.context.projectId), loadMaterials(view.context.projectId)])

    if (view.context.workflowDefinitionId) {
      const bpmn = await getWorkflowBpmn(view.context.workflowDefinitionId)
      bpmnXml.value = bpmn.bpmnXml
    }
    if (showMessage) ElMessage.success('流程详情已刷新，当前节点草稿已保留')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '流程详情加载失败')
  } finally {
    loading.value = false
  }
}

function validateRequiredMaterials() {
  const view = runtimeView.value
  if (!view) return true
  const selectedTypes = new Set(
    materials.value
      .filter((item) => workflowDraft.value.selectedMaterialVersionIds.includes(item.materialVersionId) && item.isCurrent)
      .map((item) => item.materialTypeCode),
  )
  const missing = view.materialRequirements.filter((requirement) =>
    Boolean(requirement.required && requirement.materialTypeCode && !selectedTypes.has(requirement.materialTypeCode)),
  )
  if (missing.length) {
    ElMessage.warning(`请先选择或上传必填材料的当前版本：${missing.map((item) => item.materialTypeName || item.materialTypeCode).join('、')}`)
    return false
  }
  return true
}

async function submitCurrentDraft() {
  const view = runtimeView.value
  const transition = selectedTransition.value
  if (!view || !transition) return
  if (!validateRequiredMaterials()) return

  const payload = workflowDraft.value.payload
  const currentDraftKey = workflowDraft.value.key
  const request: StateTransitionRequest = {
    eventType: transition.eventType,
    expectedSeq: view.context.currentSeq,
    result: payload.result || transition.result,
    remark: workflowDraft.value.remark || payload.remark,
    materialVersionIds: Array.from(new Set(workflowDraft.value.selectedMaterialVersionIds)),
    formCode: payload.formCode,
    nodeFormData: payload.data,
  }

  submitting.value = true
  try {
    await submitStateTransition(moduleInstanceId.value, request)
    draftCache.delete(currentDraftKey)
    formDrawerOpen.value = false
    ElNotification.success({ title: '提交成功', message: '已根据后端最新运行时视图刷新页面。' })
    await loadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '流程提交失败')
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

const workflowEvents = useWorkflowEvents((event) => {
  if (!event.moduleInstanceId || event.moduleInstanceId === moduleInstanceId.value) {
    loadDetail()
  }
})

onMounted(() => {
  loadDetail()
  workflowEvents.connect()
})
</script>

<template>
  <AppShell>
    <el-skeleton v-if="loading && !runtimeView" animated :rows="10" />
    <el-empty v-else-if="!runtimeView" description="未加载到流程详情" />

    <template v-else>
      <section class="workflow-detail-v01-topbar">
        <div class="workflow-detail-v01-topbar-main">
          <el-button link class="workflow-detail-v01-back" :icon="ArrowLeft" @click="router.push({ name: 'workflow' })">
            返回工作台
          </el-button>
          <div>
            <h2>{{ pageTitle }}</h2>
            <p>{{ moduleTitle }}</p>
          </div>
        </div>
        <div class="workflow-detail-v01-topbar-meta">
          <el-tag effect="dark" :type="statusTag.type">{{ statusTag.text }}</el-tag>
          <span>轮次 {{ runtimeView.context.currentRoundNo ?? '-' }}</span>
          <span>节点 {{ runtimeView.context.currentNodeName || runtimeView.context.currentNodeId || '-' }}</span>
          <el-button :icon="FullScreen" plain @click="openBpmnLarge">展开 BPMN</el-button>
          <el-button :icon="Refresh" :loading="loading" plain @click="loadDetail(true)">刷新</el-button>
        </div>
      </section>

      <section class="workflow-detail-v01-page">
        <aside class="workflow-detail-v01-column">
          <el-card class="workflow-detail-v01-card" shadow="never">
            <template #header>
              <div class="workflow-detail-v01-card-title">
                <span>项目概览</span>
                <el-tag size="small" type="info">{{ runtimeView.context.moduleType }}</el-tag>
              </div>
            </template>
            <div class="workflow-detail-v01-overview-title">{{ projectSummary?.projectName || pageTitle }}</div>
            <div class="workflow-detail-v01-kv-list">
              <div class="workflow-detail-v01-kv"><span>项目编号</span><strong>{{ projectSummary?.projectCode || '-' }}</strong></div>
              <div class="workflow-detail-v01-kv"><span>负责人</span><strong>{{ projectSummary?.leaderRealName || '-' }}</strong></div>
              <div class="workflow-detail-v01-kv"><span>所属部门</span><strong>{{ projectSummary?.deptName || '-' }}</strong></div>
              <div class="workflow-detail-v01-kv"><span>项目类型</span><strong>{{ projectSummary?.projectType || '-' }}</strong></div>
              <div class="workflow-detail-v01-kv"><span>项目级别</span><strong>{{ projectSummary?.projectLevel || '-' }}</strong></div>
              <div class="workflow-detail-v01-kv"><span>生命周期</span><strong>{{ projectSummary?.lifecycleStage || '-' }}</strong></div>
            </div>
          </el-card>

          <el-card class="workflow-detail-v01-card" shadow="never">
            <template #header>
              <div class="workflow-detail-v01-card-title">
                <span>申报/流程材料</span>
                <el-button link type="primary" :icon="Document" @click="formDrawerOpen = true">办理材料</el-button>
              </div>
            </template>
            <el-empty v-if="!materialSummaries.length" description="当前节点暂无材料要求" />
            <div v-else class="workflow-detail-v01-material-list">
              <div v-for="item in materialSummaries" :key="item.key" class="workflow-detail-v01-material-item">
                <div>
                  <div class="workflow-detail-v01-material-name">
                    {{ item.name }}
                    <el-tag v-if="item.required" size="small" type="danger">必填</el-tag>
                  </div>
                  <p>{{ item.latest?.fileName || item.requirement.description || '尚未选择或上传材料' }}</p>
                </div>
                <el-badge :value="item.count" type="primary" />
              </div>
            </div>
          </el-card>

          <el-card class="workflow-detail-v01-card" shadow="never">
            <template #header>历史退回/最近记录</template>
            <el-empty v-if="!returnedRecords.length && !recentRecords.length" description="暂无历史记录" />
            <div v-else class="workflow-detail-v01-record-list">
              <div
                v-for="record in (returnedRecords.length ? returnedRecords : recentRecords)"
                :key="record.stateRecordId"
                class="workflow-detail-v01-record-item"
              >
                <el-tag size="small" :type="resultType(record.result)">{{ record.result || record.eventType }}</el-tag>
                <strong>{{ record.toNodeId || record.toState }}</strong>
                <p>{{ record.summary || '暂无摘要' }}</p>
                <span>{{ formatDateTime(record.createdAt) }}</span>
              </div>
            </div>
          </el-card>
        </aside>

        <main class="workflow-detail-v01-center">
          <BpmnViewerPanel
            ref="bpmnPanelRef"
            :bpmn-xml="bpmnXml"
            :current-node-id="runtimeView.context.currentNodeId"
            :history="runtimeView.history"
            :open-tasks="runtimeView.openTasks"
          />

          <el-card class="workflow-detail-v01-card workflow-detail-v01-timeline-card" shadow="never">
            <template #header>
              <div class="workflow-detail-v01-card-title">
                <span>流程办理时间线</span>
                <el-tag size="small" type="info">{{ timelineSteps.length }} 步</el-tag>
              </div>
            </template>
            <div class="workflow-detail-v01-timeline-scroll">
              <div class="workflow-detail-v01-timeline-line" />
              <button
                v-for="step in timelineSteps"
                :key="step.id"
                type="button"
                :class="['workflow-detail-v01-step', `is-${step.status}`, { 'is-selected': selectedStep?.id === step.id }]"
                @click="selectStep(step)"
              >
                <span class="workflow-detail-v01-step-dot">{{ step.seq ?? '' }}</span>
                <strong>{{ step.name }}</strong>
                <small>{{ formatDateTime(step.time) }}</small>
                <em>{{ step.summary || step.eventType || step.state || '等待办理' }}</em>
              </button>
            </div>
          </el-card>
        </main>

        <aside class="workflow-detail-v01-column workflow-detail-v01-right">
          <el-card class="workflow-detail-v01-card" shadow="never">
            <template #header>
              <div class="workflow-detail-v01-card-title">
                <span>当前办理任务</span>
                <el-tag size="small" :type="runtimeView.canOperate ? 'success' : 'info'">
                  {{ runtimeView.canOperate ? '可办理' : '只读' }}
                </el-tag>
              </div>
            </template>
            <el-alert
              :title="runtimeView.canOperate ? '当前用户具备该节点办理权限' : '当前用户不可办理此节点，仅可查看运行时状态'"
              :type="runtimeView.canOperate ? 'success' : 'info'"
              :closable="false"
              show-icon
            />
            <div class="workflow-detail-v01-meta-grid">
              <div v-for="item in currentNodeMeta" :key="item.label">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>

            <template v-if="transitionOptions.length">
              <div class="workflow-detail-v01-section-title">可执行动作</div>
              <el-radio-group v-model="workflowDraft.transitionKey" class="workflow-detail-v01-action-group">
                <el-radio-button
                  v-for="transition in transitionOptions"
                  :key="transitionKey(transition)"
                  :label="transitionKey(transition)"
                >
                  {{ transition.result || transition.eventType }}
                </el-radio-button>
              </el-radio-group>
              <el-input
                v-model="workflowDraft.remark"
                class="workflow-detail-v01-remark"
                type="textarea"
                :rows="4"
                placeholder="填写审批意见或办理说明"
              />
              <div class="workflow-detail-v01-submit-row">
                <el-button @click="formDrawerOpen = true">办理当前节点</el-button>
                <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitCurrentDraft">
                  提交办理
                </el-button>
              </div>
            </template>
            <el-empty v-else description="当前节点暂无可执行动作" />
          </el-card>

          <el-card class="workflow-detail-v01-card" shadow="never">
            <template #header>选中节点详情</template>
            <el-descriptions v-if="selectedStep" :column="1" size="small" border>
              <el-descriptions-item label="节点">{{ selectedStep.name }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ selectedStep.state || '-' }}</el-descriptions-item>
              <el-descriptions-item label="事件">{{ selectedStep.eventType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="结果">{{ selectedStep.result || '-' }}</el-descriptions-item>
              <el-descriptions-item label="摘要">{{ selectedStep.summary || '-' }}</el-descriptions-item>
              <el-descriptions-item label="时间">{{ formatDateTime(selectedStep.time) }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="请选择时间线节点" />
          </el-card>

          <el-card class="workflow-detail-v01-card" shadow="never">
            <template #header>最近审批记录</template>
            <el-timeline v-if="recentRecords.length">
              <el-timeline-item
                v-for="record in recentRecords"
                :key="record.stateRecordId"
                :timestamp="formatDateTime(record.createdAt)"
                :type="resultType(record.result)"
              >
                <div class="workflow-detail-v01-timeline-mini-title">{{ record.eventType }} · {{ record.result || '-' }}</div>
                <p>{{ record.summary || record.toState }}</p>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无审批记录" />
          </el-card>
        </aside>
      </section>

      <el-drawer v-model="formDrawerOpen" title="办理当前节点" size="62%">
        <WorkflowNodeWorkPanel
          v-model:transition-key="workflowDraft.transitionKey"
          v-model:remark="workflowDraft.remark"
          v-model:form-model="workflowDraft.formModel"
          v-model:selected-material-ids="workflowDraft.selectedMaterialVersionIds"
          :view="runtimeView"
          :transitions="transitionOptions"
          :submitting="submitting"
          :recent-records="recentRecords"
          :returned-records="returnedRecords"
          @payload-change="updateDraftPayload"
          @materials-changed="loadMaterials(runtimeView.context.projectId)"
          @save-draft="saveDraft"
          @discard="discardDraft"
          @submit="submitCurrentDraft"
          @close="formDrawerOpen = false"
        />
      </el-drawer>
    </template>
  </AppShell>
</template>
