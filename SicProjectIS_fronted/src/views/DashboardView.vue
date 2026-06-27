<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { listProjects } from '../api/projects'
import { ApiError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { lifecycleLabel, roleLabel } from '../utils/displayLabels'
import type { ProjectSummary } from '../types/project'

const auth = useAuthStore()
const loadingProjects = ref(false)
const projectError = ref('')
const projects = ref<ProjectSummary[]>([])

const roleCodes = computed(() => auth.user?.roleCodes ?? [])
const permissionCodes = computed(() => auth.user?.permissionCodes ?? [])
const canOpenPermissionCenter = computed(() =>
  roleCodes.value.some((roleCode) => ['SYSTEM_ADMIN', 'SCIENCE_ADMIN', 'DEPT_ADMIN'].includes(roleCode)),
)
const canStartWorkflow = computed(() =>
  roleCodes.value.some((roleCode) => ['SYSTEM_ADMIN', 'SCIENCE_ADMIN', 'PROJECT_LEADER'].includes(roleCode)),
)

const projectCount = computed(() => projects.value.length)
const ownedProjectCount = computed(() =>
  projects.value.filter((project) => project.leaderUserId === auth.user?.userId).length,
)
const deptProjectCount = computed(() =>
  projects.value.filter((project) => project.deptId != null && project.deptId === auth.user?.deptId).length,
)
const activeProjectCount = computed(() =>
  projects.value.filter((project) => !['ARCHIVED', 'FINISHED', 'CLOSED'].includes(String(project.lifecycleStage ?? '').toUpperCase())).length,
)
const recentProjects = computed(() => projects.value.slice(0, 5))
const rolePreview = computed(() => roleCodes.value.map(roleLabel).join(' / ') || '暂无系统角色')
const permissionPreview = computed(() => permissionCodes.value.slice(0, 10))
const lifecycleEntries = computed(() => {
  const counts = new Map<string, number>()
  for (const project of projects.value) {
    const key = project.lifecycleStage || 'UNKNOWN'
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6)
})

async function loadDashboardProjects() {
  loadingProjects.value = true
  projectError.value = ''
  try {
    projects.value = await listProjects()
  } catch (err) {
    projectError.value = err instanceof ApiError ? err.message : '项目数据加载失败'
  } finally {
    loadingProjects.value = false
  }
}

onMounted(() => {
  auth.refreshCurrentUser()
  loadDashboardProjects()
})
</script>

<template>
  <AppShell>
    <section class="dashboard-workspace-hero panel">
      <div class="dashboard-workspace-main">
        <p class="eyebrow">工作台</p>
        <h2>{{ auth.user?.realName ?? auth.user?.username ?? '用户' }}，欢迎回来</h2>
        <div class="dashboard-user-line">
          <span>{{ auth.user?.deptName || '未设置部门' }}</span>
          <span>{{ rolePreview }}</span>
        </div>
      </div>
      <div class="dashboard-quick-actions">
        <RouterLink class="text-button primary-link" :to="{ name: 'projects' }">查看项目</RouterLink>
        <RouterLink v-if="canStartWorkflow" class="text-button" :to="{ name: 'workflow' }">发起/办理流程</RouterLink>
        <RouterLink v-if="canOpenPermissionCenter" class="text-button" :to="{ name: 'admin-project-authorizations' }">项目授权</RouterLink>
      </div>
    </section>

    <section class="dashboard-insight-grid">
      <article class="panel stat-card">
        <span class="stat-label">可访问项目</span>
        <strong class="stat-value">{{ projectCount }}</strong>
        <p class="helper-text">当前账号可查看或参与的项目总数</p>
      </article>
      <article class="panel stat-card">
        <span class="stat-label">我负责的项目</span>
        <strong class="stat-value">{{ ownedProjectCount }}</strong>
        <p class="helper-text">负责人为当前用户的项目</p>
      </article>
      <article class="panel stat-card">
        <span class="stat-label">本部门项目</span>
        <strong class="stat-value">{{ deptProjectCount }}</strong>
        <p class="helper-text">与当前用户部门匹配的项目</p>
      </article>
      <article class="panel stat-card">
        <span class="stat-label">进行中项目</span>
        <strong class="stat-value">{{ activeProjectCount }}</strong>
        <p class="helper-text">未归档或未关闭的项目</p>
      </article>
    </section>

    <p v-if="projectError" class="form-error">{{ projectError }}</p>

    <section class="dashboard-main-grid">
      <article class="panel dashboard-list-panel">
        <div class="section-title">
          <div>
            <h3>最近可访问项目</h3>
            <p class="helper-text">优先进入授权、材料和流程办理场景</p>
          </div>
          <RouterLink class="text-button" :to="{ name: 'projects' }">全部项目</RouterLink>
        </div>
        <div v-if="loadingProjects" class="helper-text">正在加载项目...</div>
        <div v-else-if="!recentProjects.length" class="helper-text">当前暂无可访问项目。</div>
        <div v-else class="dashboard-project-short-list">
          <article v-for="project in recentProjects" :key="project.projectId" class="dashboard-project-short-item">
            <div>
              <span>{{ project.projectCode }}</span>
              <strong>{{ project.projectName }}</strong>
              <p>{{ project.deptName || '未设置部门' }} / {{ project.leaderRealName || '未设置负责人' }}</p>
            </div>
            <RouterLink class="text-button" :to="{ name: 'project-authorization', params: { projectId: project.projectId } }">进入</RouterLink>
          </article>
        </div>
      </article>

      <article class="panel dashboard-side-panel">
        <div class="section-title">
          <h3>生命周期分布</h3>
          <span class="helper-text">{{ lifecycleEntries.length }} 类状态</span>
        </div>
        <div v-if="!lifecycleEntries.length" class="helper-text">暂无项目状态数据。</div>
        <div v-else class="dashboard-state-list">
          <div v-for="[stage, count] in lifecycleEntries" :key="stage">
            <span>{{ lifecycleLabel(stage) }}</span>
            <strong>{{ count }}</strong>
          </div>
        </div>
      </article>

      <article class="panel dashboard-side-panel">
        <div class="section-title">
          <h3>权限摘要</h3>
          <span class="helper-text">{{ permissionCodes.length }} 项</span>
        </div>
        <div class="tag-list compact-tags">
          <span v-for="permission in permissionPreview" :key="permission" class="tag muted">{{ permission }}</span>
          <span v-if="permissionCodes.length > permissionPreview.length" class="tag">+{{ permissionCodes.length - permissionPreview.length }}</span>
          <span v-if="!permissionCodes.length" class="tag muted">暂无权限</span>
        </div>
      </article>
    </section>
  </AppShell>
</template>
