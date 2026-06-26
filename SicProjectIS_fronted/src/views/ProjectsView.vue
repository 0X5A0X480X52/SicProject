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

const emptyState = computed(() => !loading.value && projects.value.length === 0)

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    projects.value = await listProjects()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'Failed to load projects'
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
    <section class="page-header">
      <div>
        <p class="eyebrow">Project Access</p>
        <h2>Accessible Projects</h2>
      </div>
      <button type="button" class="secondary-button" @click="loadProjects">Refresh</button>
    </section>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="loading" class="helper-text">Loading project catalog...</p>
    <p v-else-if="emptyState" class="helper-text">No projects are currently available for this account.</p>

    <section v-else class="project-list">
      <article v-for="project in projects" :key="project.projectId" class="project-row">
        <div class="project-meta">
          <div>
            <p class="project-code">{{ project.projectCode }}</p>
            <h3>{{ project.projectName }}</h3>
          </div>
          <div class="project-badges">
            <span class="tag muted">{{ project.deptName ?? 'No Department' }}</span>
            <span class="tag">{{ lifecycleLabel(project.lifecycleStage) }}</span>
          </div>
        </div>

        <dl class="project-facts">
          <div>
            <dt>Leader</dt>
            <dd>{{ project.leaderRealName ?? '-' }}</dd>
          </div>
          <div>
            <dt>Type</dt>
            <dd>{{ project.projectType ?? '-' }}</dd>
          </div>
          <div>
            <dt>Level</dt>
            <dd>{{ project.projectLevel ?? '-' }}</dd>
          </div>
        </dl>

        <div class="project-actions">
          <RouterLink
            class="text-button"
            :to="{ name: 'project-authorization', params: { projectId: project.projectId } }"
          >
            Authorization
          </RouterLink>
        </div>
      </article>
    </section>
  </AppShell>
</template>
