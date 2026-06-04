<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import { getAdminProjectAuthorizations, getAuditLogs } from '../api/admin'
import type { AdminProjectAuthorizationIndex, AuditLogRecord } from '../types/admin'

const loading = ref(false)
const logs = ref<AuditLogRecord[]>([])
const projectIndex = ref<AdminProjectAuthorizationIndex | null>(null)

const filters = reactive({
  keyword: '',
  actionType: '',
  scopeType: '',
  projectId: '',
})

const actionOptions = ['UPDATE', 'GRANT', 'REVOKE']
const scopeOptions = ['SYSTEM_ROLE', 'ROLE_PERMISSION', 'PROJECT_GRANT', 'PROJECT_LEADER']
const filteredCountLabel = computed(() => `${logs.value.length} logs`)

async function loadMeta() {
  try {
    projectIndex.value = await getAdminProjectAuthorizations()
  } catch {
    projectIndex.value = null
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const response = await getAuditLogs({
      keyword: filters.keyword || undefined,
      actionType: filters.actionType || undefined,
      scopeType: filters.scopeType || undefined,
      projectId: filters.projectId ? Number(filters.projectId) : null,
    })
    logs.value = response.logs
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to load audit logs')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.actionType = ''
  filters.scopeType = ''
  filters.projectId = ''
  loadLogs()
}

onMounted(async () => {
  await Promise.all([loadMeta(), loadLogs()])
})
</script>

<template>
  <PermissionWorkbenchLayout>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">Permission Administration</p>
          <h2 class="section-heading">Audit Logs</h2>
        </div>
        <el-button @click="loadLogs">Refresh</el-button>
      </section>

      <el-card shadow="never" class="workbench-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">Authorization Audit Log</h3>
              <p class="section-subtle">Use filters from the toolbar and expand rows to inspect before/after snapshots.</p>
            </div>
          </div>
        </template>

      <el-form :inline="true" class="toolbar-form toolbar-form-plain">
        <el-form-item label="Keyword">
          <el-input v-model="filters.keyword" placeholder="Search remark, role, user" clearable />
        </el-form-item>
        <el-form-item label="Action">
          <el-select v-model="filters.actionType" clearable placeholder="All actions" style="width: 160px">
            <el-option v-for="action in actionOptions" :key="action" :label="action" :value="action" />
          </el-select>
        </el-form-item>
        <el-form-item label="Scope">
          <el-select v-model="filters.scopeType" clearable placeholder="All scopes" style="width: 180px">
            <el-option v-for="scope in scopeOptions" :key="scope" :label="scope" :value="scope" />
          </el-select>
        </el-form-item>
        <el-form-item label="Project">
          <el-select v-model="filters.projectId" clearable placeholder="All projects" style="width: 220px">
            <el-option
              v-for="project in projectIndex?.projects ?? []"
              :key="project.projectId"
              :label="project.projectName"
              :value="String(project.projectId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">Reset</el-button>
          <el-button type="primary" @click="loadLogs">Search</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" v-loading="loading" border stripe row-key="logId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="audit-expand">
              <p><strong>Grant Type:</strong> {{ row.grantType || '-' }}</p>
              <p><strong>Role Code:</strong> {{ row.roleCode || '-' }}</p>
              <p><strong>Permission Code:</strong> {{ row.permissionCode || '-' }}</p>
              <p><strong>Remark:</strong> {{ row.remark || '-' }}</p>
              <div class="audit-snapshots">
                <div>
                  <strong>Before</strong>
                  <pre>{{ row.beforeSnapshot || '-' }}</pre>
                </div>
                <div>
                  <strong>After</strong>
                  <pre>{{ row.afterSnapshot || '-' }}</pre>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="scopeType" label="Scope" width="160" />
        <el-table-column prop="actionType" label="Action" width="120" />
        <el-table-column label="Project" min-width="180">
          <template #default="{ row }">{{ row.projectName || 'System scope' }}</template>
        </el-table-column>
        <el-table-column label="Operator" min-width="150">
          <template #default="{ row }">{{ row.operatorUser?.realName || 'Unknown' }}</template>
        </el-table-column>
        <el-table-column label="Target" min-width="150">
          <template #default="{ row }">{{ row.targetUser?.realName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="Remark" min-width="220" />
        <el-table-column label="Created At" width="190">
          <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
        </el-table-column>
      </el-table>

      <p class="table-footnote">{{ filteredCountLabel }}</p>
      </el-card>
    </div>
  </PermissionWorkbenchLayout>
</template>
