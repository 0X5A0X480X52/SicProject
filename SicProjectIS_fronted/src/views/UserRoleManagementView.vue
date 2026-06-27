<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import { getAdminUserDetail, getAdminUsers, updateUserRoles, updateUserStatus } from '../api/admin'
import type { AdminUserDetail, AdminUserListItem, AdminUserQueryResponse, ChangeDiffSummary } from '../types/admin'
import { roleLabel } from '../utils/displayLabels'

const loading = ref(false)
const saving = ref(false)
const data = ref<AdminUserQueryResponse | null>(null)
const selectedUser = ref<AdminUserDetail | null>(null)
const editorVisible = ref(false)
const detailLoading = ref(false)
const roleDraft = ref<string[]>([])

const filters = reactive({
  username: '',
  realName: '',
  roleCode: '',
  enabled: '',
})

const roles = computed(() => data.value?.roles ?? [])
const users = computed(() => data.value?.users ?? [])
const pendingDiff = computed<ChangeDiffSummary>(() => buildDiff(selectedUser.value?.roleCodes ?? [], roleDraft.value))
const selectedRoleDetails = computed(() => roles.value.filter((role) => roleDraft.value.includes(role.roleCode)))
const enabledRoleCount = computed(() => roles.value.filter((role) => role.enabled).length)
const roleChangeCount = computed(() => pendingDiff.value.added.length + pendingDiff.value.removed.length)

function roleDescription(roleCode: string, fallback?: string | null) {
  if (fallback && fallback.trim()) return fallback
  switch (roleCode) {
    case 'SYSTEM_ADMIN': return '系统级管理员，可管理用户、角色、权限和全局配置。'
    case 'SCIENCE_ADMIN': return '科研管理部门角色，负责项目统筹、流程审核和授权协调。'
    case 'DEPT_ADMIN': return '院系管理角色，负责本部门项目管理与审核。'
    case 'PROJECT_LEADER': return '项目负责人角色，可维护项目材料并发起或办理相关流程。'
    case 'EXPERT': return '专家角色，仅参与被分配的评审任务。'
    case 'FINANCE_ADMIN': return '财务管理角色，负责经费结算和预算相关流程办理。'
    default: return '用于解析系统权限和流程职责的角色。'
  }
}

async function loadUsers() {
  loading.value = true
  try {
    data.value = await getAdminUsers({
      username: filters.username || undefined,
      realName: filters.realName || undefined,
      roleCode: filters.roleCode || undefined,
      enabled: filters.enabled === '' ? null : filters.enabled === 'true',
    })
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

async function openEditor(user: AdminUserListItem) {
  detailLoading.value = true
  editorVisible.value = true
  try {
    selectedUser.value = await getAdminUserDetail(user.userId)
    roleDraft.value = [...selectedUser.value.roleCodes]
  } catch (err) {
    editorVisible.value = false
    ElMessage.error(err instanceof ApiError ? err.message : '用户详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function saveRoles() {
  if (!selectedUser.value) return
  await ElMessageBox.confirm(
    [
      `用户：${selectedUser.value.realName} (${selectedUser.value.username})`,
      `新增角色：${pendingDiff.value.added.map(roleLabel).join(', ') || '无'}`,
      `移除角色：${pendingDiff.value.removed.map(roleLabel).join(', ') || '无'}`,
    ].join('\n'),
    '确认提交角色变更',
    {
      type: 'warning',
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
    },
  )

  saving.value = true
  try {
    const response = await updateUserRoles(selectedUser.value.userId, { roleCodes: roleDraft.value })
    data.value = response.query
    selectedUser.value = response.user
    ElMessage.success(`已更新 ${response.user.realName} 的系统角色`)
    editorVisible.value = false
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '角色更新失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user: AdminUserListItem) {
  await ElMessageBox.confirm(
    `确认将 ${user.realName} ${user.enabled ? '停用' : '启用'} 吗？`,
    '确认修改用户状态',
    {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    },
  )
  saving.value = true
  try {
    const detail = await updateUserStatus(user.userId, { enabled: !user.enabled })
    if (selectedUser.value?.userId === detail.userId) {
      selectedUser.value = detail
    }
    await loadUsers()
    ElMessage.success(`${detail.realName} 已${detail.enabled ? '启用' : '停用'}`)
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '用户状态更新失败')
  } finally {
    saving.value = false
  }
}

function resetFilters() {
  filters.username = ''
  filters.realName = ''
  filters.roleCode = ''
  filters.enabled = ''
  loadUsers()
}

function buildDiff(before: string[], after: string[]): ChangeDiffSummary {
  return {
    added: after.filter((code) => !before.includes(code)),
    removed: before.filter((code) => !after.includes(code)),
  }
}

onMounted(loadUsers)
</script>

<template>
  <PermissionWorkbenchLayout>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">系统管理</p>
          <h2 class="section-heading">用户与角色</h2>
        </div>
        <el-button @click="loadUsers">刷新</el-button>
      </section>

      <el-form :inline="true" class="toolbar-form">
        <el-form-item label="用户名">
          <el-input v-model="filters.username" placeholder="搜索用户名" clearable />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="filters.realName" placeholder="搜索姓名" clearable />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.roleCode" placeholder="全部角色" clearable style="width: 180px">
            <el-option v-for="role in roles" :key="role.roleId" :label="roleLabel(role.roleCode)" :value="role.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.enabled" placeholder="全部" clearable style="width: 140px">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="loadUsers">查询</el-button>
        </el-form-item>
      </el-form>

      <el-card shadow="never" class="workbench-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">用户与系统角色矩阵</h3>
              <p class="section-subtle">可通过上方条件筛选用户，并在列表中打开角色多选编辑。</p>
            </div>
          </div>
        </template>

        <el-table :data="users" v-loading="loading" border stripe>
          <el-table-column prop="username" label="用户名" min-width="160" />
          <el-table-column prop="realName" label="姓名" min-width="140" />
          <el-table-column prop="deptName" label="部门" min-width="180">
            <template #default="{ row }">
              {{ row.deptName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="240">
            <template #default="{ row }">
              <div class="inline-tag-list">
                <el-tag v-for="roleCode in row.roleCodes" :key="roleCode" size="small">{{ roleLabel(roleCode) }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-space>
                <el-button v-if="row.canEditRoles" link type="primary" @click="openEditor(row)">编辑角色</el-button>
                <el-button
                  v-if="row.canToggleStatus"
                  link
                  :type="row.enabled ? 'danger' : 'success'"
                  :disabled="saving"
                  @click="toggleStatus(row)"
                >
                  {{ row.enabled ? '停用' : '启用' }}
                </el-button>
              </el-space>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="editorVisible" width="860px" title="编辑用户角色" class="user-role-editor-dialog">
      <template v-if="selectedUser">
        <el-skeleton v-if="detailLoading" :rows="7" animated />

        <template v-else>
          <section class="role-editor-user-panel">
            <div class="role-editor-user-main">
              <span class="shell-eyebrow">当前用户</span>
              <strong>{{ selectedUser.realName }}</strong>
              <span>{{ selectedUser.username }} / {{ selectedUser.deptName || '无部门' }}</span>
            </div>
            <div class="role-editor-user-stats">
              <div><span>状态</span><strong>{{ selectedUser.enabled ? '启用' : '停用' }}</strong></div>
              <div><span>现有</span><strong>{{ selectedUser.roleCodes.length }}</strong></div>
              <div><span>已选</span><strong>{{ roleDraft.length }}</strong></div>
              <div><span>变更</span><strong>{{ roleChangeCount }}</strong></div>
            </div>
          </section>

          <section class="role-editor-section">
            <div class="role-editor-section-head">
              <div>
                <h3 class="card-heading">角色分配</h3>
                <p class="section-subtle">选择一个或多个已启用角色。停用角色仅用于查看，不能分配。</p>
              </div>
              <el-tag type="info" effect="plain">{{ enabledRoleCount }} 个可用角色</el-tag>
            </div>

            <el-checkbox-group v-model="roleDraft" class="role-choice-grid">
              <el-checkbox
                v-for="role in roles"
                :key="role.roleId"
                :label="role.roleCode"
                :disabled="saving || !selectedUser.canEditRoles || !role.enabled"
                class="role-choice-card"
              >
                <div class="role-choice-content">
                  <div class="role-choice-title-row">
                    <strong>{{ roleLabel(role.roleCode) }}</strong>
                    <el-tag v-if="!role.enabled" size="small" type="info">已停用</el-tag>
                  </div>
                  <span class="role-choice-code">{{ role.roleCode }}</span>
                  <p>{{ roleDescription(role.roleCode, role.roleDesc) }}</p>
                </div>
              </el-checkbox>
            </el-checkbox-group>
          </section>

          <section class="role-editor-review-panel">
            <div>
              <h3 class="card-heading">已选角色</h3>
              <div class="inline-tag-list">
                <el-tag v-for="role in selectedRoleDetails" :key="role.roleCode" type="primary" effect="light">
                  {{ roleLabel(role.roleCode) }}
                </el-tag>
                <el-tag v-if="!selectedRoleDetails.length" type="info" effect="plain">未选择角色</el-tag>
              </div>
            </div>
            <el-alert type="info" show-icon :closable="false" class="diff-alert">
              <template #title>
                新增：{{ pendingDiff.added.map(roleLabel).join(', ') || '无' }}；移除：{{ pendingDiff.removed.map(roleLabel).join(', ') || '无' }}
              </template>
            </el-alert>
          </section>
        </template>
      </template>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="detailLoading || !selectedUser?.canEditRoles" @click="saveRoles">保存角色</el-button>
      </template>
    </el-dialog>
  </PermissionWorkbenchLayout>
</template>
