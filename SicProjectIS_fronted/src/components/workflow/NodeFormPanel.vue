<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { buildRequestFromFlatForm } from '../../composables/useNodeFormHelpers'
import type { NodeFormDefinition, NodeFormSaveRequest, RuntimeViewResponse } from '../../types/nodeForms'

const props = defineProps<{ view: RuntimeViewResponse }>()
const emit = defineEmits<{ change: [payload: { formCode?: string; data?: NodeFormSaveRequest; result?: string; remark?: string }] }>()

const selectedFormCode = ref('')
const form = reactive({ approved: true, title: '', remark: '', json: '{}' })

const selectedForm = computed<NodeFormDefinition | undefined>(() =>
  props.view.nodeForms?.find((item) => item.formCode === selectedFormCode.value),
)

const isReview = computed(() => selectedForm.value?.dataKind === 'CHECK_ITEM')
const isExternal = computed(() => selectedForm.value?.dataKind === 'EXTERNAL_RESULT')
const isExpert = computed(() => selectedForm.value?.dataKind === 'EXPERT_REVIEW')

function parseJson() {
  try {
    return JSON.parse(form.json || '{}')
  } catch {
    ElMessage.warning('高级 JSON 格式不正确，已忽略')
    return {}
  }
}

function buildRequest(): NodeFormSaveRequest | undefined {
  const definition = selectedForm.value
  if (!definition) return undefined
  const extra = parseJson()
  const passed = form.approved
  return buildRequestFromFlatForm(
    definition.dataKind,
    {
      ...extra,
      approved: passed,
      remark: form.remark,
      noticeTitle: extra.noticeTitle || form.title || definition.title,
      applicationTitle: extra.applicationTitle || form.title,
      contractName: extra.contractName || form.title,
      acceptanceTitle: extra.acceptanceTitle || form.title,
    },
    {
      projectId: props.view.context.projectId,
      moduleInstanceId: props.view.context.moduleInstanceId,
    },
    definition,
  )
}

function notify() {
  const request = buildRequest()
  const result = form.approved ? 'APPROVED' : 'REJECTED'
  emit('change', {
    formCode: selectedFormCode.value || undefined,
    data: request,
    result,
    remark: form.remark,
  })
}

watch(() => props.view.nodeForms, (forms) => {
  selectedFormCode.value = forms?.[0]?.formCode || ''
  form.title = forms?.[0]?.title || ''
  form.remark = ''
  form.json = '{}'
  notify()
}, { immediate: true })
watch(form, notify, { deep: true })
watch(selectedFormCode, notify)
</script>

<template>
  <el-card class="workflow-card" shadow="never">
    <template #header>节点业务表单</template>
    <el-empty v-if="!view.nodeForms?.length" description="当前节点暂无匹配业务表单" />
    <el-form v-else label-position="top">
      <el-form-item label="表单类型">
        <el-select v-model="selectedFormCode" style="width: 100%">
          <el-option v-for="item in view.nodeForms" :key="item.formCode" :label="item.title" :value="item.formCode" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!isExpert" label="标题 / 名称">
        <el-input v-model="form.title" />
      </el-form-item>
      <el-form-item v-if="isReview || isExternal" label="办理结果">
        <el-radio-group v-model="form.approved">
          <el-radio-button :label="true">通过</el-radio-button>
          <el-radio-button :label="false">退回 / 不通过</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="意见 / 说明">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="高级 JSON 数据">
        <el-input v-model="form.json" type="textarea" :rows="5" placeholder="用于补充当前表单的结构化字段，可留空为 {}" />
      </el-form-item>
    </el-form>
  </el-card>
</template>

