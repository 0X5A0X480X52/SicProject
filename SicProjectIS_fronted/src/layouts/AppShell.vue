<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { FolderOpened, House, Lock, SwitchButton, Tickets } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const roles = computed(() => auth.user?.roleCodes ?? [])
const isAdminWorkspaceVisible = computed(
  () =>
    roles.value.includes('SYSTEM_ADMIN') ||
    roles.value.includes('SCIENCE_ADMIN') ||
    roles.value.includes('DEPT_ADMIN'),
)

const currentMainTab = computed(() => {
  if (route.path.startsWith('/admin')) {
    return 'admin'
  }
  if (route.path.startsWith('/node-forms')) {
    return 'node-forms'
  }
  return 'dashboard'
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('退出后将清理当前登录态。', '确认退出', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await auth.logout()
  router.push({ name: 'login' })
}

function handleTabSelect(index: string) {
  if (index === 'dashboard') {
    router.push({ name: 'dashboard' })
    return
  }
  if (index === 'node-forms') {
    router.push({ name: 'node-forms-debug' })
    return
  }
  if (roles.value.includes('SYSTEM_ADMIN') || roles.value.includes('SCIENCE_ADMIN')) {
    router.push({ name: 'admin-overview' })
    return
  }
  router.push({ name: 'admin-project-authorizations' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div>
        <p class="shell-eyebrow">SIC Project IS</p>
        <h1 class="shell-title">Research Workflow Console</h1>
        <p class="shell-subtitle">Security, project access, and workflow operations in one workspace.</p>
      </div>

      <div class="shell-user-meta">
        <el-button link type="primary" @click="router.push({ name: 'projects' })">
          <el-icon><FolderOpened /></el-icon>
          <span>Projects</span>
        </el-button>
        <div class="shell-user-summary">
          <strong>{{ auth.user?.realName }}</strong>
          <div class="shell-role-tags">
            <el-tag v-for="role in roles" :key="role" size="small" type="info">{{ role }}</el-tag>
          </div>
        </div>
        <el-button type="danger" plain @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          <span>Logout</span>
        </el-button>
      </div>
    </header>

    <el-menu
      class="workspace-menu"
      mode="horizontal"
      :default-active="currentMainTab"
      @select="handleTabSelect"
    >
      <el-menu-item index="dashboard">
        <el-icon><House /></el-icon>
        <span>Dashboard</span>
      </el-menu-item>
      <el-menu-item v-if="isAdminWorkspaceVisible" index="admin">
        <el-icon><Lock /></el-icon>
        <span>Permission Center</span>
      </el-menu-item>
      <el-menu-item index="node-forms">
        <el-icon><Tickets /></el-icon>
        <span>Node Forms</span>
      </el-menu-item>
    </el-menu>

    <main class="shell-main">
      <slot />
    </main>
  </div>
</template>
