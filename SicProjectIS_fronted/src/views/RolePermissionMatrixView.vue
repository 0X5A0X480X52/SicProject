<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import { getRolePermissionMatrix, updateRolePermissions } from '../api/admin'
import type { RolePermissionMatrix, RolePermissionMatrixRow } from '../types/admin'

const loading = ref(false)
const saving = ref(false)
const matrix = ref<RolePermissionMatrix | null>(null)
const selectedRoleCode = ref('')
const permissionDraft = ref<string[]>([])
const editorVisible = ref(false)

const selectedRow = computed(() =>
  matrix.value?.matrix.find((row) => row.roleCode === selectedRoleCode.value) ?? null,
)

const permissionGroups = computed(() => {
  const groups = new Map<string, RolePermissionMatrix['permissions']>()
  for (const permission of matrix.value?.permissions ?? []) {
    const list = groups.get(permission.permissionGroup) ?? []
    list.push(permission)
    groups.set(permission.permissionGroup, list)
  }
  return [...groups.entries()]
})

const groupKeys = computed(() => permissionGroups.value.map(([group]) => group))

const pendingDiff = computed(() => {
  const before = selectedRow.value?.permissionCodes ?? []
  const after = permissionDraft.value
  return {
    added: after.filter((code) => !before.includes(code)),
    removed: before.filter((code) => !after.includes(code)),
  }
})

async function loadMatrix() {
  loading.value = true
  try {
    matrix.value = await getRolePermissionMatrix()
    if (!selectedRoleCode.value && matrix.value.matrix.length > 0) {
      selectRole(matrix.value.matrix[0])
    } else if (selectedRoleCode.value) {
      const current = matrix.value.matrix.find((row) => row.roleCode === selectedRoleCode.value)
      if (current) {
        selectRole(current)
      }
    }
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to load role permission matrix')
  } finally {
    loading.value = false
  }
}

function selectRole(row: RolePermissionMatrixRow) {
  selectedRoleCode.value = row.roleCode
  permissionDraft.value = [...row.permissionCodes]
  editorVisible.value = true
}

function handleCurrentChange(row: RolePermissionMatrixRow | null) {
  if (row) {
    selectedRoleCode.value = row.roleCode
    permissionDraft.value = [...row.permissionCodes]
  }
}

function summarizeGroup(row: RolePermissionMatrixRow, group: string) {
  const permissions = permissionGroups.value.find(([key]) => key === group)?.[1] ?? []
  const matched = permissions
    .filter((permission) => row.permissionCodes.includes(permission.permissionCode))
    .map((permission) => permission.permissionName)
  if (matched.length === 0) {
    return '-'
  }
  return matched.join(' / ')
}

async function savePermissions() {
  if (!selectedRoleCode.value) return
  await ElMessageBox.confirm(
    [
      `角色：${selectedRoleCode.value}`,
      `新增权限：${pendingDiff.value.added.join(', ') || '无'}`,
      `移除权限：${pendingDiff.value.removed.join(', ') || '无'}`,
    ].join('\n'),
    '确认提交权限矩阵变更',
    {
      type: 'warning',
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
    },
  )

  saving.value = true
  try {
    const response = await updateRolePermissions(selectedRoleCode.value, {
      permissionCodes: permissionDraft.value,
    })
    matrix.value = response.matrix
    const next = response.matrix.matrix.find((row) => row.roleCode === response.roleCode)
    if (next) {
      selectRole(next)
    }
    ElMessage.success(`已更新 ${response.roleCode} 的权限集合`)
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to update permissions')
  } finally {
    saving.value = false
  }
}

onMounted(loadMatrix)
</script>

<template>
  <PermissionWorkbenchLayout>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">System Administration</p>
          <h2 class="section-heading">Role Permissions</h2>
        </div>
        <el-button @click="loadMatrix">Refresh</el-button>
      </section>

      <el-card shadow="never" class="workbench-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">Role Permission Matrix</h3>
              <p class="section-subtle">Use the role list to review permission coverage and open checkbox-based editing.</p>
            </div>
          </div>
        </template>

        <el-table :data="matrix?.matrix ?? []" v-loading="loading" border stripe highlight-current-row @current-change="handleCurrentChange">
            <el-table-column prop="roleCode" label="Role Code" min-width="180" />
            <el-table-column prop="roleName" label="Role Name" min-width="180" />
            <el-table-column label="Status" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? 'Enabled' : 'Disabled' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-for="group in groupKeys"
              :key="group"
              :label="group"
              min-width="180"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ summarizeGroup(row, group) }}
              </template>
            </el-table-column>
            <el-table-column label="Action" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="selectRole(row)">Edit</el-button>
              </template>
            </el-table-column>
          </el-table>
      </el-card>
    </div>

    <el-dialog v-model="editorVisible" width="720px" title="Edit Role Permissions">
      <template v-if="selectedRow">
        <el-descriptions :column="1" border class="drawer-summary">
          <el-descriptions-item label="Role">{{ selectedRow.roleName }}</el-descriptions-item>
          <el-descriptions-item label="Role Code">{{ selectedRow.roleCode }}</el-descriptions-item>
          <el-descriptions-item label="Status">{{ selectedRow.enabled ? 'Enabled' : 'Disabled' }}</el-descriptions-item>
        </el-descriptions>

        <div class="permission-groups">
          <section v-for="[group, items] in permissionGroups" :key="group" class="permission-group">
            <h3 class="group-heading">{{ group }}</h3>
            <el-checkbox-group v-model="permissionDraft" class="checkbox-grid-list">
              <el-checkbox
                v-for="permission in items"
                :key="permission.permissionId"
                :label="permission.permissionCode"
                border
              >
                {{ permission.permissionCode }}
              </el-checkbox>
            </el-checkbox-group>
          </section>
        </div>

        <el-alert type="info" :closable="false" show-icon class="diff-alert">
          <template #title>
            新增：{{ pendingDiff.added.join(', ') || '无' }}；移除：{{ pendingDiff.removed.join(', ') || '无' }}
          </template>
        </el-alert>
      </template>

      <template #footer>
        <el-button @click="editorVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="savePermissions">Save Permissions</el-button>
      </template>
    </el-dialog>
  </PermissionWorkbenchLayout>
</template>
