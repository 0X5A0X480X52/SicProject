<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ExpertReviewPanel from './ExpertReviewPanel.vue'
import MaterialRequirementPanel from './MaterialRequirementPanel.vue'
import { eventLabel, resultLabel, roleLabel } from '../../utils/displayLabels'
import { filteredFieldsForKind, fieldType, isTextareaField, defaultsForKind, requiredFieldsForKind } from '../../utils/nodeFormFields'
import { buildRequestFromFlatForm } from '../../composables/useNodeFormHelpers'
import type {
  AvailableTransition,
  NodeFormDefinition,
  NodeFormSaveRequest,
  RuntimeViewResponse,
} from '../../types/nodeForms'

interface WorkflowNodeFormModel {
  [key: string]: string | number | boolean | null | undefined
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

const activeCollapse = ref<string[]>(['work'])

const selectedForm = computed<NodeFormDefinition | undefined>(() =>
  props.view.nodeForms?.find((item) => item.writeMode !== 'READ_ONLY') ?? props.view.nodeForms?.[0],
)

const dataKind = computed(() => selectedForm.value?.dataKind)
const isReadOnlyForm = computed(() => !selectedForm.value || selectedForm.value.writeMode === 'READ_ONLY' || dataKind.value === 'DOCUMENT')
const isExpertForm = computed(() => dataKind.value === 'EXPERT_REVIEW')
const hasWritableFields = computed(() => Boolean(selectedForm.value && !isReadOnlyForm.value && !isExpertForm.value))

/** User-writable field entries for the current dataKind (system fields excluded). */
const writableFields = computed<Array<[string, string]>>(() =>
  hasWritableFields.value ? filteredFieldsForKind(dataKind.value) : [],
)

const selectedTransitionLabel = computed(() => {
  const item = props.transitions.find((transition) => keyOf(transition) === props.transitionKey) ?? props.transitions[0]
  return item ? (resultLabel(item.result) !== '-' ? resultLabel(item.result) : eventLabel(item.eventType)) : '无可执行动作'
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

function updateField(key: string, value: string | number | boolean | undefined) {
  patchModel({ [key]: value })
}

function updateSelectedMaterials(ids: number[]) {
  emit('update:selectedMaterialIds', ids)
}

function isRequiredField(key: string) {
  return requiredFieldsForKind(dataKind.value).includes(key)
}

function buildRequest(): WorkflowNodeSubmitPayload {
  const definition = selectedForm.value
  if (!definition || isReadOnlyForm.value || isExpertForm.value) {
    return { remark: props.remark }
  }

  const kind = dataKind.value
  const approved = props.formModel.approved !== false
  const result = approved ? 'APPROVED' : 'REJECTED'
  const remark = props.remark || (props.formModel.remark as string) || ''

  // Merge formModel with explicit approved/remark overrides
  const flatForm = {
    ...defaultsForKind(kind),
    ...props.formModel,
    approved,
    remark,
  }

  const data = buildRequestFromFlatForm(
    kind,
    flatForm,
    {
      projectId: props.view.context.projectId,
      moduleInstanceId: props.view.context.moduleInstanceId,
    },
    definition,
  )

  return {
    formCode: definition.formCode,
    data,
    result,
    remark,
  }
}

function validateAndSubmit() {
  emit('submit')
}

// Emit payload whenever form data changes
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
      <el-descriptions-item label="候选角色">{{ roleLabel(view.context.currentCandidateRoleCode) }}</el-descriptions-item>
      <el-descriptions-item label="当前动作">{{ selectedTransitionLabel }}</el-descriptions-item>
      <el-descriptions-item label="轮次/序号">{{ view.context.currentRoundNo ?? '-' }} / {{ view.context.currentSeq ?? '-' }}</el-descriptions-item>
    </el-descriptions>

    <!-- Collapsible stacked cards replacing el-tabs -->
    <el-collapse v-model="activeCollapse" class="workflow-node-work-collapse">

      <!-- Card 1: 办理信息 — always visible, expanded by default -->
      <el-collapse-item title="📋 办理信息" name="work">
        <el-form label-position="top" class="workflow-node-work-form">
          <!-- Transition action selector -->
          <el-form-item v-if="transitions.length" label="办理动作">
            <el-radio-group :model-value="transitionKey" @update:model-value="updateTransitionKey">
              <el-radio-button v-for="transition in transitions" :key="keyOf(transition)" :label="keyOf(transition)">
                {{ resultLabel(transition.result) !== '-' ? resultLabel(transition.result) : eventLabel(transition.eventType) }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-alert v-if="isReadOnlyForm" title="当前节点为只读文档或暂无可填写业务信息。" type="info" :closable="false" />
          <el-alert v-else-if="isExpertForm" title="专家评审请在“专家意见”卡片中创建批次、邀请专家或提交评分。" type="info" :closable="false" />

          <!-- Dynamic field rendering for all user-writable fields -->
          <template v-if="hasWritableFields && writableFields.length">
            <!-- Approval toggle for review-like nodes -->
            <el-form-item
              v-if="dataKind === 'CHECK_ITEM' || dataKind === 'EXTERNAL_RESULT' || dataKind === 'SEAL' || dataKind === 'SUBMISSION' || dataKind === 'ARCHIVE' || dataKind === 'PUBLICITY' || dataKind === 'FINANCIAL_SETTLEMENT' || dataKind === 'ACHIEVEMENT' || dataKind === 'SURPLUS_RETURN'"
              label="办理结果"
            >
              <el-radio-group
                :model-value="formModel.approved !== false"
                @update:model-value="updateField('approved', $event)"
              >
                <el-radio-button :label="true">通过</el-radio-button>
                <el-radio-button :label="false">退回 / 不通过</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <!-- Dynamic fields grid -->
            <el-row :gutter="12">
              <el-col
                v-for="[key, label] in writableFields"
                :key="key"
                :xs="24"
                :sm="12"
              >
                <el-form-item :label="label" :required="isRequiredField(key)">
                  <!-- Boolean fields -->
                  <el-switch
                    v-if="fieldType(key) === 'boolean'"
                    :model-value="!!formModel[key]"
                    @update:model-value="updateField(key, $event)"
                  />
                  <!-- Number fields -->
                  <el-input-number
                    v-else-if="fieldType(key) === 'number'"
                    :model-value="formModel[key] !== undefined && formModel[key] !== null ? Number(formModel[key]) : undefined"
                    :min="0"
                    controls-position="right"
                    class="wide-control"
                    @update:model-value="updateField(key, $event)"
                  />
                  <!-- Textarea fields -->
                  <el-input
                    v-else-if="isTextareaField(key)"
                    :model-value="formModel[key] !== undefined && formModel[key] !== null ? String(formModel[key]) : ''"
                    type="textarea"
                    :rows="3"
                    :placeholder="`填写${label}`"
                    @update:model-value="updateField(key, $event)"
                  />
                  <!-- Regular text fields -->
                  <el-input
                    v-else
                    :model-value="formModel[key] !== undefined && formModel[key] !== null ? String(formModel[key]) : ''"
                    :placeholder="`填写${label}`"
                    @update:model-value="updateField(key, $event)"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- Remark / opinion textarea — always shown -->
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
      </el-collapse-item>

      <!-- Card 2: 附件材料 — collapsed by default -->
      <el-collapse-item title="📎 附件材料" name="materials">
        <MaterialRequirementPanel
          :project-id="view.context.projectId"
          :requirements="view.materialRequirements"
          :selected-ids="selectedMaterialIds"
          @update:selected-ids="updateSelectedMaterials"
          @materials-changed="emit('materials-changed')"
        />
      </el-collapse-item>

      <!-- Card 3: 专家意见 — conditional, collapsed by default -->
      <el-collapse-item
        v-if="isExpertForm || view.context.currentCandidateRoleCode === 'EXPERT'"
        title="👥 专家意见"
        name="expert"
      >
        <ExpertReviewPanel :view="view" />
      </el-collapse-item>

    </el-collapse>

    <!-- Sticky footer action bar -->
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
