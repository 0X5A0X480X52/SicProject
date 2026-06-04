<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import { getAdminOverview } from '../api/admin'
import type { AdminOverview } from '../types/admin'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const overview = ref<AdminOverview | null>(null)

const statRows = computed(() => {
  if (!overview.value) return []
  return [
    { label: 'Users', value: overview.value.totalUsers },
    { label: 'Enabled Users', value: overview.value.enabledUsers },
    { label: 'Roles', value: overview.value.totalRoles },
    { label: 'Permissions', value: overview.value.totalPermissions },
    { label: 'Active Grants', value: overview.value.activeProjectGrants },
    { label: 'Audit Logs', value: overview.value.auditLogCount },
  ]
})

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await getAdminOverview()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to load overview')
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<template>
  <PermissionWorkbenchLayout>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">Permission Administration</p>
          <h2 class="section-heading">Overview</h2>
        </div>
        <el-button @click="loadOverview">Refresh</el-button>
      </section>

      <el-skeleton v-if="loading" :rows="10" animated />

      <template v-else-if="overview">
        <div class="overview-grid">
          <el-card shadow="never">
            <template #header>
              <div class="card-title-row">
                <span>Platform Snapshot</span>
              </div>
            </template>
            <el-table :data="statRows" border stripe>
              <el-table-column prop="label" label="Metric" min-width="180" />
              <el-table-column prop="value" label="Value" width="120" />
            </el-table>
          </el-card>

          <el-card shadow="never">
            <template #header>
              <div class="card-title-row">
                <span>Project Grant Distribution</span>
                <el-button link type="primary" @click="router.push({ name: 'admin-project-authorizations' })">
                  Open Grants
                </el-button>
              </div>
            </template>
            <el-table :data="overview.grantTypeCounts" border stripe>
              <el-table-column prop="label" label="Grant Type" min-width="220" />
              <el-table-column prop="count" label="Count" width="120" />
            </el-table>
          </el-card>

          <el-card shadow="never">
            <template #header>
              <div class="card-title-row">
                <span>System Role Coverage</span>
                <el-button link type="primary" @click="router.push({ name: 'admin-users' })">
                  Open Users
                </el-button>
              </div>
            </template>
            <el-table :data="overview.roleCounts" border stripe>
              <el-table-column prop="label" label="Role" min-width="220" />
              <el-table-column prop="count" label="Users" width="120" />
            </el-table>
          </el-card>
        </div>
      </template>
    </div>
  </PermissionWorkbenchLayout>
</template>
