<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { listProjects } from '../api/projects'
import type { ProjectSummary } from '../types/project'
import { ApiError } from '../api/client'
import { lifecycleLabel } from '../utils/displayLabels'

const loading = ref(false)
const error = ref('')
const projects = ref<ProjectSummary[]>([])
const keyword = ref('')
const lifecycleFilter = ref('')
const deptFilter = ref('')

const emptyState = computed(() => !loading.value && projects.value.length === 0)
const lifecycleOptions = computed(() => uniqueOptions(projects.value.map((project) => project.lifecycleStage)))
const deptOptions = computed(() => uniqueOptions(projects.value.map((project) => project.deptName)))
const leaderCount = computed(() => new Set(projects.value.map((project) => project.leaderUserId).filter(Boolean)).size)
const activeCount = computed(() =>
  projects.value.filter((project) => !['ARCHIVED', 'FINISHED', 'CLOSED'].includes(String(project.lifecycleStage ?? '').toUpperCase())).length,
)
const filteredProjects = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return projects.value.filter((project) => {
    const matchesKeyword = !text || [
      project.projectCode,
      project.projectName,
      project.deptName,
      project.leaderRealName,
      project.projectType,
      project.projectLevel,
    ].some((value) => String(value ?? '').toLowerCase().includes(text))
    const matchesLifecycle = !lifecycleFilter.value || project.lifecycleStage === lifecycleFilter.value
    const matchesDept = !deptFilter.value || project.deptName === deptFilter.value
    return matchesKeyword && matchesLifecycle && matchesDept
  })
})
const lifecycleSummary = computed(() => {
  const counts = new Map<string, number>()
  for (const project of projects.value) {
    const key = project.lifecycleStage || 'UNKNOWN'
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
})

function uniqueOptions(values: Array<string | null>) {
  return [...new Set(values.filter((value): value is string => Boolean(value)))].sort((a, b) => a.localeCompare(b))
}

function resetFilters() {
  keyword.value = ''
  lifecycleFilter.value = ''
  deptFilter.value = ''
}

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    projects.value = await listProjects()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '项目列表加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<template>
  <AppShell>
    <section class="projects-hero panel">
      <div>
        <p class="eyebrow">项目目录</p>
        <h2>可访问项目</h2>
        <p class="helper-text">按名称、编号、部门、负责人快速定位项目，并进入授权维护或后续流程处理。</p>
      </div>
      <div class="dashboard-quick-actions">
        <RouterLink class="text-button primary-link" :to="{ name: 'workflow' }">流程工作台</RouterLink>
        <button type="button" class="secondary-button" @click="loadProjects">刷新</button>
      </div>
    </section>

    <section class="project-stat-strip">
      <article class="panel stat-card">
        <span class="stat-label">项目总数</span>
        <strong class="stat-value">{{ projects.length }}</strong>
      </article>
      <article class="panel stat-card">
        <span class="stat-label">进行中</span>
        <strong class="stat-value">{{ activeCount }}</strong>
      </article>
      <article class="panel stat-card">
        <span class="stat-label">涉及部门</span>
        <strong class="stat-value">{{ deptOptions.length }}</strong>
      </article>
      <article class="panel stat-card">
        <span class="stat-label">项目负责人</span>
        <strong class="stat-value">{{ leaderCount }}</strong>
      </article>
    </section>

    <section class="project-toolbar panel">
      <label>
        <span>关键词</span>
        <input v-model="keyword" type="search" placeholder="项目名称 / 编号 / 负责人 / 类型" />
      </label>
      <label>
        <span>生命周期</span>
        <select v-model="lifecycleFilter">
          <option value="">全部状态</option>
          <option v-for="stage in lifecycleOptions" :key="stage" :value="stage">{{ lifecycleLabel(stage) }}</option>
        </select>
      </label>
      <label>
        <span>部门</span>
        <select v-model="deptFilter">
          <option value="">全部部门</option>
          <option v-for="dept in deptOptions" :key="dept" :value="dept">{{ dept }}</option>
        </select>
      </label>
      <div class="project-toolbar-actions">
        <button type="button" class="secondary-button" @click="resetFilters">重置</button>
      </div>
    </section>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="loading" class="helper-text">正在加载项目目录...</p>
    <p v-else-if="emptyState" class="helper-text">当前账号暂无可访问项目。</p>

    <section v-else class="projects-layout-grid">
      <aside class="panel project-summary-panel">
        <div class="section-title">
          <h3>状态分布</h3>
          <span class="helper-text">{{ filteredProjects.length }} / {{ projects.length }}</span>
        </div>
        <div class="dashboard-state-list">
          <div v-for="[stage, count] in lifecycleSummary" :key="stage">
            <span>{{ lifecycleLabel(stage) }}</span>
            <strong>{{ count }}</strong>
          </div>
        </div>
      </aside>

      <section class="project-card-list">
        <article v-for="project in filteredProjects" :key="project.projectId" class="project-directory-card panel">
          <div class="project-directory-head">
            <div>
              <p class="project-code">{{ project.projectCode }}</p>
              <h3>{{ project.projectName }}</h3>
            </div>
            <span class="tag">{{ lifecycleLabel(project.lifecycleStage) }}</span>
          </div>

          <dl class="project-directory-facts">
            <div>
              <dt>负责人</dt>
              <dd>{{ project.leaderRealName ?? '-' }}</dd>
            </div>
            <div>
              <dt>所属部门</dt>
              <dd>{{ project.deptName ?? '-' }}</dd>
            </div>
            <div>
              <dt>项目类型</dt>
              <dd>{{ project.projectType ?? '-' }}</dd>
            </div>
            <div>
              <dt>项目级别</dt>
              <dd>{{ project.projectLevel ?? '-' }}</dd>
            </div>
          </dl>

          <div class="project-directory-actions">
            <RouterLink class="text-button primary-link" :to="{ name: 'project-authorization', params: { projectId: project.projectId } }">授权与成员</RouterLink>
            <RouterLink class="text-button" :to="{ name: 'workflow' }">查看流程</RouterLink>
          </div>
        </article>

        <p v-if="!filteredProjects.length" class="helper-text">没有符合筛选条件的项目。</p>
      </section>
    </section>
  </AppShell>
</template>
