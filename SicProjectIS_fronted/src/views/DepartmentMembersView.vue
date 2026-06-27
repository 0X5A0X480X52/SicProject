<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import {
  createDepartment,
  deleteDepartment,
  getDepartmentMembers,
  removeDepartmentMember,
  searchDepartmentMemberCandidates,
  updateDepartment,
  updateDepartmentMemberRoles,
  upsertDepartmentMember,
} from '../api/departmentMembers'
import type { DepartmentMember, DepartmentMemberQueryResponse, DepartmentOption } from '../types/departmentMembers'
import { roleLabel } from '../utils/displayLabels'

const loading = ref(false)
const saving = ref(false)
const candidateLoading = ref(false)
const data = ref<DepartmentMemberQueryResponse | null>(null)
const selectedDeptId = ref<number | null>(null)
const keyword = ref('')
const activeTab = ref<'departments' | 'members'>('members')
const candidateDialogVisible = ref(false)
const roleDialogVisible = ref(false)
const departmentDialogVisible = ref(false)
const candidateKeyword = ref('')
const candidates = ref<DepartmentMember[]>([])
const selectedCandidate = ref<DepartmentMember | null>(null)
const selectedMember = ref<DepartmentMember | null>(null)
const selectedDepartment = ref<DepartmentOption | null>(null)
const targetDeptId = ref<number | null>(null)
const roleDraft = ref<string[]>([])
const departmentForm = reactive({ deptCode: '', deptName: '', parentDeptId: null as number | null, enabled: true })

const departments = computed(() => data.value?.departments ?? [])
const assignableRoleCodes = computed(() => data.value?.assignableRoleCodes ?? [])
const canManageDepartments = computed(() => Boolean(data.value?.canManageDepartments))
const memberManagementDisabled = computed(() => Boolean(data.value?.memberManagementDisabled))
const disabledReason = computed(() => data.value?.disabledReason || '请联系系统管理员分配部门')
const targetDeptName = computed(() => departments.value.find((item) => item.deptId === targetDeptId.value)?.deptName || targetDeptId.value || '-')
const filteredMembers = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const rows = data.value?.members ?? []
  if (!normalized) return rows
  return rows.filter((member) =>
    [member.username, member.realName, member.deptName, member.phone, member.email, ...member.roleCodes]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(normalized),
  )
})

async function loadMembers() {
  loading.value = true
  try {
    data.value = await getDepartmentMembers(selectedDeptId.value)
    if (selectedDeptId.value == null && data.value.departments.length === 1) {
      selectedDeptId.value = data.value.departments[0].deptId
    }
    if (data.value.memberManagementDisabled) {
      activeTab.value = 'members'
    }
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '部门管理数据加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCandidates() {
  if (memberManagementDisabled.value) return
  candidateLoading.value = true
  try {
    const response = await searchDepartmentMemberCandidates(candidateKeyword.value)
    candidates.value = response.users
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '候选用户加载失败')
  } finally {
    candidateLoading.value = false
  }
}

function openDepartmentDialog(department?: DepartmentOption) {
  selectedDepartment.value = department ?? null
  departmentForm.deptCode = department?.deptCode ?? ''
  departmentForm.deptName = department?.deptName ?? ''
  departmentForm.parentDeptId = department?.parentDeptId ?? null
  departmentForm.enabled = department?.enabled ?? true
  departmentDialogVisible.value = true
}

async function saveDepartment() {
  if (!departmentForm.deptName.trim()) {
    ElMessage.warning('请填写部门名称')
    return
  }
  saving.value = true
  try {
    const request = { ...departmentForm }
    if (selectedDepartment.value) {
      await updateDepartment(selectedDepartment.value.deptId, request)
      ElMessage.success('部门已更新')
    } else {
      await createDepartment(request)
      ElMessage.success('部门已创建')
    }
    departmentDialogVisible.value = false
    await loadMembers()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '部门保存失败')
  } finally {
    saving.value = false
  }
}

async function removeDepartment(department: DepartmentOption) {
  await ElMessageBox.confirm(
    `确认删除部门 ${department.deptName || department.deptCode || department.deptId} 吗？该部门下用户将变为未分配部门。`,
    '确认删除部门',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  )
  saving.value = true
  try {
    await deleteDepartment(department.deptId)
    if (selectedDeptId.value === department.deptId) selectedDeptId.value = null
    ElMessage.success('部门已删除')
    await loadMembers()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '部门删除失败')
  } finally {
    saving.value = false
  }
}

function openCandidateDialog() {
  if (memberManagementDisabled.value) return
  selectedCandidate.value = null
  targetDeptId.value = selectedDeptId.value ?? (departments.value.length === 1 ? departments.value[0].deptId : null)
  candidateKeyword.value = ''
  candidates.value = []
  candidateDialogVisible.value = true
  loadCandidates()
}

function openRoleDialog(member: DepartmentMember) {
  selectedMember.value = member
  roleDraft.value = member.roleCodes.filter((roleCode) => assignableRoleCodes.value.includes(roleCode))
  roleDialogVisible.value = true
}

async function addCandidate() {
  if (!selectedCandidate.value || targetDeptId.value == null) {
    ElMessage.warning('请选择部门和用户')
    return
  }
  await ElMessageBox.confirm(
    `用户：${selectedCandidate.value.realName} (${selectedCandidate.value.username})\n目标部门：${targetDeptName.value}`,
    '确认分配用户部门',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
  )
  saving.value = true
  try {
    await upsertDepartmentMember(targetDeptId.value, selectedCandidate.value.userId, {})
    candidateDialogVisible.value = false
    ElMessage.success('用户部门已分配')
    await loadMembers()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '部门分配失败')
  } finally {
    saving.value = false
  }
}

async function saveRoles() {
  if (!selectedMember.value) return
  await ElMessageBox.confirm(
    `用户：${selectedMember.value.realName}\n角色：${roleDraft.value.map(roleLabel).join(', ') || '无'}`,
    '确认更新成员角色',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
  )
  saving.value = true
  try {
    await updateDepartmentMemberRoles(selectedMember.value.userId, { roleCodes: roleDraft.value })
    roleDialogVisible.value = false
    ElMessage.success('成员角色已更新')
    await loadMembers()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '成员角色更新失败')
  } finally {
    saving.value = false
  }
}

async function removeMember(member: DepartmentMember) {
  await ElMessageBox.confirm(
    `确认将 ${member.realName} 从 ${member.deptName || '当前部门'} 移除吗？`,
    '确认移除成员',
    { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' },
  )
  saving.value = true
  try {
    await removeDepartmentMember(member.userId)
    ElMessage.success('成员已移除部门')
    await loadMembers()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '成员移除失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadMembers)
</script>

<template>
  <PermissionWorkbenchLayout>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">权限中心</p>
          <h2 class="section-heading">部门管理</h2>
        </div>
        <el-button @click="loadMembers">刷新</el-button>
      </section>

      <el-alert
        v-if="memberManagementDisabled"
        type="warning"
        show-icon
        :closable="false"
        :title="disabledReason"
      />

      <el-card shadow="never" class="workbench-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">部门与成员</h3>
              <p class="section-subtle">系统管理员维护部门并分配任意用户部门；二级机构管理员和科技处管理员维护可管理范围内成员。</p>
            </div>
            <el-button v-if="canManageDepartments" type="primary" @click="openDepartmentDialog()">新增部门</el-button>
          </div>
        </template>

        <el-tabs v-model="activeTab">
          <el-tab-pane v-if="canManageDepartments" label="部门定义" name="departments">
            <el-table :data="departments" v-loading="loading" border stripe>
              <el-table-column prop="deptCode" label="部门编码" min-width="150">
                <template #default="{ row }">{{ row.deptCode || '-' }}</template>
              </el-table-column>
              <el-table-column prop="deptName" label="部门名称" min-width="180" />
              <el-table-column prop="parentDeptId" label="上级部门" min-width="120">
                <template #default="{ row }">{{ row.parentDeptId || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDepartmentDialog(row)">编辑</el-button>
                  <el-button link type="danger" :disabled="saving" @click="removeDepartment(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="部门成员" name="members">
            <el-form :inline="true" class="toolbar-form toolbar-form-plain">
              <el-form-item label="部门">
                <el-select v-model="selectedDeptId" placeholder="全部可管理部门" clearable style="width: 260px" :disabled="memberManagementDisabled" @change="loadMembers">
                  <el-option v-for="dept in departments" :key="dept.deptId" :label="dept.deptName || dept.deptCode || dept.deptId" :value="dept.deptId" />
                </el-select>
              </el-form-item>
              <el-form-item label="搜索">
                <el-input v-model="keyword" placeholder="姓名、账号、电话、角色" clearable style="width: 280px" :disabled="memberManagementDisabled" />
              </el-form-item>
              <el-form-item>
                <div class="inline-tag-list">
                  <el-tag v-for="roleCode in assignableRoleCodes" :key="roleCode" type="info">{{ roleLabel(roleCode) }}</el-tag>
                </div>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :disabled="memberManagementDisabled || departments.length === 0" @click="openCandidateDialog">分配部门</el-button>
              </el-form-item>
            </el-form>

            <el-table :data="filteredMembers" v-loading="loading" border stripe>
              <el-table-column prop="username" label="用户名" min-width="150" />
              <el-table-column prop="realName" label="姓名" min-width="140" />
              <el-table-column prop="deptName" label="部门" min-width="180">
                <template #default="{ row }">{{ row.deptName || '未分配部门' }}</template>
              </el-table-column>
              <el-table-column label="角色" min-width="240">
                <template #default="{ row }">
                  <div class="inline-tag-list">
                    <el-tag v-for="roleCode in row.roleCodes" :key="roleCode" size="small">{{ roleLabel(roleCode) }}</el-tag>
                    <el-tag v-if="!row.roleCodes.length" size="small" type="info">无角色</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" :disabled="memberManagementDisabled || row.deptId == null" @click="removeMember(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>

    <el-dialog v-model="departmentDialogVisible" :title="selectedDepartment ? '编辑部门' : '新增部门'" width="520px">
      <el-form label-width="96px">
        <el-form-item label="部门编码">
          <el-input v-model="departmentForm.deptCode" placeholder="可选，如 SCHOOL_CS" />
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="departmentForm.deptName" placeholder="部门名称" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="departmentForm.parentDeptId" placeholder="无上级部门" clearable style="width: 100%">
            <el-option v-for="dept in departments" :key="dept.deptId" :disabled="dept.deptId === selectedDepartment?.deptId" :label="dept.deptName || dept.deptCode || dept.deptId" :value="dept.deptId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="departmentForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="departmentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDepartment">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="candidateDialogVisible" title="分配用户部门" width="720px">
      <el-form label-width="96px">
        <el-form-item label="目标部门" required>
          <el-select v-model="targetDeptId" placeholder="请选择目标部门" filterable style="width: 100%">
            <el-option v-for="dept in departments" :key="dept.deptId" :label="dept.deptName || dept.deptCode || dept.deptId" :value="dept.deptId" />
          </el-select>
        </el-form-item>
        <el-form-item label="搜索用户">
          <el-input v-model="candidateKeyword" placeholder="姓名、账号、部门、角色" clearable @keyup.enter="loadCandidates">
            <template #append><el-button :loading="candidateLoading" @click="loadCandidates">查询</el-button></template>
          </el-input>
        </el-form-item>
        <el-form-item label="选择用户">
          <el-table :data="candidates" v-loading="candidateLoading" border height="260" highlight-current-row @current-change="selectedCandidate = $event">
            <el-table-column prop="realName" label="姓名" min-width="120" />
            <el-table-column prop="username" label="账号" min-width="120" />
            <el-table-column prop="deptName" label="当前部门" min-width="160">
              <template #default="{ row }">{{ row.deptName || '未分配部门' }}</template>
            </el-table-column>
          </el-table>
        </el-form-item>

      </el-form>
      <template #footer>
        <el-button @click="candidateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addCandidate">确认分配</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="编辑成员角色" width="520px">
      <el-form v-if="selectedMember" label-width="96px">
        <el-form-item label="成员">{{ selectedMember.realName }} / {{ selectedMember.username }}</el-form-item>
        <el-form-item label="可编辑角色">
          <el-checkbox-group v-model="roleDraft">
            <el-checkbox v-for="roleCode in assignableRoleCodes" :key="roleCode" :label="roleCode">{{ roleLabel(roleCode) }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存角色</el-button>
      </template>
    </el-dialog>
  </PermissionWorkbenchLayout>
</template>