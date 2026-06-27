<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataAnalysis, Key, List, Management, Medal, OfficeBuilding, User } from '@element-plus/icons-vue'
import AppShell from './AppShell.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roleCodes = computed(() => auth.user?.roleCodes ?? [])
const isSystemAdmin = computed(() => roleCodes.value.includes('SYSTEM_ADMIN'))
const isScienceAdmin = computed(() => roleCodes.value.includes('SCIENCE_ADMIN'))
const isDeptAdmin = computed(() => roleCodes.value.includes('DEPT_ADMIN'))

const items = computed(() => {
  const links: Array<{ label: string; routeName: string; icon: unknown }> = []
  if (isSystemAdmin.value || isScienceAdmin.value) {
    links.push({ label: '权限总览', routeName: 'admin-overview', icon: DataAnalysis })
  }
  if (isSystemAdmin.value) {
    links.push({ label: '用户与角色', routeName: 'admin-users', icon: User })
    links.push({ label: '角色权限', routeName: 'admin-roles-permissions', icon: Key })
  }
  if (isSystemAdmin.value || isScienceAdmin.value || isDeptAdmin.value) {
    links.push({ label: '项目授权', routeName: 'admin-project-authorizations', icon: Management })
  }
  if (isSystemAdmin.value || isScienceAdmin.value || isDeptAdmin.value) {
    links.push({ label: '专家资格审核', routeName: 'admin-expert-qualification', icon: Medal })
    links.push({ label: '部门管理', routeName: 'admin-department-members', icon: OfficeBuilding })
  }
  if (isSystemAdmin.value || isScienceAdmin.value) {
    links.push({ label: '审计日志', routeName: 'admin-audit-logs', icon: List })
  }
  return links
})

const activeRoute = computed(() => String(route.name ?? ''))

function handleSelect(routeName: string) {
  router.push({ name: routeName })
}
</script>

<template>
  <AppShell>
    <section class="permission-shell">
      <aside class="permission-sidebar">
        <div class="permission-sidebar-head">
          <p class="shell-eyebrow">权限中心</p>
          <h2 class="section-heading">授权管理</h2>
          <p class="section-subtle">集中管理全局角色、项目授权和审计记录。</p>
        </div>

        <el-menu
          class="permission-menu"
          mode="horizontal"
          :default-active="activeRoute"
          @select="handleSelect"
        >
          <el-menu-item v-for="item in items" :key="item.routeName" :index="item.routeName">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <div class="permission-content">
        <slot />
      </div>
    </section>
  </AppShell>
</template>



