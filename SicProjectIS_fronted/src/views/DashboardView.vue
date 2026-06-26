<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { useAuthStore } from '../stores/auth'
import { roleLabel } from '../utils/displayLabels'

const auth = useAuthStore()

const summaryCards = computed(() => [
  {
    label: 'Account',
    value: auth.user?.username ?? '-',
    note: auth.user?.realName ?? 'Unknown user',
  },
  {
    label: 'Department',
    value: auth.user?.deptName ?? 'Unassigned',
    note: auth.user?.deptId == null ? 'Profile can be completed later' : `Dept ID ${auth.user.deptId}`,
  },
  {
    label: 'Roles',
    value: String(auth.user?.roleCodes.length ?? 0),
    note: (auth.user?.roleCodes ?? []).map(roleLabel).join(', ') || 'No system roles',
  },
  {
    label: 'Permissions',
    value: String(auth.user?.permissionCodes.length ?? 0),
    note: 'Permission set resolved from current roles',
  },
])

const canOpenPermissionCenter = computed(() =>
  (auth.user?.roleCodes ?? []).some((roleCode) =>
    ['SYSTEM_ADMIN', 'SCIENCE_ADMIN', 'DEPT_ADMIN'].includes(roleCode),
  ),
)

onMounted(() => {
  auth.refreshCurrentUser()
})
</script>

<template>
  <AppShell>
    <section class="dashboard-hero panel">
      <div>
        <p class="eyebrow">Workspace</p>
        <h2>Welcome back, {{ auth.user?.realName ?? 'User' }}</h2>
        <p class="dashboard-hero-copy">
          Use the dashboard for quick orientation, then move into the permission center when you
          need to manage roles, grants, and audit activity.
        </p>
      </div>
      <div class="dashboard-hero-actions">
        <RouterLink class="text-button primary-link" :to="{ name: 'projects' }">
          Open Project Catalog
        </RouterLink>
        <RouterLink
          v-if="canOpenPermissionCenter"
          class="text-button"
          :to="{ name: 'admin-project-authorizations' }"
        >
          Open Permission Center
        </RouterLink>
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="card in summaryCards" :key="card.label" class="panel stat-card">
        <span class="stat-label">{{ card.label }}</span>
        <strong class="stat-value">{{ card.value }}</strong>
        <p class="helper-text">{{ card.note }}</p>
      </article>
    </section>

    <section class="dashboard-sections">
      <article class="panel">
        <div class="section-title">
          <h3>System Roles</h3>
          <span class="helper-text">{{ auth.user?.roleCodes.length ?? 0 }} assigned</span>
        </div>
        <div class="tag-list">
          <span v-for="role in auth.user?.roleCodes" :key="role" class="tag">{{ roleLabel(role) }}</span>
        </div>
      </article>

      <article class="panel">
        <div class="section-title">
          <h3>Resolved Permissions</h3>
          <span class="helper-text">{{ auth.user?.permissionCodes.length ?? 0 }} available</span>
        </div>
        <div class="tag-list">
          <span
            v-for="permission in auth.user?.permissionCodes"
            :key="permission"
            class="tag muted"
          >
            {{ permission }}
          </span>
        </div>
      </article>
    </section>
  </AppShell>
</template>

