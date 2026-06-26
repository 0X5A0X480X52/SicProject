<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { ArrowLeft, Document, EditPen, FullScreen, Refresh } from '@element-plus/icons-vue'
import AppShell from '../layouts/AppShell.vue'
import { listProjectMaterials } from '../api/materials'
import { listProjects } from '../api/projects'
import { getRuntimeView, getWorkflowBpmn, getWorkflowNodes, submitStateTransition } from '../api/stateMachine'
import { useWorkflowEvents } from '../composables/useWorkflowEvents'
import { buildRequestFromFlatForm, quickActionConfig, detectExpertSubType } from '../composables/useNodeFormHelpers'
import { defaultsForKind, requiredFieldsForKind } from '../utils/nodeFormFields'
import MaterialRequirementPanel from '../components/workflow/MaterialRequirementPanel.vue'
import BpmnViewerPanel from '../components/workflow/BpmnViewerPanel.vue'
import ProcessSteps, { type WorkflowProcessStep } from '../components/workflow/ProcessSteps.vue'
import RecordTimeline, { type WorkflowDisplayRecord } from '../components/workflow/RecordTimeline.vue'
import WorkflowNodeWorkPanel from '../components/workflow/WorkflowNodeWorkPanel.vue'
import ExpertReviewPanel from '../components/workflow/ExpertReviewPanel.vue'
import type { AvailableTransition, MaterialContextView, MaterialRequirementView, ModuleStateRecord, NodeFormSaveRequest, RuntimeViewResponse, StateTransitionRequest } from '../types/nodeForms'
import type { ProjectSummary } from '../types/project'
import type { WorkflowNodeDefinition } from '../types/workflow'
import { eventLabel, lifecycleLabel, moduleTypeLabel, operationModeLabel, resultLabel, roleLabel, stateLabel } from '../utils/displayLabels'

type DraftKey = string
interface MaterialSummaryItem { key: string; name: string; required: boolean; count: number; latest?: MaterialContextView; requirement: MaterialRequirementView; missing: boolean }
interface WorkflowNodeFormModel { approved?: boolean; title?: string; summary?: string; remark?: string; externalActorCode?: string; externalActorName?: string; resultDocumentNo?: string; operationType?: string; operationTarget?: string; archiveType?: string; approvedAmount?: number | null; receivedAmount?: number | null; spentAmount?: number | null; settlementResult?: string; financeReviewComment?: string; settlementAmount?: number | null; scoreValue?: number | null; reviewResult?: string; isLimitedProject?: boolean }
interface WorkflowNodeSubmitPayload { formCode?: string; data?: NodeFormSaveRequest; result?: string; remark?: string }
interface WorkflowNodeDraft { key: DraftKey; transitionKey: string; remark: string; formModel: WorkflowNodeFormModel; selectedMaterialVersionIds: number[]; payload: WorkflowNodeSubmitPayload }
interface RoundGroup { roundNo: number | string; resultText: string; summary: string; startAt: string; endAt: string; records: WorkflowDisplayRecord[] }
interface WorkflowDisplayHistoryRecord extends WorkflowDisplayRecord { source: ModuleStateRecord; displayRoundNo: number | string; displayNodeId: string; statusKind: WorkflowProcessStep['status'] }

const route = useRoute()
const router = useRouter()
const moduleInstanceId = computed(() => Number(route.params.moduleInstanceId))
const loading = ref(false)
const submitting = ref(false)
const runtimeView = ref<RuntimeViewResponse | null>(null)
const projectSummary = ref<ProjectSummary | null>(null)
const materials = ref<MaterialContextView[]>([])
const workflowNodes = ref<WorkflowNodeDefinition[]>([])
const bpmnXml = ref('')
const formDrawerOpen = ref(false)
const selectedNodeId = ref('')
const openedDetailPanels = ref(['base'])
const helperPanels = ref<string[]>([])
const materialsOpinionsCollapse = ref<string[]>(['materials'])
const bpmnPanelRef = ref<InstanceType<typeof BpmnViewerPanel> | null>(null)
const draftCache = new Map<DraftKey, WorkflowNodeDraft>()
const workflowDraft = ref<WorkflowNodeDraft>(createEmptyDraft('__empty__'))

const context = computed(() => runtimeView.value?.context)
const pageTitle = computed(() => projectSummary.value?.projectName || (context.value ? `项目 ${context.value.projectId}` : '流程办理详情'))
const moduleTitle = computed(() => context.value ? `${moduleTypeLabel(context.value.moduleType)} · ${context.value.currentNodeName || context.value.currentNodeId || '当前节点'}` : '流程模块')
const statusTag = computed(() => {
  const ctx = context.value
  if (!ctx) return { text: '加载中', type: 'info' as const }
  if (ctx.finishedAt) return { text: '已完成', type: 'success' as const }
  if (runtimeView.value?.canOperate) return { text: '待我办理', type: 'warning' as const }
  return { text: '流转中', type: 'primary' as const }
})
const currentDataKind = computed(() => {
  const form = runtimeView.value?.nodeForms?.find((item) => item.writeMode !== 'READ_ONLY')
    ?? runtimeView.value?.nodeForms?.[0]
  return form?.dataKind
})
const expertSubType = computed(() =>
  detectExpertSubType(context.value?.currentNodeId, context.value?.currentCandidateRoleCode),
)
const quickAction = computed(() => quickActionConfig(currentDataKind.value, expertSubType.value))
const isExpertNode = computed(() => quickAction.value.category === 'expert')
const isFinancialSettlementNode = computed(() => currentDataKind.value === 'FINANCIAL_SETTLEMENT')
const currentTask = computed(() => {
  const ctx = context.value
  if (!ctx) return null
  return runtimeView.value?.openTasks.find((task) => task.nodeId === ctx.currentNodeId) ?? runtimeView.value?.openTasks[0] ?? null
})
const transitionOptions = computed(() => runtimeView.value?.availableTransitions ?? [])
const selectedTransition = computed(() => transitionOptions.value.find((item) => transitionKey(item) === workflowDraft.value.transitionKey) ?? transitionOptions.value[0] ?? null)
const canSubmit = computed(() => Boolean(runtimeView.value?.canOperate && selectedTransition.value))
const summaryItems = computed(() => [
  { label: '项目编号', value: projectSummary.value?.projectCode || '-' },
  { label: '负责人', value: projectSummary.value?.leaderRealName || '-' },
  { label: '所属单位', value: projectSummary.value?.deptName || '-' },
  { label: '当前节点', value: context.value?.currentNodeName || context.value?.currentNodeId || '-' },
  { label: '最近更新', value: formatDateTime(context.value?.lastTransitionTime || context.value?.startedAt) },
])
const currentNodeMeta = computed(() => {
  const ctx = context.value
  if (!ctx) return []
  return [
    { label: '当前节点', value: ctx.currentNodeName || ctx.currentNodeId || '-' },
    { label: '处理角色', value: roleLabel(ctx.currentCandidateRoleCode || currentTask.value?.candidateRoleCode) },
    { label: '处理人', value: ctx.currentResponsibleActorName || ctx.currentRepresentedActorName || '-' },
    { label: '办理模式', value: operationModeLabel(ctx.currentOperationMode) },
  ]
})
const displayHistoryRecords = computed<WorkflowDisplayHistoryRecord[]>(() => (runtimeView.value?.history ?? []).map(toDisplayHistoryRecord))
const processNodes = computed<WorkflowProcessStep[]>(() => {
  const view = runtimeView.value
  if (!view) return []
  if (!workflowNodes.value.length) return fallbackProcessNodes(view)

  const currentRoundNo = view.context.currentRoundNo
  const historyByNode = new Map<string, WorkflowDisplayHistoryRecord[]>()
  const roundCounts = new Map<string, number>()
  displayHistoryRecords.value.forEach((record) => {
    if (!record.displayNodeId) return
    roundCounts.set(record.displayNodeId, (roundCounts.get(record.displayNodeId) ?? 0) + 1)
    if (record.displayRoundNo !== currentRoundNo) return
    historyByNode.set(record.displayNodeId, [...(historyByNode.get(record.displayNodeId) ?? []), record])
  })

  return workflowNodes.value
    .filter(isVisibleProcessNode)
    .map((node) => {
      const records = historyByNode.get(node.nodeId) ?? []
      const latest = records[records.length - 1]
      const isCurrent = !view.context.finishedAt && node.nodeId === view.context.currentNodeId
      const status = isCurrent ? 'current' : latest?.statusKind ?? 'waiting'
      return {
        id: `node-${node.nodeId}`,
        name: node.nodeName || node.nodeId,
        status,
        statusText: stepStatusText(status, latest?.source),
        desc: latest?.remark || stateLabel(node.stateCode) || node.nodeType || '待流程流转至该节点',
        actor: node.responsibleActorName || node.representedActorName || roleLabel(node.candidateRoleCode),
        finishedAt: status === 'done' || status === 'returned' || status === 'rejected' ? latest?.time : '',
        roundCount: (roundCounts.get(node.nodeId) ?? 0) > 1 ? roundCounts.get(node.nodeId) : undefined,
      } satisfies WorkflowProcessStep
    })
})
const selectedNode = computed(() => processNodes.value.find((node) => node.id === selectedNodeId.value) ?? processNodes.value.find((node) => node.status === 'current') ?? processNodes.value[processNodes.value.length - 1] ?? null)
const materialSummaries = computed<MaterialSummaryItem[]>(() => (runtimeView.value?.materialRequirements ?? []).map((requirement) => {
  const code = requirement.materialTypeCode ?? `requirement-${requirement.requirementId}`
  const versions = materials.value.filter((item) => item.materialTypeCode === requirement.materialTypeCode)
  const selected = versions.find((item) => workflowDraft.value.selectedMaterialVersionIds.includes(item.materialVersionId))
  const latest = selected ?? versions.find((item) => item.isCurrent) ?? versions[0]
  return { key: code, name: requirement.materialTypeName || requirement.materialTypeCode || '材料', required: Boolean(requirement.required), count: versions.length, latest, requirement, missing: Boolean(requirement.required && !versions.some((item) => item.isCurrent)) }
}))
const materialChecks = computed(() => {
  const required = materialSummaries.value.filter((item) => item.required)
  const missing = required.filter((item) => item.missing)
  return [
    { label: '材料要求', value: `${materialSummaries.value.length} 项`, type: 'info' as const },
    { label: '必填材料', value: `${required.length} 项`, type: missing.length ? ('warning' as const) : ('success' as const) },
    { label: '当前版本', value: `${materials.value.filter((item) => item.isCurrent).length} 份`, type: 'primary' as const },
  ]
})
const returnedRecordsInline = computed(() => (runtimeView.value?.history ?? []).filter(isReturnedRecord).slice(-3).reverse())
const riskItems = computed(() => {
  const risks: Array<{ id: string; type: 'success' | 'warning' | 'info' | 'error'; title: string; desc: string }> = []
  const missing = materialSummaries.value.filter((item) => item.missing)
  if (missing.length) risks.push({ id: 'missing-materials', type: 'warning', title: '存在必填材料缺失', desc: missing.map((item) => item.name).join('、') })
  if (runtimeView.value && !runtimeView.value.canOperate) risks.push({ id: 'readonly', type: 'info', title: '当前用户不可办理此节点', desc: '页面仅展示运行时状态和历史记录。' })
  if (runtimeView.value && !transitionOptions.value.length) risks.push({ id: 'no-action', type: 'info', title: '当前节点暂无可执行动作', desc: '请等待流程状态更新或检查节点配置。' })
  if (returnedRecordsInline.value.length) risks.push({ id: 'returned', type: 'warning', title: '存在退回或驳回记录', desc: '建议展开历史轮次查看退回原因。' })
  if (!risks.length) risks.push({ id: 'ok', type: 'success', title: '暂无突出风险', desc: '材料、权限和流转记录未发现明显阻塞项。' })
  return risks
})
const displayRecords = computed(() => displayHistoryRecords.value)
const currentRound = computed<RoundGroup>(() => {
  const ctxRound = context.value?.currentRoundNo
  const records = displayHistoryRecords.value.filter((record) => record.displayRoundNo === ctxRound)
  return buildRoundGroup(ctxRound ?? '-', records)
})
const historyRounds = computed<RoundGroup[]>(() => {
  const currentRoundNo = context.value?.currentRoundNo
  const groups = new Map<number | string, WorkflowDisplayRecord[]>()
  displayHistoryRecords.value.forEach((record) => {
    const roundNo = record.displayRoundNo
    if (roundNo === currentRoundNo) return
    groups.set(roundNo, [...(groups.get(roundNo) ?? []), record])
  })
  return [...groups.entries()].sort(([a], [b]) => Number(b) - Number(a)).map(([roundNo, records]) => buildRoundGroup(roundNo, records))
})

function transitionKey(transition: AvailableTransition) { return `${transition.transitionId || transition.eventType}-${transition.result || ''}-${transition.targetRef}` }
function createEmptyDraft(key: DraftKey, view?: RuntimeViewResponse): WorkflowNodeDraft {
  const firstTransition = view?.availableTransitions?.[0]
  const firstForm = view?.nodeForms?.find((item) => item.writeMode !== 'READ_ONLY') ?? view?.nodeForms?.[0]
  const baseFormModel = defaultsForKind(firstForm?.dataKind)
  return {
    key,
    transitionKey: firstTransition ? transitionKey(firstTransition) : '',
    remark: '',
    formModel: {
      ...baseFormModel,
      approved: true,
      title: firstForm?.title || view?.context.currentNodeName || '',
      summary: '',
      externalActorName: '主管部门/第三方机构',
      reviewResult: 'PASSED',
      settlementAmount: null,
      receivedAmount: null,
      spentAmount: null,
      settlementResult: 'APPROVED',
    },
    selectedMaterialVersionIds: [],
    payload: {},
  }
}
function buildDraftKey(view: RuntimeViewResponse): DraftKey { const ctx = view.context; return [ctx.moduleInstanceId, ctx.currentSeq ?? 'seq', ctx.currentNodeId || ctx.currentState || 'node'].join(':') }
function ensureDraft(view: RuntimeViewResponse, reset = false) {
  const key = buildDraftKey(view)
  if (reset || !draftCache.has(key)) draftCache.set(key, createEmptyDraft(key, view))
  const draft = draftCache.get(key)!
  if (!draft.transitionKey && view.availableTransitions[0]) draft.transitionKey = transitionKey(view.availableTransitions[0])
  workflowDraft.value = draft
}
function updateDraftPayload(payload: WorkflowNodeSubmitPayload) { workflowDraft.value.payload = payload }
function saveDraft() { draftCache.set(workflowDraft.value.key, workflowDraft.value); ElMessage.success('本页草稿已保留') }
function discardDraft() { if (!runtimeView.value) return; ensureDraft(runtimeView.value, true); ElMessage.success('已放弃当前节点未提交修改') }
function fallbackProcessNodes(view: RuntimeViewResponse): WorkflowProcessStep[] {
  const byId = new Map<string, WorkflowProcessStep & { seq: number }>()
  const counts = new Map<string, number>()
  const currentRoundNo = view.context.currentRoundNo
  displayHistoryRecords.value.forEach((record, index) => {
    const nodeId = record.displayNodeId || record.source.toState || record.source.eventType || String(record.source.stateRecordId)
    const id = `node-${nodeId}`
    counts.set(id, (counts.get(id) ?? 0) + 1)
    if (record.displayRoundNo !== currentRoundNo) return
    byId.set(id, {
      id,
      seq: record.source.seq ?? index + 1,
      name: record.nodeName,
      status: record.statusKind,
      statusText: record.actionText,
      desc: record.remark || eventLabel(record.source.eventType) || stateLabel(record.source.toState),
      actor: eventLabel(record.source.eventType),
      finishedAt: record.time,
      roundCount: counts.get(id),
    })
  })
  const ctx = view.context
  const currentId = `node-${ctx.currentNodeId || ctx.currentState || ctx.moduleInstanceId}`
  if (!ctx.finishedAt) byId.set(currentId, { id: currentId, seq: ctx.currentSeq ?? 9999, name: ctx.currentNodeName || ctx.currentNodeId || ctx.currentState || '当前办理节点', status: 'current', statusText: view.canOperate ? '待我办理' : '流转中', desc: ctx.lastSummary || ctx.currentState || '等待当前节点办理', actor: ctx.currentResponsibleActorName || roleLabel(ctx.currentCandidateRoleCode || currentTask.value?.candidateRoleCode), finishedAt: '', roundCount: Math.max(1, counts.get(currentId) ?? 1) })
  if (!byId.size) byId.set(currentId, { id: currentId, seq: 1, name: ctx.currentNodeName || '当前办理节点', status: ctx.finishedAt ? 'done' : 'current', statusText: ctx.finishedAt ? '已完成' : '当前节点', desc: ctx.lastSummary || '-', actor: roleLabel(ctx.currentCandidateRoleCode), finishedAt: formatDateTime(ctx.finishedAt), roundCount: 1 })
  return [...byId.values()].sort((a, b) => a.seq - b.seq).map(({ seq: _seq, ...node }) => node)
}
function stepStatusText(status: WorkflowProcessStep['status'], record?: ModuleStateRecord) {
  if (status === 'current') return runtimeView.value?.canOperate ? '待我办理' : '处理中'
  if (status === 'waiting') return '待进行'
  if (status === 'returned') return '已退回'
  if (status === 'rejected') return '已驳回'
  return resultLabel(record?.result) || '已完成'
}function recordStepStatus(record: ModuleStateRecord): WorkflowProcessStep['status'] { if (isRejectedRecord(record)) return 'rejected'; if (isReturnedRecord(record)) return 'returned'; return 'done' }
function recordStatusText(record: ModuleStateRecord) { if (isRejectedRecord(record)) return '已驳回'; if (isReturnedRecord(record)) return '已退回'; return resultLabel(record.result) || '已完成' }
function isReturnedRecord(record: ModuleStateRecord) { const text = `${record.eventType ?? ''} ${record.result ?? ''} ${record.summary ?? ''}`.toUpperCase(); return text.includes('RETURN') || text.includes('退回') }
function isRejectedRecord(record: ModuleStateRecord) { const text = `${record.eventType ?? ''} ${record.result ?? ''} ${record.summary ?? ''}`.toUpperCase(); return text.includes('REJECT') || text.includes('FAIL') || text.includes('驳回') }
function formatDateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function resultType(value?: string) { const text = String(value ?? '').toUpperCase(); if (text.includes('PASS') || text.includes('APPROVE')) return 'success'; if (text.includes('RETURN') || text.includes('REJECT') || text.includes('FAIL')) return 'warning'; return 'info' }
function statusTagType(status: string) { if (status === 'done') return 'success'; if (status === 'current') return 'primary'; if (status === 'returned') return 'warning'; if (status === 'rejected') return 'danger'; return 'info' }
function isVisibleProcessNode(node: WorkflowNodeDefinition) { const nodeType = String(node.nodeType ?? '').toUpperCase(); return nodeType !== 'START_EVENT' && nodeType !== 'GATEWAY' }
function toDisplayHistoryRecord(record: ModuleStateRecord): WorkflowDisplayHistoryRecord {
  const statusKind = recordStepStatus(record)
  const isRoundEndingRecord = statusKind === 'returned' || statusKind === 'rejected'
  const displayRoundNo = isRoundEndingRecord && typeof record.roundNo === 'number' ? Math.max(1, record.roundNo - 1) : record.roundNo ?? '-'
  const displayNodeId = record.fromNodeId || record.toNodeId || record.toState || record.eventType || String(record.stateRecordId)
  return {
    id: record.stateRecordId,
    source: record,
    displayRoundNo,
    displayNodeId,
    statusKind,
    time: formatDateTime(record.createdAt),
    nodeName: workflowNodeName(displayNodeId, record),
    action: record.result || record.eventType || '',
    actionText: recordStatusText(record),
    operator: eventLabel(record.eventType),
    role: `第 ${displayRoundNo} 轮`,
    remark: record.summary || stateLabel(record.toState),
  }
}
function workflowNodeName(nodeId: string, record?: ModuleStateRecord) {
  const node = workflowNodes.value.find((item) => item.nodeId === nodeId)
  return node?.nodeName || nodeId || stateLabel(record?.toState) || eventLabel(record?.eventType) || `记录 ${record?.seq ?? '-'}`
}
function isActionReturned(value?: string) { const text = String(value ?? '').toUpperCase(); return text.includes('RETURN') || text.includes('REJECT') || text.includes('FAIL') || text.includes('退回') || text.includes('驳回') }
function isActionRejected(value?: string) { const text = String(value ?? '').toUpperCase(); return text.includes('REJECT') || text.includes('FAIL') || text.includes('驳回') }
function buildRoundGroup(roundNo: number | string, records: WorkflowDisplayRecord[]): RoundGroup { const first = records[0]; const last = records[records.length - 1]; const hasRejected = records.some((record) => isActionRejected(record.action) || isActionRejected(record.remark) || record.actionText === '已驳回'); const hasReturned = records.some((record) => isActionReturned(record.action) || isActionReturned(record.remark) || record.actionText === '已退回'); return { roundNo, resultText: hasRejected ? '已驳回' : hasReturned ? '已退回' : '正常流转', summary: last?.remark || '暂无摘要', startAt: first?.time || '-', endAt: last?.time || '-', records } }
function selectNode(node: WorkflowProcessStep) { selectedNodeId.value = node.id }
function openBpmnLarge() { bpmnPanelRef.value?.openLarge() }
async function loadProjectSummary(projectId: number) { try { const projects = await listProjects(); projectSummary.value = projects.find((project) => project.projectId === projectId) ?? null } catch { projectSummary.value = null } }
async function loadMaterials(projectId: number) { try { materials.value = await listProjectMaterials(projectId) } catch { materials.value = [] } }
async function loadWorkflowNodes(workflowDefinitionId: number) {
  try {
    workflowNodes.value = await getWorkflowNodes(workflowDefinitionId)
  } catch {
    workflowNodes.value = []
  }
}
function isBlankFormValue(value: unknown) {
  return value === undefined || value === null || value === ''
}

function validateRequiredNodeFormFields() {
  const kind = currentDataKind.value
  const missing = requiredFieldsForKind(kind).filter((key) => isBlankFormValue((workflowDraft.value.formModel as Record<string, unknown>)[key]))
  if (missing.length) {
    const labels: Record<string, string> = {
      receivedAmount: '到账经费',
      spentAmount: '支出经费',
      noticeType: '通知类型',
      noticeTitle: '通知标题',
      achievementType: '成果类型',
      achievementTitle: '成果名称',
    }
    ElMessage.warning(`请先填写必填业务字段：${missing.map((key) => labels[key] || key).join('、')}`)
    return false
  }
  return true
}

function buildCurrentFormPayload(result?: string): WorkflowNodeSubmitPayload {
  const view = runtimeView.value
  if (!view) return {}
  const form = view.nodeForms?.find((item) => item.writeMode !== 'READ_ONLY') ?? view.nodeForms?.[0]
  const kind = form?.dataKind
  const data = buildRequestFromFlatForm(
    kind,
    {
      ...defaultsForKind(kind),
      ...workflowDraft.value.formModel,
      approved: result ? result === 'APPROVED' : workflowDraft.value.formModel.approved !== false,
      remark: workflowDraft.value.remark,
      financeReviewComment: workflowDraft.value.formModel.financeReviewComment || workflowDraft.value.remark,
    },
    { projectId: view.context.projectId, moduleInstanceId: view.context.moduleInstanceId },
    form,
  )
  return {
    formCode: form?.formCode,
    data,
    result: result || (workflowDraft.value.formModel.approved !== false ? 'APPROVED' : 'REJECTED'),
    remark: workflowDraft.value.remark,
  }
}

function buildQuickPayload(approved: boolean): WorkflowNodeSubmitPayload {
  const view = runtimeView.value
  if (!view) return {}
  const form = view.nodeForms?.find((item) => item.writeMode !== 'READ_ONLY') ?? view.nodeForms?.[0]
  const kind = form?.dataKind
  const baseDefaults = defaultsForKind(kind)
  const flatForm = {
    ...baseDefaults,
    ...workflowDraft.value.formModel,
    approved,
    remark: workflowDraft.value.remark,
  }
  const data = buildRequestFromFlatForm(
    kind,
    flatForm,
    { projectId: view.context.projectId, moduleInstanceId: view.context.moduleInstanceId },
    form,
  )
  return {
    formCode: form?.formCode,
    data,
    result: approved ? 'APPROVED' : 'REJECTED',
    remark: workflowDraft.value.remark,
  }
}

async function quickApprove() {
  if (!canSubmit.value) return
  workflowDraft.value.payload = buildQuickPayload(true)
  await submitCurrentDraft()
}

async function quickReject() {
  if (!canSubmit.value) return
  workflowDraft.value.payload = buildQuickPayload(false)
  await submitCurrentDraft()
}

async function quickSubmitGeneric() {
  if (!canSubmit.value || !validateRequiredNodeFormFields()) return
  workflowDraft.value.payload = buildCurrentFormPayload()
  await submitCurrentDraft()
}

async function quickSubmitFinancialSettlement() {
  if (!canSubmit.value || !validateRequiredNodeFormFields()) return
  workflowDraft.value.payload = buildCurrentFormPayload('APPROVED')
  await submitCurrentDraft()
}

async function loadDetail(showMessage = false) {
  if (!Number.isFinite(moduleInstanceId.value)) { ElMessage.error('模块实例 ID 无效'); return }
  loading.value = true
  try {
    const view = await getRuntimeView(moduleInstanceId.value)
    runtimeView.value = view
    ensureDraft(view)
    await Promise.all([loadProjectSummary(view.context.projectId), loadMaterials(view.context.projectId)])
    if (view.context.workflowDefinitionId) {
      await Promise.all([
        loadWorkflowNodes(view.context.workflowDefinitionId),
        getWorkflowBpmn(view.context.workflowDefinitionId).then((bpmn) => { bpmnXml.value = bpmn.bpmnXml }),
      ])
    }
    if (showMessage) ElMessage.success('流程详情已刷新，当前节点草稿已保留')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '流程详情加载失败') } finally { loading.value = false }
}
function validateRequiredMaterials() {
  const view = runtimeView.value
  if (!view) return true
  const selectedTypes = new Set(materials.value.filter((item) => workflowDraft.value.selectedMaterialVersionIds.includes(item.materialVersionId) && item.isCurrent).map((item) => item.materialTypeCode))
  const missing = view.materialRequirements.filter((requirement) => Boolean(requirement.required && requirement.materialTypeCode && !selectedTypes.has(requirement.materialTypeCode)))
  if (missing.length) { ElMessage.warning(`请先选择或上传必填材料的当前版本：${missing.map((item) => item.materialTypeName || item.materialTypeCode).join('、')}`); return false }
  return true
}
async function submitCurrentDraft() {
  const view = runtimeView.value
  const transition = selectedTransition.value
  if (!view || !transition || !validateRequiredMaterials() || !validateRequiredNodeFormFields()) return
  const payload = workflowDraft.value.payload
  const currentDraftKey = workflowDraft.value.key
  const request: StateTransitionRequest = { eventType: transition.eventType, expectedSeq: view.context.currentSeq, result: payload.result || transition.result, remark: workflowDraft.value.remark || payload.remark, materialVersionIds: Array.from(new Set(workflowDraft.value.selectedMaterialVersionIds)), formCode: payload.formCode, nodeFormData: payload.data }
  submitting.value = true
  try { await submitStateTransition(moduleInstanceId.value, request); draftCache.delete(currentDraftKey); formDrawerOpen.value = false; ElNotification.success({ title: '提交成功', message: '已根据后端最新运行时视图刷新页面。' }); await loadDetail() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '流程提交失败'); await loadDetail() }
  finally { submitting.value = false }
}
watch(processNodes, (nodes) => { if (nodes.length && (!selectedNodeId.value || !nodes.some((node) => node.id === selectedNodeId.value))) selectedNodeId.value = nodes.find((node) => node.status === 'current')?.id ?? nodes[nodes.length - 1].id })
const workflowEvents = useWorkflowEvents((event) => { if (!event.moduleInstanceId || event.moduleInstanceId === moduleInstanceId.value) loadDetail() })
onMounted(() => { loadDetail(); workflowEvents.connect() })
</script>

<template>
  <AppShell>
    <el-skeleton v-if="loading && !runtimeView" animated :rows="10" />
    <el-empty v-else-if="!runtimeView" description="未加载到流程详情" />

    <main v-else class="workflow-detail-stack-page">
      <section class="workflow-detail-stack-hero">
        <div class="workflow-detail-stack-hero-main">
          <el-button class="workflow-detail-stack-back-button" text :icon="ArrowLeft" @click="router.push({ name: 'workflow' })">返回</el-button>
          <div class="workflow-detail-stack-hero-text">
            <div class="workflow-detail-stack-title-line">
              <h1>{{ moduleTitle }}</h1>
              <el-tag :type="statusTag.type" effect="light">{{ statusTag.text }}</el-tag>
              <el-tag type="warning" effect="light">第 {{ runtimeView.context.currentRoundNo ?? '-' }} 轮</el-tag>
            </div>
            <p>{{ pageTitle }}</p>
          </div>
        </div>
        <div class="workflow-detail-stack-hero-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadDetail(true)">刷新</el-button>
          <el-button :icon="Document" @click="formDrawerOpen = true">查看材料</el-button>
          <el-button type="primary" :icon="EditPen" :disabled="!runtimeView.canOperate" @click="formDrawerOpen = true">办理当前节点</el-button>
        </div>
      </section>

      <section class="workflow-detail-stack-summary-strip">
        <div v-for="item in summaryItems" :key="item.label" class="workflow-detail-stack-summary-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <section class="workflow-detail-stack-content">
        <el-card shadow="never" class="workflow-detail-stack-section-card workflow-detail-stack-detail-card">
          <template #header>
            <div class="workflow-detail-stack-card-title-row compact">
              <div>
                <h2>项目详细信息</h2>
                <p>保留项目详情，以堆叠折叠方式收纳。</p>
              </div>
              <el-tag type="info" effect="plain">{{ moduleTypeLabel(runtimeView.context.moduleType) }}</el-tag>
            </div>
          </template>
          <el-collapse v-model="openedDetailPanels" class="workflow-detail-stack-detail-collapse">
            <el-collapse-item title="基础信息" name="base">
              <el-descriptions :column="3" border>
                <el-descriptions-item label="项目编号">{{ projectSummary?.projectCode || '-' }}</el-descriptions-item>
                <el-descriptions-item label="项目名称">{{ projectSummary?.projectName || pageTitle }}</el-descriptions-item>
                <el-descriptions-item label="生命周期">{{ lifecycleLabel(projectSummary?.lifecycleStage) }}</el-descriptions-item>
                <el-descriptions-item label="负责人">{{ projectSummary?.leaderRealName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="所属单位">{{ projectSummary?.deptName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="模块实例">{{ runtimeView.context.moduleInstanceId }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
            <el-collapse-item title="项目属性与流程状态" name="attribute">
              <el-descriptions :column="3" border>
                <el-descriptions-item label="项目类型">{{ projectSummary?.projectType || '-' }}</el-descriptions-item>
                <el-descriptions-item label="项目级别">{{ projectSummary?.projectLevel || '-' }}</el-descriptions-item>
                <el-descriptions-item label="模块类型">{{ moduleTypeLabel(runtimeView.context.moduleType) }}</el-descriptions-item>
                <el-descriptions-item label="当前状态">{{ stateLabel(runtimeView.context.currentState) }}</el-descriptions-item>
                <el-descriptions-item label="当前序号">{{ runtimeView.context.currentSeq ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="最近更新">{{ formatDateTime(runtimeView.context.lastTransitionTime) }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
            <el-collapse-item title="参与角色与补充信息" name="extra">
              <div class="workflow-detail-stack-tag-block">
                <span>候选角色</span>
                <div>
                  <el-tag effect="plain">{{ roleLabel(runtimeView.context.currentCandidateRoleCode) }}</el-tag>
                  <el-tag v-if="runtimeView.context.currentLaneName" type="primary" effect="light">{{ runtimeView.context.currentLaneName }}</el-tag>
                </div>
              </div>
              <div class="workflow-detail-stack-tag-block">
                <span>最近结果</span>
                <div>
                  <el-tag :type="resultType(runtimeView.context.lastResult)" effect="light">{{ resultLabel(runtimeView.context.lastResult) }}</el-tag>
                  <el-tag type="info" effect="plain">{{ eventLabel(runtimeView.context.lastEventType) }}</el-tag>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>

        <el-card shadow="never" class="workflow-detail-stack-section-card workflow-detail-stack-process-card">
          <template #header>
            <div class="workflow-detail-stack-card-title-row">
              <div>
                <h2>流程办理进度</h2>
                <p>点击节点查看该节点状态与处理说明。</p>
              </div>
              <el-tag type="info" effect="plain">{{ processNodes.length }} 步</el-tag>
            </div>
          </template>
          <ProcessSteps :nodes="processNodes" :selected-node-id="selectedNodeId" @select-node="selectNode" />
          <div v-if="selectedNode" class="workflow-detail-stack-selected-node-panel">
            <div class="workflow-detail-stack-selected-main">
              <span class="workflow-detail-stack-eyebrow">选中节点详情</span>
              <strong>{{ selectedNode.name }}</strong>
              <p>{{ selectedNode.desc || '暂无说明' }}</p>
            </div>
            <div class="workflow-detail-stack-selected-meta-row">
              <div class="workflow-detail-stack-selected-meta"><span>责任角色</span><strong>{{ selectedNode.actor || '-' }}</strong></div>
              <div class="workflow-detail-stack-selected-meta">
                <span>节点状态</span>
                <strong><el-tag :type="statusTagType(selectedNode.status)" size="small" effect="light">{{ selectedNode.statusText }}</el-tag></strong>
              </div>
              <div class="workflow-detail-stack-selected-meta"><span>完成时间</span><strong>{{ selectedNode.finishedAt || '未完成' }}</strong></div>
              <div class="workflow-detail-stack-selected-meta"><span>办理轮次</span><strong>{{ selectedNode.roundCount && selectedNode.roundCount > 1 ? `第 ${selectedNode.roundCount} 轮` : '首轮' }}</strong></div>
            </div>
          </div>
        </el-card>

        <section class="workflow-detail-stack-workbench-grid">
          <!-- ===== 当前办理任务 ===== -->
          <el-card shadow="never" class="workflow-detail-stack-section-card">
            <template #header>
              <div class="workflow-detail-stack-card-title-row compact">
                <div><h2>当前办理任务</h2><p>{{ quickAction.category === 'approval' ? '审批结点：请选择通过或不通过后提交。' : quickAction.category === 'draft' ? '草稿结点：可保存草稿或直接提交。' : '办理当前结点任务。' }}</p></div>
                <el-tag type="primary" effect="plain">{{ operationModeLabel(runtimeView.context.currentOperationMode) }}</el-tag>
              </div>
            </template>
            <div class="workflow-detail-stack-task-grid">
              <div v-for="item in currentNodeMeta" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
            </div>
            <el-alert class="workflow-detail-stack-task-alert" :type="runtimeView.canOperate ? 'success' : 'info'" :closable="false" show-icon>
              {{ runtimeView.canOperate ? '当前用户具备该节点办理权限。' : '当前用户不可办理此节点，仅可查看运行时状态。' }}
            </el-alert>

            <!-- Transition selector -->
            <div v-if="transitionOptions.length" class="workflow-detail-stack-action-row">
              <el-button v-for="transition in transitionOptions" :key="transitionKey(transition)" :type="workflowDraft.transitionKey === transitionKey(transition) ? 'primary' : ''" @click="workflowDraft.transitionKey = transitionKey(transition)">
                {{ resultLabel(transition.result) !== '-' ? resultLabel(transition.result) : eventLabel(transition.eventType) }}
              </el-button>
            </div>
            <div v-else class="workflow-detail-stack-action-row">
              <el-button disabled>暂无可执行动作</el-button>
            </div>

            <!-- Quick action area: different layouts per node type -->
            <div v-if="quickAction.supportsQuickAction && runtimeView.canOperate" class="workflow-detail-stack-quick-actions">
              <!-- Approval nodes: 通过/不通过 -->
              <template v-if="quickAction.category === 'approval'">
                <el-input v-model="workflowDraft.remark" class="workflow-detail-stack-remark" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="quickAction.remarkPlaceholder || '填写审批意见'" />
                <div class="workflow-detail-stack-submit-row">
                  <el-button @click="formDrawerOpen = true">打开办理面板</el-button>
                  <el-button type="success" :loading="submitting" :disabled="!canSubmit" @click="quickApprove">{{ quickAction.primaryLabel }}</el-button>
                  <el-button type="danger" :loading="submitting" :disabled="!canSubmit" @click="quickReject">{{ quickAction.secondaryLabel }}</el-button>
                </div>
              </template>

              <!-- Draft nodes: 保存草稿/提交草稿 -->
              <template v-else-if="quickAction.category === 'draft'">
                <el-input v-model="workflowDraft.remark" class="workflow-detail-stack-remark" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="quickAction.remarkPlaceholder || '填写办理说明'" />
                <div class="workflow-detail-stack-submit-row">
                  <el-button @click="formDrawerOpen = true">打开办理面板</el-button>
                  <el-button plain :loading="submitting" :disabled="!canSubmit" @click="saveDraft">{{ quickAction.secondaryLabel }}</el-button>
                  <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="quickSubmitGeneric">{{ quickAction.primaryLabel }}</el-button>
                </div>
              </template>

              <!-- Financial settlement node: core amount fields first -->
              <template v-else-if="isFinancialSettlementNode">
                <el-alert
                  title="请填写到账经费和支出经费，并上传经费决算与报销清单后提交。"
                  type="info"
                  :closable="false"
                  show-icon
                  class="workflow-detail-stack-quick-hint"
                />
                <el-form label-position="top" class="workflow-detail-stack-finance-form">
                  <el-row :gutter="12">
                    <el-col :xs="24" :sm="8">
                      <el-form-item label="批准经费">
                        <el-input-number v-model="workflowDraft.formModel.approvedAmount" :min="0" :precision="2" controls-position="right" class="wide-control" />
                      </el-form-item>
                    </el-col>
                    <el-col :xs="24" :sm="8">
                      <el-form-item label="到账经费" required>
                        <el-input-number v-model="workflowDraft.formModel.receivedAmount" :min="0" :precision="2" controls-position="right" class="wide-control" />
                      </el-form-item>
                    </el-col>
                    <el-col :xs="24" :sm="8">
                      <el-form-item label="支出经费" required>
                        <el-input-number v-model="workflowDraft.formModel.spentAmount" :min="0" :precision="2" controls-position="right" class="wide-control" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-form-item label="财务意见 / 办理说明">
                    <el-input v-model="workflowDraft.remark" class="workflow-detail-stack-remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="填写经费决算说明，可留空" />
                  </el-form-item>
                </el-form>
                <div class="workflow-detail-stack-submit-row">
                  <el-button @click="formDrawerOpen = true">打开完整办理面板</el-button>
                  <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="quickSubmitFinancialSettlement">完成决算并流转</el-button>
                </div>
              </template>

              <!-- Operation / Project record / Notice nodes: single submit -->
              <template v-else>
                <el-input v-model="workflowDraft.remark" class="workflow-detail-stack-remark" type="textarea" :rows="2" maxlength="500" show-word-limit :placeholder="quickAction.remarkPlaceholder || '填写办理说明'" />
                <div class="workflow-detail-stack-submit-row">
                  <el-button @click="formDrawerOpen = true">打开办理面板</el-button>
                  <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="quickSubmitGeneric">{{ quickAction.primaryLabel }}</el-button>
                </div>
              </template>
            </div>

            <!-- Expert review nodes: show core expert workflow directly -->
            <div v-else-if="isExpertNode && runtimeView.canOperate" class="workflow-detail-stack-quick-actions">
              <el-alert
                :title="quickAction.description"
                type="info"
                :closable="false"
                show-icon
                class="workflow-detail-stack-quick-hint"
              />
              <ExpertReviewPanel :view="runtimeView" embedded @changed="loadDetail()" />
              <div class="workflow-detail-stack-submit-row">
                <el-button :icon="EditPen" :disabled="!runtimeView.canOperate" @click="formDrawerOpen = true">打开完整办理面板</el-button>
                <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitCurrentDraft">完成并流转</el-button>
              </div>
            </div>

            <!-- Fallback when no quick action is supported -->
            <div v-else class="workflow-detail-stack-quick-actions">
              <el-input v-model="workflowDraft.remark" class="workflow-detail-stack-remark" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="quickAction.remarkPlaceholder || '填写审批意见或办理说明'" />
              <div class="workflow-detail-stack-submit-row">
                <el-button @click="formDrawerOpen = true">打开办理面板</el-button>
                <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitCurrentDraft">提交办理</el-button>
              </div>
            </div>
          </el-card>

          <!-- ===== 材料与意见 ===== -->
          <el-card shadow="never" class="workflow-detail-stack-section-card">
            <template #header>
              <div class="workflow-detail-stack-card-title-row compact"><div><h2>材料与意见</h2><p>材料上传、审批意见和风险提示。</p></div></div>
            </template>
            <el-collapse v-model="materialsOpinionsCollapse" class="workflow-detail-stack-clean-collapse">
              <!-- 材料附件 — 可交互上传/选择 -->
              <el-collapse-item title="📎 材料附件" name="materials">
                <div class="workflow-detail-stack-check-row">
                  <el-tag v-for="check in materialChecks" :key="check.label" :type="check.type" effect="light">{{ check.label }}：{{ check.value }}</el-tag>
                </div>
                <MaterialRequirementPanel
                  :project-id="runtimeView.context.projectId"
                  :requirements="runtimeView.materialRequirements"
                  :selected-ids="workflowDraft.selectedMaterialVersionIds"
                  @update:selected-ids="ids => workflowDraft.selectedMaterialVersionIds = ids"
                  @materials-changed="loadMaterials(runtimeView.context.projectId)"
                />
              </el-collapse-item>

              <!-- 审批意见 — 折叠 -->
              <el-collapse-item title="💬 审批意见" name="opinions">
                <el-empty v-if="!displayRecords.length" description="暂无审批意见" />
                <div v-else class="workflow-detail-stack-opinion-list">
                  <div v-for="record in displayRecords.slice().reverse().slice(0, 5)" :key="record.id" class="workflow-detail-stack-opinion-item">
                    <div><strong>{{ record.nodeName }} · {{ record.operator || '-' }}</strong><span>{{ record.time }}</span></div>
                    <el-tag size="small" :type="resultType(record.action)" effect="light">{{ record.actionText }}</el-tag>
                    <p>{{ record.remark || '暂无说明' }}</p>
                  </div>
                </div>
              </el-collapse-item>

              <!-- 风险提示 — 折叠 -->
              <el-collapse-item title="⚠️ 风险提示" name="risks">
                <div class="workflow-detail-stack-risk-list">
                  <el-alert v-for="risk in riskItems" :key="risk.id" :type="risk.type" :title="risk.title" :description="risk.desc" :closable="false" show-icon />
                </div>
              </el-collapse-item>
            </el-collapse>
          </el-card>
        </section>

        <el-card shadow="never" class="workflow-detail-stack-section-card">
          <template #header>
            <div class="workflow-detail-stack-card-title-row compact">
              <div><h2>本轮流转记录</h2><p>展示当前第 {{ currentRound.roundNo }} 轮，历史轮次默认折叠。</p></div>
              <el-tag type="primary" effect="plain">{{ currentRound.records.length }} 条</el-tag>
            </div>
          </template>
          <el-empty v-if="!currentRound.records.length" description="本轮暂无流转记录" />
          <RecordTimeline v-else :records="currentRound.records" />
        </el-card>

        <el-card shadow="never" class="workflow-detail-stack-section-card">
          <template #header>
            <div class="workflow-detail-stack-card-title-row compact">
              <div><h2>历史轮次</h2><p>默认折叠，仅在排查退回原因时展开。</p></div>
              <el-tag type="warning" effect="plain">{{ historyRounds.length }} 轮</el-tag>
            </div>
          </template>
          <el-empty v-if="!historyRounds.length" description="暂无历史轮次" />
          <el-collapse v-else accordion>
            <el-collapse-item v-for="round in historyRounds" :key="round.roundNo" :name="round.roundNo">
              <template #title>
                <div class="workflow-detail-stack-round-title">
                  <strong>第 {{ round.roundNo }} 轮</strong>
                  <el-tag size="small" type="warning" effect="light">{{ round.resultText }}</el-tag>
                  <span>{{ round.summary }}</span>
                  <em>{{ round.startAt }} ~ {{ round.endAt }}</em>
                </div>
              </template>
              <RecordTimeline :records="round.records" />
            </el-collapse-item>
          </el-collapse>
        </el-card>

        <el-card shadow="never" class="workflow-detail-stack-section-card workflow-detail-stack-helper-card">
          <template #header>
            <div class="workflow-detail-stack-card-title-row compact">
              <div><h2>辅助查看</h2><p>BPMN 与原始运行时信息按需展开。</p></div>
              <el-button :icon="FullScreen" plain @click="openBpmnLarge">展开 BPMN</el-button>
            </div>
          </template>
          <el-collapse v-model="helperPanels">
            <el-collapse-item title="BPMN 流程图预览" name="bpmn">
              <BpmnViewerPanel ref="bpmnPanelRef" :bpmn-xml="bpmnXml" :current-node-id="runtimeView.context.currentNodeId" :history="runtimeView.history" :open-tasks="runtimeView.openTasks" />
            </el-collapse-item>
            <el-collapse-item title="原始流程信息" name="runtime">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="流程定义">{{ runtimeView.context.workflowDefinitionId }}</el-descriptions-item>
                <el-descriptions-item label="当前 BPMN 节点">{{ runtimeView.context.currentNodeId || '-' }}</el-descriptions-item>
                <el-descriptions-item label="开始时间">{{ formatDateTime(runtimeView.context.startedAt) }}</el-descriptions-item>
                <el-descriptions-item label="完成时间">{{ formatDateTime(runtimeView.context.finishedAt) }}</el-descriptions-item>
                <el-descriptions-item label="最近摘要">{{ runtimeView.context.lastSummary || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>
        </el-card>
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
          @payload-change="updateDraftPayload"
          @materials-changed="loadMaterials(runtimeView.context.projectId)"
          @save-draft="saveDraft"
          @discard="discardDraft"
          @submit="submitCurrentDraft"
          @close="formDrawerOpen = false"
        />
      </el-drawer>
    </main>
  </AppShell>
</template>








