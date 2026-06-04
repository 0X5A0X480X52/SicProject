<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import { getAdminUserDetail, getAdminUsers, updateUserRoles, updateUserStatus } from '../api/admin'
import type { AdminUserDetail, AdminUserListItem, AdminUserQueryResponse, ChangeDiffSummary } from '../types/admin'

const loading = ref(false)
const saving = ref(false)
const data = ref<AdminUserQueryResponse | null>(null)
const selectedUser = ref<AdminUserDetail | null>(null)
const editorVisible = ref(false)
const detailLoading = ref(false)
const roleDraft = ref<string[]>([])
const userSearch = ref('')

const filters = reactive({
  username: '',
  realName: '',
  roleCode: '',
  enabled: '',
})

const roles = computed(() => data.value?.roles ?? [])
const users = computed(() => data.value?.users ?? [])
const filteredUsers = computed(() => {
  if (!userSearch.value.trim()) {
    return users.value
  }
  const keyword = userSearch.value.trim().toLowerCase()
  return users.value.filter((user) =>
    [user.username, user.realName, user.deptName, ...user.roleCodes]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
})
const pendingDiff = computed<ChangeDiffSummary>(() => buildDiff(selectedUser.value?.roleCodes ?? [], roleDraft.value))

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
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to load users')
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
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to load user detail')
  } finally {
    detailLoading.value = false
  }
}

async function saveRoles() {
  if (!selectedUser.value) return
  await ElMessageBox.confirm(
    [
      `用户：${selectedUser.value.realName} (${selectedUser.value.username})`,
      `新增角色：${pendingDiff.value.added.join(', ') || '无'}`,
      `移除角色：${pendingDiff.value.removed.join(', ') || '无'}`,
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
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to update roles')
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
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to update status')
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
          <p class="shell-eyebrow">System Administration</p>
          <h2 class="section-heading">Users & Roles</h2>
        </div>
        <el-button @click="loadUsers">Refresh</el-button>
      </section>

      <el-form :inline="true" class="toolbar-form">
        <el-form-item label="Username">
          <el-input v-model="filters.username" placeholder="Search username" clearable />
        </el-form-item>
        <el-form-item label="Real Name">
          <el-input v-model="filters.realName" placeholder="Search name" clearable />
        </el-form-item>
        <el-form-item label="Role">
          <el-select v-model="filters.roleCode" placeholder="All roles" clearable style="width: 180px">
            <el-option v-for="role in roles" :key="role.roleId" :label="role.roleCode" :value="role.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="filters.enabled" placeholder="All" clearable style="width: 140px">
            <el-option label="Enabled" value="true" />
            <el-option label="Disabled" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">Reset</el-button>
          <el-button type="primary" @click="loadUsers">Search</el-button>
        </el-form-item>
      </el-form>

      <el-card shadow="never" class="workbench-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">User And System Role Matrix</h3>
              <p class="section-subtle">Search from the toolbar, then use the list to open checkbox-based role editing.</p>
            </div>
          </div>
        </template>

      <el-table :data="users" v-loading="loading" border stripe>
        <el-table-column prop="username" label="Username" min-width="160" />
        <el-table-column prop="realName" label="Real Name" min-width="140" />
        <el-table-column prop="deptName" label="Department" min-width="180">
          <template #default="{ row }">
            {{ row.deptName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="Roles" min-width="240">
          <template #default="{ row }">
            <div class="inline-tag-list">
              <el-tag v-for="roleCode in row.roleCodes" :key="roleCode" size="small">{{ roleCode }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="110">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? 'Enabled' : 'Disabled' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="220" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button v-if="row.canEditRoles" link type="primary" @click="openEditor(row)">Edit Roles</el-button>
              <el-button
                v-if="row.canToggleStatus"
                link
                :type="row.enabled ? 'danger' : 'success'"
                :disabled="saving"
                @click="toggleStatus(row)"
              >
                {{ row.enabled ? 'Disable' : 'Enable' }}
              </el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
      </el-card>
    </div>

    <el-dialog v-model="editorVisible" width="760px" title="Edit User Roles">
      <template v-if="selectedUser">
        <el-skeleton v-if="detailLoading" :rows="6" animated />

        <template v-else>
          <div class="dialog-two-column">
            <section class="dialog-side-list">
              <el-input v-model="userSearch" placeholder="Search user by name, department, role" clearable />
              <el-table
                :data="filteredUsers"
                border
                stripe
                highlight-current-row
                height="360"
                @current-change="(row: AdminUserListItem | null) => row && openEditor(row)"
              >
                <el-table-column prop="realName" label="User" min-width="140" />
                <el-table-column prop="deptName" label="Department" min-width="140" />
              </el-table>
            </section>

            <section>
              <el-descriptions :column="1" border class="drawer-summary">
                <el-descriptions-item label="User">{{ selectedUser.realName }}</el-descriptions-item>
                <el-descriptions-item label="Username">{{ selectedUser.username }}</el-descriptions-item>
                <el-descriptions-item label="Department">{{ selectedUser.deptName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Status">
                  {{ selectedUser.enabled ? 'Enabled' : 'Disabled' }}
                </el-descriptions-item>
              </el-descriptions>

              <el-checkbox-group v-model="roleDraft" class="checkbox-grid-list">
                <el-checkbox
                  v-for="role in roles"
                  :key="role.roleId"
                  :label="role.roleCode"
                  :disabled="saving || !selectedUser.canEditRoles || !role.enabled"
                  border
                >
                  {{ role.roleCode }}
                </el-checkbox>
              </el-checkbox-group>
            </section>
          </div>

          <el-alert type="info" show-icon :closable="false" class="diff-alert">
            <template #title>
              新增：{{ pendingDiff.added.join(', ') || '无' }}；移除：{{ pendingDiff.removed.join(', ') || '无' }}
            </template>
          </el-alert>
        </template>
      </template>
      <template #footer>
        <el-button @click="editorVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">Save Roles</el-button>
      </template>
    </el-dialog>
  </PermissionWorkbenchLayout>
</template>
