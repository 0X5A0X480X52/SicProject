<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { AvailableTransition, StateTransitionRequest } from '../../types/nodeForms'

const props = defineProps<{
  modelValue: boolean
  transition: AvailableTransition | null
  expectedSeq?: number | null
  submitting: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [request: StateTransitionRequest]
}>()

const form = reactive({ remark: '', materialVersionIdsText: '' })

watch(() => props.modelValue, (open) => {
  if (open) {
    form.remark = props.transition?.result || ''
    form.materialVersionIdsText = ''
  }
})

function submit() {
  if (!props.transition) return
  const materialVersionIds = form.materialVersionIdsText
    .split(',')
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isFinite(value) && value > 0)
  emit('submit', {
    eventType: props.transition.eventType,
    expectedSeq: props.expectedSeq ?? undefined,
    result: props.transition.result,
    remark: form.remark,
    materialVersionIds,
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="提交流程动作"
    width="520px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="动作">
        <el-input :model-value="transition?.eventType || ''" disabled />
      </el-form-item>
      <el-form-item label="审批意见 / 操作说明">
        <el-input v-model="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="材料版本 ID">
        <el-input v-model="form.materialVersionIdsText" placeholder="多个 ID 用英文逗号分隔；可留空" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>
