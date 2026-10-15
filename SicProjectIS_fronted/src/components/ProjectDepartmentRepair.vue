<script setup lang="ts">
/**
 * 为历史空部门项目提供一次性归属补充入口，只向全局管理员展示操作；选择启用部门并填写原因后由后端加锁校验。
 */
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getDepartments } from '../api/departmentMembers'
import { assignMissingProjectDepartment } from '../api/projects'
import type { DepartmentOption } from '../types/departmentMembers'
const props = defineProps<{ projectId: number; deptId: number | null }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const manageable = computed(() => auth.user?.roleCodes.some(role => ['SYSTEM_ADMIN', 'SCIENCE_ADMIN'].includes(role)))
const open = ref(false)
const departments = ref<DepartmentOption[]>([])
const department = ref<number | null>(null)
const reason = ref('')
const saving = ref(false)
async function begin() {
  try { departments.value = (await getDepartments()).filter(row => row.enabled); open.value = true }
  catch (err) { ElMessage.error(err instanceof Error ? err.message : '部门列表加载失败') }
}
/**
 * 要求选择部门并填写原因，确认后提交一次补充；成功通知父页面重查项目归属，不在前端先行伪造部门。
 */
async function save() {
  if (!department.value || !reason.value.trim()) { ElMessage.warning('请选择部门并填写补充原因'); return }
  const choice = department.value
  try { await ElMessageBox.confirm('请核对项目归属部门。补充后不能通过此入口再次变更。', '补充归属部门') } catch { return }
  saving.value = true
  try {
    await assignMissingProjectDepartment(props.projectId, choice, reason.value)
    open.value = false; ElMessage.success('项目归属部门已补充'); emit('changed')
  } catch (err) { ElMessage.error(err instanceof Error ? err.message : '归属部门补充失败') }
  finally { saving.value = false }
}
</script>
<template>
  <el-alert v-if="props.deptId == null" title="该历史项目缺少归属部门，部门管理员无法正常审批。" type="warning" :closable="false" show-icon>
    <el-button v-if="manageable" type="primary" link @click="begin">补充归属部门</el-button>
    <span v-else>请联系系统管理员或科研管理员处理。</span>
  </el-alert>
  <el-dialog v-model="open" title="补充项目归属部门" width="500px">
    <el-form label-position="top">
      <el-form-item label="归属部门"><el-select v-model="department"><el-option v-for="row in departments" :key="row.deptId" :value="row.deptId" :label="row.deptName ?? String(row.deptId)" /></el-select></el-form-item>
      <el-form-item label="补充原因"><el-input v-model="reason" type="textarea" maxlength="500" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="open = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认补充</el-button></template>
  </el-dialog>
</template>
