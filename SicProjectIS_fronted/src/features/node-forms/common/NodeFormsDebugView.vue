<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Refresh, UploadFilled } from '@element-plus/icons-vue'
import {
  createNodeFormRecord,
  deleteNodeFormRecord,
  getNodeForm,
  getNodeFormDefinitions,
  saveNodeForm,
  updateNodeFormRecord,
} from '../../../api/nodeForms'
import {
  deleteMaterialVersion,
  downloadMaterialVersion,
  listProjectMaterials,
  uploadMaterialVersion,
} from '../../../api/materials'
import {
  getRuntimeView,
  startModuleInstance,
  submitStateTransition,
} from '../../../api/stateMachine'
import { defaultsForKind, fieldType, fieldsForKind } from '../../../utils/nodeFormFields'
import type {
  MaterialContextView,
  NodeFormContext,
  NodeFormDataKind,
  NodeFormDataResponse,
  NodeFormDefinition,
  NodeFormSaveRequest,
  RuntimeViewResponse,
} from '../../../types/nodeForms'

const definitions = ref<NodeFormDefinition[]>([])
const selectedCode = ref('')
const loading = ref(false)
const saving = ref(false)
const data = ref<NodeFormDataResponse | null>(null)
const recordId = ref<number | undefined>()
const materialTypeCode = ref('APP_FORM')
const uploadBusy = ref(false)
const runtimeLoading = ref(false)
const runtimeView = ref<RuntimeViewResponse | null>(null)
const materialVersionIdsText = ref('')
const transitionForm = reactive({
  moduleType: 'APPLICATION',
  eventType: '',
  result: '',
  remark: '',
  includeNodeForm: false,
})
const context = reactive<NodeFormContext>({
  projectId: undefined,
  moduleInstanceId: undefined,
  stateRecordId: undefined,
})

const form = reactive<Record<string, string | number | boolean | undefined>>({})

const groupedDefinitions = computed(() => ({
  APPLICATION: definitions.value.filter((item) => item.moduleType === 'APPLICATION'),
  CONTRACT: definitions.value.filter((item) => item.moduleType === 'CONTRACT'),
  ACCEPTANCE: definitions.value.filter((item) => item.moduleType === 'ACCEPTANCE'),
}))

const selectedDefinition = computed(() => definitions.value.find((item) => item.formCode === selectedCode.value))
const isSingleInstance = computed(() => selectedDefinition.value?.writeMode === 'SINGLE_INSTANCE')
const isReadOnly = computed(() => selectedDefinition.value?.writeMode === 'READ_ONLY')
const visibleRecords = computed(() => recordsForKind(selectedDefinition.value?.dataKind, data.value))
const materials = ref<MaterialContextView[]>([])
const missingContextFields = computed(() => requiredContextFields(selectedDefinition.value).filter((field) => !context[field]))
const contextWarning = computed(() => {
  if (!missingContextFields.value.length) return ''
  return `${missingContextFields.value.join(', ')} required for ${selectedDefinition.value?.formCode}`
})
const canSubmit = computed(() => Boolean(selectedDefinition.value && !isReadOnly.value && missingContextFields.value.length === 0))
const transitionOptions = computed(() => runtimeView.value?.availableTransitions ?? [])

onMounted(async () => {
  definitions.value = await getNodeFormDefinitions()
  selectedCode.value = definitions.value[0]?.formCode ?? ''
})

watch(selectedCode, () => {
  recordId.value = undefined
  resetForm()
  data.value = null
})

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
  const kind = selectedDefinition.value?.dataKind
  Object.assign(form, defaultsForKind(kind))
}

async function loadCurrent() {
  if (!selectedCode.value) return
  loading.value = true
  try {
    data.value = await getNodeForm(selectedCode.value, normalizeContext())
    materials.value = context.projectId ? await listProjectMaterials(context.projectId) : []
    fillFromLatest()
  } finally {
    loading.value = false
  }
}

async function startRuntimeModule() {
  if (!context.projectId) {
    ElMessage.warning('Project ID is required')
    return
  }
  runtimeLoading.value = true
  try {
    const response = await startModuleInstance(context.projectId, transitionForm.moduleType)
    context.moduleInstanceId = response.stateRecord.moduleInstanceId
    context.stateRecordId = response.stateRecord.stateRecordId
    ElMessage.success('Module started')
    await loadRuntimeView()
  } finally {
    runtimeLoading.value = false
  }
}

async function loadRuntimeView() {
  if (!context.moduleInstanceId) {
    ElMessage.warning('Module Instance ID is required')
    return
  }
  runtimeLoading.value = true
  try {
    runtimeView.value = await getRuntimeView(context.moduleInstanceId)
    context.projectId = runtimeView.value.context.projectId
    context.stateRecordId = runtimeView.value.history.at(-1)?.stateRecordId
    transitionForm.eventType = runtimeView.value.availableTransitions[0]?.eventType ?? ''
    transitionForm.result = runtimeView.value.availableTransitions[0]?.result ?? ''
  } finally {
    runtimeLoading.value = false
  }
}

async function submitRuntimeTransition() {
  if (!context.moduleInstanceId || !transitionForm.eventType) {
    ElMessage.warning('Module Instance ID and eventType are required')
    return
  }
  runtimeLoading.value = true
  try {
    const nodeFormData = transitionForm.includeNodeForm && selectedDefinition.value ? buildRequest() : undefined
    const response = await submitStateTransition(context.moduleInstanceId, {
      eventType: transitionForm.eventType,
      expectedSeq: runtimeView.value?.context.currentSeq,
      result: transitionForm.result || undefined,
      remark: transitionForm.remark || undefined,
      materialVersionIds: parseIds(materialVersionIdsText.value),
      formCode: transitionForm.includeNodeForm ? selectedCode.value : undefined,
      nodeFormData,
    })
    context.stateRecordId = response.stateRecord.stateRecordId
    ElMessage.success(response.finished ? 'Transition completed and process finished' : 'Transition completed')
    await loadRuntimeView()
    if (selectedCode.value) {
      await loadCurrent()
    }
  } finally {
    runtimeLoading.value = false
  }
}

async function saveCurrent() {
  if (!selectedDefinition.value) return
  if (!canSubmit.value) {
    ElMessage.warning(contextWarning.value)
    return
  }
  saving.value = true
  try {
    const request = buildRequest()
    if (isSingleInstance.value) {
      data.value = await saveNodeForm(selectedDefinition.value.formCode, request)
      ElMessage.success('Saved')
    } else if (recordId.value) {
      const response = await updateNodeFormRecord(selectedDefinition.value.formCode, recordId.value, request)
      data.value = response.data
      ElMessage.success('Updated')
    } else {
      const response = await createNodeFormRecord(selectedDefinition.value.formCode, request)
      recordId.value = response.recordId
      data.value = response.data
      ElMessage.success('Created')
    }
    if (context.projectId) {
      materials.value = await listProjectMaterials(context.projectId)
    }
  } finally {
    saving.value = false
  }
}

async function removeRecord(row: Record<string, unknown>) {
  if (!selectedDefinition.value) return
  const id = recordPrimaryId(selectedDefinition.value.dataKind, row)
  if (!id) return
  await ElMessageBox.confirm(`Delete record ${id}?`, 'Delete', {
    type: 'warning',
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
  })
  await deleteNodeFormRecord(selectedDefinition.value.formCode, Number(id), normalizeContext())
  ElMessage.success('Deleted')
  await loadCurrent()
}

function editRecord(row: Record<string, unknown>) {
  if (!selectedDefinition.value) return
  recordId.value = Number(recordPrimaryId(selectedDefinition.value.dataKind, row))
  resetForm()
  Object.assign(form, row)
}

async function uploadFile(options: { file: File; onSuccess: () => void; onError: (error: Error) => void }) {
  if (!context.projectId) {
    options.onError(new Error('Project id is required'))
    ElMessage.error('Project id is required')
    return
  }
  uploadBusy.value = true
  try {
    await uploadMaterialVersion(context.projectId, materialTypeCode.value, options.file)
    materials.value = await listProjectMaterials(context.projectId)
    options.onSuccess()
    ElMessage.success('Uploaded')
  } catch (error) {
    options.onError(error as Error)
  } finally {
    uploadBusy.value = false
  }
}

async function deleteFile(id: number) {
  await deleteMaterialVersion(id)
  if (context.projectId) {
    materials.value = await listProjectMaterials(context.projectId)
  }
}

function fillFromLatest() {
  if (!selectedDefinition.value || !data.value) return
  resetForm()
  const latest = visibleRecords.value.at(-1)
  if (latest) {
    Object.assign(form, latest)
    const id = recordPrimaryId(selectedDefinition.value.dataKind, latest as Record<string, unknown>)
    recordId.value = id ? Number(id) : undefined
    return
  }
  const draft = draftForKind(selectedDefinition.value.dataKind, data.value)
  if (draft && typeof draft === 'object') {
    Object.assign(form, extractDraftFields(selectedDefinition.value.dataKind, draft as Record<string, unknown>))
  }
}

function buildRequest(): NodeFormSaveRequest {
  const kind = selectedDefinition.value?.dataKind
  const base = {
    projectId: context.projectId,
    moduleInstanceId: context.moduleInstanceId,
    stateRecordId: context.stateRecordId,
  }
  if (kind === 'APPLICATION_DRAFT') {
    return {
      ...base,
      applicationDraft: {
        application: {
          applicationTitle: form.applicationTitle,
          isLimitedProject: form.isLimitedProject,
          applicationSummary: form.applicationSummary,
        },
        extension: {
          moduleInstanceId: context.moduleInstanceId,
          applicationCategory: form.applicationCategory,
          expectedBudget: numberOrUndefined(form.expectedBudget),
          isLimitedProject: form.isLimitedProject,
        },
        detail: {
          researchBackground: form.researchBackground,
          researchObjective: form.researchObjective,
          researchContent: form.researchContent,
          expectedOutcomes: form.expectedOutcomes,
        },
      },
    }
  }
  if (kind === 'CONTRACT_DRAFT') {
    return {
      ...base,
      contractDraft: {
        contract: {
          contractCode: form.contractCode,
          contractName: form.contractName,
          contractAmount: numberOrUndefined(form.contractAmount),
          sealStatus: form.sealStatus,
        },
        extension: {
          moduleInstanceId: context.moduleInstanceId,
          partyAName: form.partyAName,
          partyBName: form.partyBName,
          contractSource: form.contractSource,
        },
      },
    }
  }
  if (kind === 'ACCEPTANCE_DRAFT') {
    return {
      ...base,
      acceptanceDraft: {
        acceptance: {
          certificateNo: form.certificateNo,
          conclusion: form.conclusion,
        },
        extension: {
          moduleInstanceId: context.moduleInstanceId,
          acceptanceType: form.acceptanceType,
          taskCompletionRate: numberOrUndefined(form.taskCompletionRate),
          acceptanceBatchNo: form.acceptanceBatchNo,
        },
      },
    }
  }
  if (kind === 'NOTICE') {
    return {
      ...base,
      notice: {
        moduleType: selectedDefinition.value?.moduleType,
        noticeType: form.noticeType,
        noticeTitle: form.noticeTitle,
        noticeNo: form.noticeNo,
        publishUnit: form.publishUnit,
        noticeScope: form.noticeScope,
        contentSummary: form.contentSummary,
        materialRequirementSummary: form.materialRequirementSummary,
      },
    }
  }
  if (kind === 'CHECK_ITEM') {
    return { ...base, runtimeRecord: { checkItem: { ...form, moduleInstanceId: context.moduleInstanceId, stateRecordId: context.stateRecordId } } }
  }
  if (kind === 'EXTERNAL_RESULT') {
    return { ...base, runtimeRecord: { externalResult: { ...form, approvedAmount: numberOrUndefined(form.approvedAmount) } } }
  }
  if (kind === 'SEAL') {
    return { ...base, runtimeRecord: { sealRecord: { ...form, copyCount: numberOrUndefined(form.copyCount) } } }
  }
  if (kind === 'SUBMISSION') {
    return { ...base, runtimeRecord: { submissionRecord: { ...form } } }
  }
  if (kind === 'ARCHIVE') {
    return {
      ...base,
      runtimeRecord: {
        archiveRecord: {
          ...form,
          paperCopyCount: numberOrUndefined(form.paperCopyCount),
          electronicCopyCount: numberOrUndefined(form.electronicCopyCount),
        },
      },
    }
  }
  if (kind === 'PUBLICITY') {
    return { ...base, projectRecord: { publicity: { ...form, moduleInstanceId: context.moduleInstanceId, stateRecordId: context.stateRecordId } } }
  }
  if (kind === 'FINANCIAL_SETTLEMENT') {
    return {
      ...base,
      projectRecord: {
        financialSettlement: {
          moduleInstanceId: context.moduleInstanceId,
          stateRecordId: context.stateRecordId,
          approvedAmount: numberOrUndefined(form.approvedAmount),
          receivedAmount: numberOrUndefined(form.receivedAmount),
          spentAmount: numberOrUndefined(form.spentAmount),
          settlementResult: form.settlementResult,
          financeReviewComment: form.financeReviewComment,
        },
      },
    }
  }
  if (kind === 'ACHIEVEMENT') {
    return {
      ...base,
      projectRecord: {
        achievement: {
          moduleInstanceId: context.moduleInstanceId,
          achievementType: form.achievementType,
          achievementTitle: form.achievementTitle,
          authorList: form.authorList,
          achievementLevel: form.achievementLevel,
          proofMaterialVersionId: numberOrUndefined(form.proofMaterialVersionId),
          remark: form.remark,
        },
      },
    }
  }
  if (kind === 'SURPLUS_RETURN') {
    return {
      ...base,
      projectRecord: {
        surplusReturn: {
          ...form,
          moduleInstanceId: context.moduleInstanceId,
          stateRecordId: context.stateRecordId,
          surplusAmount: numberOrUndefined(form.surplusAmount),
          returnedAmount: numberOrUndefined(form.returnedAmount),
        },
      },
    }
  }
  if (kind === 'EXPERT_REVIEW') {
    return {
      ...base,
      expertReview: {
        batchId: numberOrUndefined(form.batchId),
        assignmentId: numberOrUndefined(form.assignmentId),
        createBatch: form.reviewTitle
          ? {
              moduleInstanceId: context.moduleInstanceId,
              workflowNodeId: selectedDefinition.value?.nodeId,
              reviewType: selectedDefinition.value?.stateCode,
              reviewTitle: form.reviewTitle,
              ruleType: form.ruleType,
              minExpertCount: numberOrUndefined(form.minExpertCount),
              passScore: numberOrUndefined(form.passScore),
              recommendScore: numberOrUndefined(form.recommendScore),
              removeHighestLowest: form.removeHighestLowest,
              expectedExpertCount: numberOrUndefined(form.expectedExpertCount),
            }
          : undefined,
        assignExpert: form.expertUserId
          ? {
              expertUserId: numberOrUndefined(form.expertUserId),
              expertName: form.expertName,
              expertOrg: form.expertOrg,
              expertTitle: form.expertTitle,
            }
          : undefined,
        submitScore: form.scoreValue
          ? {
              conflictOfInterest: form.conflictOfInterest,
              valid: form.valid,
              reviewResult: form.reviewResult,
              reviewComment: form.reviewComment,
              scores: [
                {
                  itemCode: form.itemCode,
                  itemName: form.itemName,
                  weight: numberOrUndefined(form.weight),
                  maxScore: numberOrUndefined(form.maxScore),
                  scoreValue: numberOrUndefined(form.scoreValue),
                  comment: form.comment,
                },
              ],
            }
          : undefined,
      },
    }
  }
  return base
}

function requiredContextFields(definition?: NodeFormDefinition): Array<keyof NodeFormContext> {
  if (!definition || definition.writeMode === 'READ_ONLY') return []
  if (definition.writeMode === 'SINGLE_INSTANCE') return ['projectId']
  switch (definition.dataKind) {
    case 'NOTICE':
      return []
    case 'CHECK_ITEM':
    case 'EXTERNAL_RESULT':
    case 'SEAL':
    case 'SUBMISSION':
    case 'ARCHIVE':
      return ['projectId', 'moduleInstanceId', 'stateRecordId']
    case 'PUBLICITY':
    case 'FINANCIAL_SETTLEMENT':
    case 'SURPLUS_RETURN':
    case 'EXPERT_REVIEW':
      return ['projectId', 'moduleInstanceId']
    case 'ACHIEVEMENT':
      return ['projectId']
    default:
      return []
  }
}


// fieldsForKind, defaultsForKind, fieldType are now imported from utils/nodeFormFields


function recordsForKind(kind?: NodeFormDataKind, source?: NodeFormDataResponse | null) {
  if (!source) return []
  switch (kind) {
    case 'NOTICE': return source.notices
    case 'CHECK_ITEM': return source.checkItems
    case 'EXTERNAL_RESULT': return source.externalResults
    case 'SEAL': return source.sealRecords
    case 'SUBMISSION': return source.submissionRecords
    case 'ARCHIVE': return source.archiveRecords
    case 'PUBLICITY': return source.publicities
    case 'FINANCIAL_SETTLEMENT': return source.financialSettlements
    case 'ACHIEVEMENT': return source.achievements
    case 'SURPLUS_RETURN': return source.surplusReturns
    case 'EXPERT_REVIEW': return source.expertReview ? [source.expertReview] : []
    default: return []
  }
}

function draftForKind(kind: NodeFormDataKind, source: NodeFormDataResponse) {
  if (kind === 'APPLICATION_DRAFT') return source.applicationDraft
  if (kind === 'CONTRACT_DRAFT') return source.contractDraft
  if (kind === 'ACCEPTANCE_DRAFT') return source.acceptanceDraft
  return undefined
}

function extractDraftFields(kind: NodeFormDataKind, draft: Record<string, unknown>) {
  if (kind === 'APPLICATION_DRAFT') return { ...(draft.application as object), ...(draft.extension as object), ...(draft.detail as object) }
  if (kind === 'CONTRACT_DRAFT') return { ...(draft.contract as object), ...(draft.extension as object) }
  if (kind === 'ACCEPTANCE_DRAFT') return { ...(draft.acceptance as object), ...(draft.extension as object) }
  return {}
}

function recordPrimaryId(kind: NodeFormDataKind, row: Record<string, unknown>) {
  const keyByKind: Record<NodeFormDataKind, string> = {
    NOTICE: 'noticeId',
    APPLICATION_DRAFT: 'applicationId',
    CONTRACT_DRAFT: 'contractId',
    ACCEPTANCE_DRAFT: 'acceptanceId',
    CHECK_ITEM: 'checkItemId',
    EXPERT_REVIEW: 'batch.batchId',
    PUBLICITY: 'publicityId',
    EXTERNAL_RESULT: 'externalResultId',
    SEAL: 'sealRecordId',
    SUBMISSION: 'submissionId',
    ARCHIVE: 'archiveId',
    FINANCIAL_SETTLEMENT: 'settlementId',
    ACHIEVEMENT: 'achievementId',
    SURPLUS_RETURN: 'returnId',
    DOCUMENT: 'documentId',
  }
  const key = keyByKind[kind]
  if (key.includes('.')) {
    const [head, tail] = key.split('.')
    return (row[head] as Record<string, unknown> | undefined)?.[tail]
  }
  return row[key]
}

function normalizeContext(): NodeFormContext {
  return {
    projectId: numberOrUndefined(context.projectId),
    moduleInstanceId: numberOrUndefined(context.moduleInstanceId),
    stateRecordId: numberOrUndefined(context.stateRecordId),
  }
}

function numberOrUndefined(value: unknown) {
  if (value === undefined || value === null || value === '') return undefined
  const next = Number(value)
  return Number.isNaN(next) ? undefined : next
}

function parseIds(value: string) {
  return value
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => !Number.isNaN(item))
}

// fieldType is now imported from utils/nodeFormFields
</script>

<template>
  <div class="node-form-debug page-stack">
    <section class="page-header">
      <div>
        <p class="eyebrow">Node Forms</p>
        <h2 class="section-heading">Form Debug Workbench</h2>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadCurrent">Load</el-button>
    </section>

    <section class="toolbar-form">
      <el-form label-position="top">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="8">
            <el-form-item label="Project ID">
              <el-input-number v-model="context.projectId" :min="1" controls-position="right" class="wide-control" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="Module Instance ID">
              <el-input-number v-model="context.moduleInstanceId" :min="1" controls-position="right" class="wide-control" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="State Record ID">
              <el-input-number v-model="context.stateRecordId" :min="1" controls-position="right" class="wide-control" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </section>

    <section class="panel runtime-panel">
      <div class="card-title-row">
        <h3>State Machine</h3>
        <div class="toolbar-actions">
          <el-button :loading="runtimeLoading" @click="loadRuntimeView">Runtime View</el-button>
          <el-button type="primary" :loading="runtimeLoading" @click="startRuntimeModule">Start Module</el-button>
        </div>
      </div>
      <el-form label-position="top">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="6">
            <el-form-item label="Module Type">
              <el-select v-model="transitionForm.moduleType" class="wide-control">
                <el-option label="APPLICATION" value="APPLICATION" />
                <el-option label="CONTRACT" value="CONTRACT" />
                <el-option label="ACCEPTANCE" value="ACCEPTANCE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="Event">
              <el-select v-model="transitionForm.eventType" filterable allow-create class="wide-control">
                <el-option
                  v-for="transition in transitionOptions"
                  :key="transition.transitionId"
                  :label="`${transition.eventType} -> ${transition.targetStateCode || transition.targetRef}`"
                  :value="transition.eventType"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="Result">
              <el-input v-model="transitionForm.result" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="Material Version IDs">
              <el-input v-model="materialVersionIdsText" placeholder="1,2,3" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="18">
            <el-form-item label="Remark">
              <el-input v-model="transitionForm.remark" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="Node Form Data">
              <el-checkbox v-model="transitionForm.includeNodeForm">Use selected form</el-checkbox>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div class="runtime-summary">
        <el-tag v-if="runtimeView?.context.currentState" type="success">{{ runtimeView.context.currentState }}</el-tag>
        <el-tag v-if="runtimeView?.context.currentNodeName">{{ runtimeView.context.currentNodeName }}</el-tag>
        <el-tag v-if="runtimeView?.context.currentSeq !== undefined" type="info">seq {{ runtimeView.context.currentSeq }}</el-tag>
        <el-button type="primary" :loading="runtimeLoading" :disabled="!transitionForm.eventType" @click="submitRuntimeTransition">Submit Transition</el-button>
      </div>
      <el-table v-if="runtimeView" :data="runtimeView.availableTransitions" border size="small" class="debug-table">
        <el-table-column prop="eventType" label="Event" width="240" />
        <el-table-column prop="targetStateCode" label="Target State" width="220" />
        <el-table-column prop="conditionType" label="Condition" width="120" />
        <el-table-column prop="conditionKey" label="Condition Key" width="220" />
        <el-table-column prop="conditionHandlerKey" label="Handler" width="240" />
        <el-table-column label="Actions" min-width="260">
          <template #default="{ row }">{{ row.actionKeys?.join(', ') }}</template>
        </el-table-column>
      </el-table>
      <el-table v-if="runtimeView" :data="runtimeView.materialRequirements" border size="small" class="debug-table">
        <el-table-column prop="materialTypeCode" label="Required Material" width="220" />
        <el-table-column prop="required" label="Required" width="100" />
        <el-table-column prop="minCount" label="Min" width="80" />
        <el-table-column prop="maxCount" label="Max" width="80" />
        <el-table-column prop="validatorKey" label="Validator" width="220" />
        <el-table-column prop="description" label="Description" />
      </el-table>
    </section>

    <section class="node-form-layout">
      <aside class="panel form-sidebar">
        <el-scrollbar height="680px">
          <div v-for="(items, group) in groupedDefinitions" :key="group" class="form-group">
            <h3>{{ group }}</h3>
            <button
              v-for="definition in items"
              :key="definition.formCode"
              class="form-code-button"
              :class="{ active: definition.formCode === selectedCode }"
              @click="selectedCode = definition.formCode"
            >
              <strong>{{ definition.title }}</strong>
              <span>{{ definition.formCode }}</span>
            </button>
          </div>
        </el-scrollbar>
      </aside>

      <main class="panel form-main">
        <div v-if="selectedDefinition" class="form-head">
          <div>
            <h3>{{ selectedDefinition.title }}</h3>
            <p class="section-subtle">{{ selectedDefinition.formCode }} · {{ selectedDefinition.stateCode }}</p>
          </div>
          <div class="toolbar-actions">
            <el-tag>{{ selectedDefinition.dataKind }}</el-tag>
            <el-tag type="info">{{ selectedDefinition.writeMode }}</el-tag>
          </div>
        </div>

        <el-form v-if="selectedDefinition && !isReadOnly" label-position="top" class="node-editor">
          <el-alert
            v-if="contextWarning"
            :title="contextWarning"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-row :gutter="12">
            <el-col v-for="[key, label] in fieldsForKind(selectedDefinition.dataKind)" :key="key" :xs="24" :sm="12">
              <el-form-item :label="label">
                <el-switch v-if="fieldType(key) === 'boolean'" v-model="form[key]" />
                <el-input-number v-else-if="fieldType(key) === 'number'" v-model="form[key]" controls-position="right" class="wide-control" />
                <el-input v-else v-model="form[key]" :type="String(key).includes('Content') || String(key).includes('Summary') || String(key).includes('remark') ? 'textarea' : 'text'" />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="drawer-actions">
            <el-input-number v-if="!isSingleInstance" v-model="recordId" placeholder="Record ID" controls-position="right" />
            <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="saveCurrent">{{ isSingleInstance ? 'Save' : recordId ? 'Update' : 'Create' }}</el-button>
          </div>
        </el-form>

        <el-divider />

        <div class="card-title-row">
          <h3>Records</h3>
          <el-button :icon="Refresh" @click="loadCurrent">Refresh</el-button>
        </div>
        <el-table :data="visibleRecords" border size="small" class="debug-table">
          <el-table-column type="index" width="54" />
          <el-table-column label="ID" width="130">
            <template #default="{ row }">{{ selectedDefinition ? recordPrimaryId(selectedDefinition.dataKind, row) : '' }}</template>
          </el-table-column>
          <el-table-column label="Data" min-width="420">
            <template #default="{ row }">
              <pre class="record-json">{{ JSON.stringify(row, null, 2) }}</pre>
            </template>
          </el-table-column>
          <el-table-column v-if="!isReadOnly" label="Actions" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editRecord(row)">Edit</el-button>
              <el-button link type="danger" :icon="Delete" @click="removeRecord(row)" />
            </template>
          </el-table-column>
        </el-table>

        <el-divider />

        <div class="material-panel">
          <div class="card-title-row">
            <h3>Materials</h3>
            <el-input v-model="materialTypeCode" class="material-type-input" placeholder="Material type code" />
          </div>
          <el-upload drag :http-request="uploadFile" :show-file-list="false" :disabled="uploadBusy">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          </el-upload>
          <el-table :data="materials" border size="small">
            <el-table-column prop="materialTypeCode" label="Type" width="180" />
            <el-table-column prop="versionNo" label="Version" width="90" />
            <el-table-column prop="fileName" label="File" min-width="220" />
            <el-table-column prop="isCurrent" label="Current" width="90" />
            <el-table-column label="Actions" width="140">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Download" @click="downloadMaterialVersion(row.materialVersionId)" />
                <el-button link type="danger" :icon="Delete" @click="deleteFile(row.materialVersionId)" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </main>
    </section>
  </div>
</template>
