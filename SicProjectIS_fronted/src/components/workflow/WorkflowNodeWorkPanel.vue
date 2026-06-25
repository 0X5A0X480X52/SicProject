<script setup lang="ts">
import { computed, watch } from 'vue'
import ExpertReviewPanel from './ExpertReviewPanel.vue'
import MaterialRequirementPanel from './MaterialRequirementPanel.vue'
import type {
  AvailableTransition,
  ModuleStateRecord,
  NodeFormDefinition,
  NodeFormSaveRequest,
  RuntimeViewResponse,
} from '../../types/nodeForms'

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
  isLimitedProject?: boolean
}

interface WorkflowNodeSubmitPayload {
  formCode?: string
  data?: NodeFormSaveRequest
  result?: string
  remark?: string
}

const props = defineProps<{
  view: RuntimeViewResponse
  transitions: AvailableTransition[]
  transitionKey: string
  remark: string
  formModel: WorkflowNodeFormModel
  selectedMaterialIds: number[]
  submitting: boolean
  recentRecords: ModuleStateRecord[]
  returnedRecords: ModuleStateRecord[]
}>()

const emit = defineEmits<{
  'update:transitionKey': [value: string]
  'update:remark': [value: string]
  'update:formModel': [value: WorkflowNodeFormModel]
  'update:selectedMaterialIds': [value: number[]]
  'payload-change': [payload: WorkflowNodeSubmitPayload]
  'save-draft': []
  discard: []
  submit: []
  close: []
  'materials-changed': []
}>()

const selectedForm = computed<NodeFormDefinition | undefined>(() =>
  props.view.nodeForms?.find((item) => item.writeMode !== 'READ_ONLY') ?? props.view.nodeForms?.[0],
)

const dataKind = computed(() => selectedForm.value?.dataKind)
const isReadOnlyForm = computed(() => !selectedForm.value || selectedForm.value.writeMode === 'READ_ONLY' || dataKind.value === 'DOCUMENT')
const isExpertForm = computed(() => dataKind.value === 'EXPERT_REVIEW')
const isReviewLike = computed(() => dataKind.value === 'CHECK_ITEM')
const isExternalResult = computed(() => dataKind.value === 'EXTERNAL_RESULT')
const isDraft = computed(() => ['APPLICATION_DRAFT', 'CONTRACT_DRAFT', 'ACCEPTANCE_DRAFT'].includes(dataKind.value ?? ''))
const isOperationRecord = computed(() => ['SEAL', 'SUBMISSION', 'ARCHIVE'].includes(dataKind.value ?? ''))
const isProjectRecord = computed(() => ['PUBLICITY', 'FINANCIAL_SETTLEMENT', 'ACHIEVEMENT', 'SURPLUS_RETURN'].includes(dataKind.value ?? ''))
const hasWritableFields = computed(() => Boolean(selectedForm.value && !isReadOnlyForm.value && !isExpertForm.value))

const selectedTransitionLabel = computed(() => {
  const item = props.transitions.find((transition) => keyOf(transition) === props.transitionKey) ?? props.transitions[0]
  return item ? item.result || item.eventType : '无可执行动作'
})

function keyOf(transition: AvailableTransition) {
  return `${transition.transitionId || transition.eventType}-${transition.result || ''}-${transition.targetRef}`
}

function patchModel(patch: Partial<WorkflowNodeFormModel>) {
  emit('update:formModel', { ...props.formModel, ...patch })
}

function updateTransitionKey(value: string | number | boolean) {
  emit('update:transitionKey', String(value))
}

function updateRemark(value: string | number) {
  emit('update:remark', String(value))
}

function updateTextField(field: keyof WorkflowNodeFormModel, value: string | number) {
  patchModel({ [field]: String(value) } as Partial<WorkflowNodeFormModel>)
}

function updateTitle(value: string | number) {
  updateTextField('title', value)
}

function updateSummary(value: string | number) {
  updateTextField('summary', value)
}

function updateExternalActorName(value: string | number) {
  updateTextField('externalActorName', value)
}

function updateResultDocumentNo(value: string | number) {
  updateTextField('resultDocumentNo', value)
}
function updateLimitedProject(value: string | number | boolean) {
  patchModel({ isLimitedProject: value === true || value === 'true' })
}
function updateApproved(value: string | number | boolean) {
  patchModel({ approved: value === true || value === 'true' })
}

function updateOperationTarget(value: string | number) {
  const text = String(value)
  patchModel({ operationTarget: text, operationType: text, archiveType: text })
}

function updateAmount(value: number | undefined) {
  patchModel({ settlementAmount: value ?? null })
}

function updateSelectedMaterials(ids: number[]) {
  emit('update:selectedMaterialIds', ids)
}
function operationTypeLabel() {
  if (dataKind.value === 'SEAL') return 'OFFICIAL_SEAL'
  if (dataKind.value === 'SUBMISSION') return '主管部门/第三方机构'
  if (dataKind.value === 'ARCHIVE') return 'PROCESS_ARCHIVE'
  return 'PROCESS_RECORD'
}

function buildRequest(): WorkflowNodeSubmitPayload {
  const definition = selectedForm.value
  if (!definition || isReadOnlyForm.value || isExpertForm.value) {
    return { remark: props.remark }
  }

  const approved = props.formModel.approved !== false
  const result = approved ? 'APPROVED' : 'REJECTED'
  const remark = props.remark || props.formModel.remark || ''
  const base: NodeFormSaveRequest = {
    projectId: props.view.context.projectId,
    moduleInstanceId: props.view.context.moduleInstanceId,
  }

  if (definition.dataKind === 'CHECK_ITEM') {
    base.runtimeRecord = {
      checkItem: {
        itemCode: definition.formCode,
        itemName: definition.title,
        itemType: 'REVIEW_RESULT',
        itemValue: String(approved),
        itemResult: result,
        required: true,
        passed: approved,
        remark,
        sortNo: 1,
      },
    }
  } else if (definition.dataKind === 'EXTERNAL_RESULT') {
    base.runtimeRecord = {
      externalResult: {
        externalActorCode: props.formModel.externalActorCode || 'EXTERNAL_ACTOR',
        externalActorName: props.formModel.externalActorName || '主管部门/第三方机构',
        externalResult: result,
        resultDocumentNo: props.formModel.resultDocumentNo,
        remark,
      },
    }
  } else if (definition.dataKind === 'SEAL') {
    base.runtimeRecord = {
      sealRecord: {
        sealType: props.formModel.operationType || operationTypeLabel(),
        sealResult: result,
        remark,
      },
    }
  } else if (definition.dataKind === 'SUBMISSION') {
    base.runtimeRecord = {
      submissionRecord: {
        submitTarget: props.formModel.operationTarget || operationTypeLabel(),
        submissionResult: result,
        remark,
      },
    }
  } else if (definition.dataKind === 'ARCHIVE') {
    base.runtimeRecord = {
      archiveRecord: {
        archiveType: props.formModel.archiveType || operationTypeLabel(),
        archiveResult: result,
        remark,
      },
    }
  } else if (definition.dataKind === 'NOTICE') {
    base.notice = {
      moduleType: definition.moduleType,
      noticeType: (definition.moduleType ?? 'NOTICE') + '_NOTICE',
      noticeTitle: props.formModel.title || definition.title,
      contentSummary: props.formModel.summary || remark,
    } as any
  } else if (definition.dataKind === 'PUBLICITY') {
    base.projectRecord = {
      publicity: {
        publicityTitle: props.formModel.title || definition.title,
        publicityResult: result,
        remark,
      },
    } as any
  } else if (definition.dataKind === 'FINANCIAL_SETTLEMENT') {
    base.projectRecord = {
      financialSettlement: {
        moduleInstanceId: props.view.context.moduleInstanceId,
        receivedAmount: props.formModel.settlementAmount ?? 0,
        spentAmount: 0,
        settlementResult: result,
        financeReviewComment: remark,
      },
    } as any
  } else if (definition.dataKind === 'ACHIEVEMENT') {
    base.projectRecord = {
      achievement: {
        achievementTitle: props.formModel.title || definition.title,
        achievementSummary: props.formModel.summary,
        remark,
      },
    } as any
  } else if (definition.dataKind === 'SURPLUS_RETURN') {
    base.projectRecord = {
      surplusReturn: {
        returnResult: result,
        returnAmount: props.formModel.settlementAmount,
        remark,
      },
    } as any
  } else if (definition.dataKind === 'APPLICATION_DRAFT') {
    base.applicationDraft = {
      application: {
        projectId: props.view.context.projectId,
        applicationTitle: props.formModel.title || definition.title,
        applicationSummary: props.formModel.summary || remark,
        isLimitedProject: props.formModel.isLimitedProject === true,
      },
      extension: {
        isLimitedProject: props.formModel.isLimitedProject === true,
      },
      detail: {
        researchObjective: props.formModel.summary || remark,
        applicantCommitment: remark,
      },
    } as any  } else if (definition.dataKind === 'CONTRACT_DRAFT') {
    base.contractDraft = {
      contract: {
        projectId: props.view.context.projectId,
        contractName: props.formModel.title || definition.title,
      },
      extension: {
        contractSummary: props.formModel.summary || remark,
      },
    } as any
  } else if (definition.dataKind === 'ACCEPTANCE_DRAFT') {
    base.acceptanceDraft = {
      acceptance: {
        projectId: props.view.context.projectId,
        conclusion: props.formModel.summary || remark,
      },
      extension: {
        acceptanceTitle: props.formModel.title || definition.title,
        acceptanceSummary: props.formModel.summary || remark,
      },
    } as any
  }

  return {
    formCode: definition.formCode,
    data: base,
    result,
    remark,
  }
}

function validateAndSubmit() {
  emit('submit')
}

watch(
  () => [props.formModel, props.remark, selectedForm.value?.formCode],
  () => emit('payload-change', buildRequest()),
  { deep: true, immediate: true },
)
</script>

<template>
  <section class="workflow-node-work-panel">
    <el-alert
      :title="view.canOperate ? '当前用户具备该节点办理权限' : '当前用户不可办理此节点，仅可查看。'"
      :type="view.canOperate ? 'success' : 'info'"
      :closable="false"
      show-icon
    />

    <el-descriptions class="workflow-node-work-summary" :column="2" size="small" border>
      <el-descriptions-item label="当前节点">{{ view.context.currentNodeName || view.context.currentNodeId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="候选角色">{{ view.context.currentCandidateRoleCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="当前动作">{{ selectedTransitionLabel }}</el-descriptions-item>
      <el-descriptions-item label="轮次/序号">{{ view.context.currentRoundNo ?? '-' }} / {{ view.context.currentSeq ?? '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-tabs class="workflow-node-work-tabs" model-value="work">
      <el-tab-pane label="办理信息" name="work">
        <el-form label-position="top" class="workflow-node-work-form">
          <el-form-item v-if="transitions.length" label="办理动作">
            <el-radio-group :model-value="transitionKey" @update:model-value="updateTransitionKey">
              <el-radio-button v-for="transition in transitions" :key="keyOf(transition)" :label="keyOf(transition)">
                {{ transition.result || transition.eventType }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-alert v-if="isReadOnlyForm" title="当前节点为只读文档或暂无可填写业务信息。" type="info" :closable="false" />
          <el-alert v-else-if="isExpertForm" title="专家评审请在“专家评审”页签中创建批次、邀请专家或提交评分。" type="info" :closable="false" />

          <template v-if="hasWritableFields">
            <el-form-item v-if="isDraft || isProjectRecord" label="标题 / 名称">
              <el-input :model-value="formModel.title" placeholder="填写标题或名称" @update:model-value="updateTitle" />
            </el-form-item>
            <el-form-item v-if="isDraft || isProjectRecord" label="摘要 / 说明">
              <el-input
                :model-value="formModel.summary"
                type="textarea"
                :rows="3"
                placeholder="填写摘要或补充说明"
                @update:model-value="updateSummary"
              />
            </el-form-item>
            <el-form-item v-if="dataKind === 'APPLICATION_DRAFT'" label="是否限项项目">
              <el-radio-group :model-value="formModel.isLimitedProject === true" @update:model-value="updateLimitedProject">
                <el-radio-button :label="true">是</el-radio-button>
                <el-radio-button :label="false">否</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="isReviewLike || isExternalResult || isOperationRecord || isProjectRecord" label="办理结果">
              <el-radio-group :model-value="formModel.approved !== false" @update:model-value="updateApproved">
                <el-radio-button :label="true">通过</el-radio-button>
                <el-radio-button :label="false">退回 / 不通过</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <template v-if="isExternalResult">
              <el-form-item label="外部单位">
                <el-input
                  :model-value="formModel.externalActorName"
                  placeholder="主管部门/第三方机构"
                  @update:model-value="updateExternalActorName"
                />
              </el-form-item>
              <el-form-item label="结果文号">
                <el-input
                  :model-value="formModel.resultDocumentNo"
                  placeholder="可选"
                  @update:model-value="updateResultDocumentNo"
                />
              </el-form-item>
            </template>
            <template v-if="isOperationRecord">
              <el-form-item label="办理类型 / 目标">
                <el-input
                  :model-value="formModel.operationTarget || formModel.operationType || formModel.archiveType"
                  placeholder="例如：学校公章、主管部门、流程归档"
                  @update:model-value="updateOperationTarget"
                />
              </el-form-item>
            </template>
            <el-form-item v-if="dataKind === 'FINANCIAL_SETTLEMENT' || dataKind === 'SURPLUS_RETURN'" label="金额">
              <el-input-number
                :model-value="formModel.settlementAmount ?? undefined"
                :min="0"
                :precision="2"
                style="width: 220px"
                @update:model-value="updateAmount"
              />
            </el-form-item>
          </template>

          <el-form-item label="审批意见 / 办理说明">
            <el-input
              :model-value="remark"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="填写审批意见或办理说明"
              @update:model-value="updateRemark"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="材料附件" name="materials">
        <MaterialRequirementPanel
          :project-id="view.context.projectId"
          :requirements="view.materialRequirements"
          :selected-ids="selectedMaterialIds"
          @update:selected-ids="updateSelectedMaterials"
          @materials-changed="emit('materials-changed')"
        />
      </el-tab-pane>

      <el-tab-pane v-if="isExpertForm || view.context.currentCandidateRoleCode === 'EXPERT'" label="专家评审" name="expert">
        <ExpertReviewPanel :view="view" />
      </el-tab-pane>

      <el-tab-pane label="历史记录" name="history">
        <el-empty v-if="!recentRecords.length && !returnedRecords.length" description="暂无历史记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="record in (returnedRecords.length ? returnedRecords : recentRecords)"
            :key="record.stateRecordId"
            :timestamp="record.createdAt?.replace('T', ' ').slice(0, 19)"
            :type="String(record.result || record.eventType).includes('RETURN') ? 'warning' : 'primary'"
          >
            <strong>{{ record.eventType }} · {{ record.result || '-' }}</strong>
            <p>{{ record.summary || record.toState }}</p>
          </el-timeline-item>
        </el-timeline>
      </el-tab-pane>
    </el-tabs>

    <div class="workflow-node-work-footer">
      <el-button @click="emit('save-draft')">保存本页草稿</el-button>
      <el-button @click="emit('discard')">放弃修改</el-button>
      <el-button @click="emit('close')">关闭</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!view.canOperate || !transitions.length" @click="validateAndSubmit">
        提交办理
      </el-button>
    </div>
  </section>
</template>
