<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import { getRolePermissionMatrix, updateRolePermissions } from '../api/admin'
import type { PermissionDefinition, RolePermissionMatrix, RolePermissionMatrixRow } from '../types/admin'

const loading = ref(false)
const saving = ref(false)
const matrix = ref<RolePermissionMatrix | null>(null)
const selectedRoleCode = ref('')
const permissionDraft = ref<string[]>([])
const editorVisible = ref(false)

const roleRows = computed(() => matrix.value?.matrix ?? [])
const permissions = computed(() => matrix.value?.permissions ?? [])
const selectedRow = computed(() =>
  roleRows.value.find((row) => row.roleCode === selectedRoleCode.value) ?? null,
)
const selectedRoleOption = computed(() =>
  matrix.value?.roles.find((role) => role.roleCode === selectedRoleCode.value) ?? null,
)

const permissionGroups = computed(() => {
  const groups = new Map<string, RolePermissionMatrix['permissions']>()
  for (const permission of permissions.value) {
    const list = groups.get(permission.permissionGroup) ?? []
    list.push(permission)
    groups.set(permission.permissionGroup, list)
  }
  return [...groups.entries()]
})

const pendingDiff = computed(() => {
  const before = selectedRow.value?.permissionCodes ?? []
  const after = permissionDraft.value
  return {
    added: after.filter((code) => !before.includes(code)),
    removed: before.filter((code) => !after.includes(code)),
  }
})
const changeCount = computed(() => pendingDiff.value.added.length + pendingDiff.value.removed.length)
const canSavePermissionChanges = computed(() => Boolean(selectedRow.value) && changeCount.value > 0 && !saving.value)

async function loadMatrix() {
  loading.value = true
  try {
    matrix.value = await getRolePermissionMatrix()
    const next = selectedRoleCode.value
      ? roleRows.value.find((row) => row.roleCode === selectedRoleCode.value)
      : roleRows.value[0]
    if (next) {
      setSelectedRole(next)
    }
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '角色权限矩阵加载失败')
  } finally {
    loading.value = false
  }
}

function setSelectedRole(row: RolePermissionMatrixRow) {
  selectedRoleCode.value = row.roleCode
  permissionDraft.value = [...row.permissionCodes]
}

function openEditor(row: RolePermissionMatrixRow) {
  setSelectedRole(row)
  editorVisible.value = true
}

function hasPermission(row: RolePermissionMatrixRow, permissionCode: string) {
  return row.permissionCodes.includes(permissionCode)
}

function permissionDisplay(permissionCode: string) {
  const permission = permissions.value.find((item) => item.permissionCode === permissionCode)
  return permission?.permissionName || permissionCode
}

function permissionDescription(permission: PermissionDefinition) {
  return permission.permissionDesc || permission.permissionCode
}

function groupCodes(items: PermissionDefinition[]) {
  return items.map((item) => item.permissionCode)
}

function isGroupChecked(items: PermissionDefinition[]) {
  const codes = groupCodes(items)
  return codes.length > 0 && codes.every((code) => permissionDraft.value.includes(code))
}

function isGroupIndeterminate(items: PermissionDefinition[]) {
  const codes = groupCodes(items)
  const selected = codes.filter((code) => permissionDraft.value.includes(code)).length
  return selected > 0 && selected < codes.length
}

function toggleGroup(items: PermissionDefinition[], checked: boolean) {
  const codes = groupCodes(items)
  if (checked) {
    permissionDraft.value = [...new Set([...permissionDraft.value, ...codes])]
  } else {
    permissionDraft.value = permissionDraft.value.filter((code) => !codes.includes(code))
  }
}

function resetDraft() {
  if (selectedRow.value) {
    permissionDraft.value = [...selectedRow.value.permissionCodes]
  }
}

async function savePermissions() {
  if (!selectedRoleCode.value || !canSavePermissionChanges.value) return
  await ElMessageBox.confirm(
    [
      `角色：${selectedRoleCode.value}`,
      `新增权限：${pendingDiff.value.added.map(permissionDisplay).join(', ') || '无'}`,
      `移除权限：${pendingDiff.value.removed.map(permissionDisplay).join(', ') || '无'}`,
    ].join('\n'),
    '确认权限变更',
    {
      type: 'warning',
      confirmButtonText: '保存变更',
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
      setSelectedRole(next)
    }
    ElMessage.success(`已更新 ${response.roleCode} 的权限集合`)
    editorVisible.value = false
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '权限更新失败')
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
          <p class="shell-eyebrow">系统管理</p>
          <h2 class="section-heading">角色权限</h2>
        </div>
        <el-button @click="loadMatrix">刷新</el-button>
      </section>

      <el-card shadow="never" class="workbench-card role-permission-matrix-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">角色权限矩阵</h3>
              <p class="section-subtle">纵向为权限，横向为角色编码；点击角色列头的“编辑”维护该角色权限。</p>
            </div>
            <el-tag type="info" effect="plain">{{ permissions.length }} 项权限 / {{ roleRows.length }} 个角色</el-tag>
          </div>
        </template>

        <el-table :data="permissions" v-loading="loading" border stripe class="role-permission-table">
          <el-table-column fixed prop="permissionGroup" label="权限分组" min-width="150" />
          <el-table-column fixed label="权限" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="permission-cell-main">
                <strong>{{ row.permissionName }}</strong>
                <span>{{ row.permissionCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              {{ permissionDescription(row) }}
            </template>
          </el-table-column>
          <el-table-column
            v-for="role in roleRows"
            :key="role.roleCode"
            :label="role.roleCode"
            min-width="150"
            align="center"
          >
            <template #header>
              <div class="role-column-head">
                <strong>{{ role.roleCode }}</strong>
                <el-button link type="primary" @click.stop="openEditor(role)">编辑</el-button>
              </div>
            </template>
            <template #default="{ row }">
              <el-tag v-if="hasPermission(role, row.permissionCode)" type="success" effect="light">已授权</el-tag>
              <el-tag v-else type="info" effect="plain">-</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="editorVisible" width="860px" title="编辑角色权限" class="role-permission-editor-dialog">
      <template v-if="selectedRow">
        <section class="role-permission-editor-head">
          <div>
            <span class="shell-eyebrow">当前角色</span>
            <strong>{{ selectedRow.roleName }}</strong>
            <span>{{ selectedRow.roleCode }}</span>
          </div>
          <div class="role-permission-editor-stats">
            <div><span>状态</span><strong>{{ selectedRow.enabled ? '启用' : '停用' }}</strong></div>
            <div><span>已授权</span><strong>{{ permissionDraft.length }}</strong></div>
            <div><span>变更</span><strong>{{ changeCount }}</strong></div>
          </div>
        </section>

        <el-alert
          v-if="selectedRoleOption?.roleDesc"
          type="info"
          :closable="false"
          show-icon
          class="diff-alert"
          :title="selectedRoleOption.roleDesc"
        />

        <div class="permission-groups role-permission-editor-groups">
          <section v-for="[group, items] in permissionGroups" :key="group" class="permission-group role-permission-editor-group">
            <div class="role-permission-group-head">
              <h3 class="group-heading">{{ group }}</h3>
              <el-checkbox
                :model-value="isGroupChecked(items)"
                :indeterminate="isGroupIndeterminate(items)"
                @change="(checked: boolean) => toggleGroup(items, checked)"
              >
                选择本组
              </el-checkbox>
            </div>
            <el-checkbox-group v-model="permissionDraft" class="permission-choice-grid">
              <el-checkbox
                v-for="permission in items"
                :key="permission.permissionId"
                :label="permission.permissionCode"
                class="permission-choice-card"
              >
                <div class="permission-choice-content">
                  <strong>{{ permission.permissionName }}</strong>
                  <span>{{ permission.permissionCode }}</span>
                  <p>{{ permissionDescription(permission) }}</p>
                </div>
              </el-checkbox>
            </el-checkbox-group>
          </section>
        </div>

        <el-alert type="info" :closable="false" show-icon class="diff-alert">
          <template #title>
            新增：{{ pendingDiff.added.map(permissionDisplay).join(', ') || '无' }}；移除：{{ pendingDiff.removed.map(permissionDisplay).join(', ') || '无' }}
          </template>
        </el-alert>
      </template>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button :disabled="!changeCount || saving" @click="resetDraft">撤销变更</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSavePermissionChanges" @click="savePermissions">保存权限</el-button>
      </template>
    </el-dialog>
  </PermissionWorkbenchLayout>
</template>
