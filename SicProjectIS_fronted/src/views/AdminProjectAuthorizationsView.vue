<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PermissionWorkbenchLayout from '../layouts/PermissionWorkbenchLayout.vue'
import { ApiError } from '../api/client'
import { getAdminProjectAuthorizations } from '../api/admin'
import {
  assignProjectExperts,
  assignProjectFinances,
  assignProjectProxies,
  changeProjectLeader,
  getProjectAuthorization,
  revokeProjectGrants,
  upsertProjectMembers,
} from '../api/projects'
import { moduleTypeLabel } from '../utils/displayLabels'
import type { AdminProjectAuthorizationIndex } from '../types/admin'
import type {
  BatchProjectGrantRequest,
  BatchProjectMemberRequest,
  ProjectAuthorizationDetail,
  ProjectGrantRecord,
  UserSummary,
} from '../types/project'

const index = ref<AdminProjectAuthorizationIndex | null>(null)
const detail = ref<ProjectAuthorizationDetail | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const selectedProjectId = ref<number | null>(null)
const activeTab = ref<'leader' | 'members' | 'experts' | 'finance' | 'proxy'>('leader')
const projectKeyword = ref('')

const leaderForm = reactive({ userId: null as number | null, reason: '' })
const memberDialogVisible = ref(false)
const grantDialogVisible = ref(false)
const memberSearch = ref('')
const grantSearch = ref('')
const memberDraft = reactive<BatchProjectMemberRequest>({ userIds: [], responsibility: '' })
const grantDraft = reactive<BatchProjectGrantRequest>({
  userIds: [],
  moduleType: 'APPLICATION',
  roundNo: 1,
  taskNodeId: 'expert_review',
  reason: '',
})
const selectedGrantIds = ref<number[]>([])

const users = computed(() => detail.value?.users ?? [])
const expertCandidates = computed(() => users.value.filter((user) => user.roleCodes.includes('EXPERT')))
const financeCandidates = computed(() => users.value.filter((user) => user.roleCodes.includes('FINANCE_ADMIN')))
const filteredMembers = computed(() => {
  const keyword = projectKeyword.value.trim().toLowerCase()
  if (!keyword || !detail.value) {
    return detail.value?.members ?? []
  }
  return detail.value.members.filter((member) =>
    [member.user.realName, member.user.username, member.user.deptName, member.memberRole, member.responsibility]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
})
const filteredExpertGrants = computed(() => filterGrants(detail.value?.expertGrants ?? []))
const filteredFinanceGrants = computed(() => filterGrants(detail.value?.financeGrants ?? []))
const filteredProxyGrants = computed(() => filterGrants(detail.value?.proxyGrants ?? []))
const memberCandidateList = computed(() => {
  const keyword = memberSearch.value.trim().toLowerCase()
  if (!keyword) {
    return users.value
  }
  return users.value.filter((user) =>
    [user.realName, user.username, user.deptName, ...user.roleCodes]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
})
const grantCandidateList = computed(() => {
  const base = currentCandidates()
  const keyword = grantSearch.value.trim().toLowerCase()
  if (!keyword) {
    return base
  }
  return base.filter((user) =>
    [user.realName, user.username, user.deptName, ...user.roleCodes]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
})
watch(selectedProjectId, (projectId) => {
  if (projectId != null) {
    loadDetail(projectId)
  }
})

async function loadIndex() {
  loading.value = true
  try {
    index.value = await getAdminProjectAuthorizations()
    if (!selectedProjectId.value && index.value.projects.length > 0) {
      selectedProjectId.value = index.value.projects[0].projectId
    }
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to load project authorization index')
  } finally {
    loading.value = false
  }
}

async function loadDetail(projectId: number) {
  detailLoading.value = true
  selectedGrantIds.value = []
  try {
    detail.value = await getProjectAuthorization(projectId)
    leaderForm.userId = detail.value.project.leaderUserId
    leaderForm.reason = ''
    resetDialogs()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to load project authorization detail')
  } finally {
    detailLoading.value = false
  }
}

function resetDialogs() {
  memberSearch.value = ''
  grantSearch.value = ''
  memberDraft.userIds = []
  memberDraft.responsibility = ''
  grantDraft.userIds = []
  grantDraft.moduleType = activeTab.value === 'experts' ? 'APPLICATION' : null
  grantDraft.roundNo = activeTab.value === 'experts' ? 1 : null
  grantDraft.taskNodeId = activeTab.value === 'experts' ? 'expert_review' : null
  grantDraft.reason = ''
}

function currentCandidates(): UserSummary[] {
  if (activeTab.value === 'experts') return expertCandidates.value
  if (activeTab.value === 'finance') return financeCandidates.value
  return users.value
}

function canManageTab(tab: typeof activeTab.value) {
  if (!detail.value) return false
  if (tab === 'leader') return detail.value.capabilities.canManageLeader
  if (tab === 'members') return detail.value.capabilities.canManageMembers
  if (tab === 'experts') return detail.value.capabilities.canManageExperts
  if (tab === 'finance') return detail.value.capabilities.canManageFinance
  return detail.value.capabilities.canManageProxy
}

async function submitLeader() {
  if (!selectedProjectId.value || !leaderForm.userId) return
  const leader = users.value.find((user) => user.userId === leaderForm.userId)
  await ElMessageBox.confirm(
    `项目：${detail.value?.project.projectName}\n新负责人：${leader?.realName || leaderForm.userId}\n原因：${leaderForm.reason || '无'}`,
    '确认变更项目负责人',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
  )

  submitting.value = true
  try {
    detail.value = await changeProjectLeader(selectedProjectId.value, {
      userId: leaderForm.userId,
      reason: leaderForm.reason,
    })
    ElMessage.success('项目负责人已更新')
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to update leader')
  } finally {
    submitting.value = false
  }
}

async function submitMembers() {
  if (!selectedProjectId.value || memberDraft.userIds.length === 0) return
  const names = users.value.filter((user) => memberDraft.userIds.includes(user.userId)).map((user) => user.realName)
  await ElMessageBox.confirm(
    `项目：${detail.value?.project.projectName}\n新增成员：${names.join(', ')}\n责任说明：${memberDraft.responsibility || '无'}`,
    '确认提交成员授权',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
  )

  submitting.value = true
  try {
    const response = await upsertProjectMembers(selectedProjectId.value, memberDraft)
    detail.value = response.detail
    memberDialogVisible.value = false
    resetDialogs()
    ElMessage.success(response.summaryMessage)
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to save members')
  } finally {
    submitting.value = false
  }
}

async function submitGrantBatch() {
  if (!selectedProjectId.value || grantDraft.userIds.length === 0) return
  const names = currentCandidates().filter((user) => grantDraft.userIds.includes(user.userId)).map((user) => user.realName)
  await ElMessageBox.confirm(
    [
      `项目：${detail.value?.project.projectName}`,
      `授权类型：${activeTab.value}`,
      `人员：${names.join(', ')}`,
      `${moduleTypeLabel(grantDraft.moduleType) === '-' ? '项目级' : moduleTypeLabel(grantDraft.moduleType)}`,
      `轮次：${grantDraft.roundNo ?? '-'}`,
      `节点：${grantDraft.taskNodeId || '-'}`,
      `原因：${grantDraft.reason || '无'}`,
    ].join('\n'),
    '确认提交项目授权',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' },
  )

  submitting.value = true
  try {
    const response =
      activeTab.value === 'experts'
        ? await assignProjectExperts(selectedProjectId.value, grantDraft)
        : activeTab.value === 'finance'
          ? await assignProjectFinances(selectedProjectId.value, grantDraft)
          : await assignProjectProxies(selectedProjectId.value, grantDraft)
    detail.value = response.detail
    grantDialogVisible.value = false
    resetDialogs()
    ElMessage.success(response.summaryMessage)
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to save project grants')
  } finally {
    submitting.value = false
  }
}

async function revokeSelectedGrants() {
  if (!selectedProjectId.value || selectedGrantIds.value.length === 0) return
  await ElMessageBox.confirm(
    `项目：${detail.value?.project.projectName}\n授权类型：${activeTab.value}\n回收数量：${selectedGrantIds.value.length}`,
    '确认批量回收授权',
    { type: 'warning', confirmButtonText: '确认回收', cancelButtonText: '取消' },
  )

  submitting.value = true
  try {
    const response = await revokeProjectGrants(selectedProjectId.value, {
      grantIds: selectedGrantIds.value,
      reason: `Batch revoke from ${activeTab.value}`,
    })
    detail.value = response.detail
    selectedGrantIds.value = []
    ElMessage.success(response.summaryMessage)
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : 'Failed to revoke project grants')
  } finally {
    submitting.value = false
  }
}

function openMemberDialog() {
  resetDialogs()
  memberDialogVisible.value = true
}

function openGrantDialog() {
  resetDialogs()
  grantDialogVisible.value = true
}

function handleGrantSelection(rows: ProjectGrantRecord[]) {
  selectedGrantIds.value = rows.map((row) => row.projectRoleGrantId)
}

function filterGrants(grants: ProjectGrantRecord[]) {
  const keyword = projectKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return grants
  }
  return grants.filter((grant) =>
    [
      grant.grantee.realName,
      grant.grantee.username,
      grant.grantee.deptName,
      grant.grantRoleCode,
      moduleTypeLabel(grant.moduleType),
      grant.taskNodeId,
      grant.grantReason,
      grant.status,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
}

onMounted(loadIndex)
</script>

<template>
  <PermissionWorkbenchLayout>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">Permission Administration</p>
          <h2 class="section-heading">Project Grants</h2>
        </div>
        <el-button @click="loadIndex">Refresh</el-button>
      </section>

      <el-card shadow="never" class="workbench-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">Project Authorization Workspace</h3>
              <p class="section-subtle">Choose a project, switch tabs, then manage grants through list views and checkbox-based dialogs.</p>
            </div>
          </div>
        </template>

      <el-form :inline="true" class="toolbar-form toolbar-form-plain">
        <el-form-item label="Project">
          <el-select v-model="selectedProjectId" placeholder="Select project" style="width: 340px">
            <el-option
              v-for="project in index?.projects ?? []"
              :key="project.projectId"
              :label="`${project.projectCode} · ${project.projectName}`"
              :value="project.projectId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Search">
          <el-input v-model="projectKeyword" placeholder="Search person, role, module, status" clearable style="width: 320px" />
        </el-form-item>
      </el-form>

      <div class="inline-tag-list">
        <el-tag v-for="item in index?.grantTypeCounts ?? []" :key="item.key" type="info">
          {{ item.label }} · {{ item.count }}
        </el-tag>
      </div>

      <el-skeleton v-if="loading || detailLoading" :rows="10" animated />

      <template v-else-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="Project">{{ detail.project.projectName }}</el-descriptions-item>
          <el-descriptions-item label="Code">{{ detail.project.projectCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Department">{{ detail.project.deptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Leader">{{ detail.leader?.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Type">{{ detail.project.projectType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Stage">{{ detail.project.lifecycleStage || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="inline-tag-list">
          <el-tag :type="detail.capabilities.canManageLeader ? 'success' : 'info'">Leader</el-tag>
          <el-tag :type="detail.capabilities.canManageMembers ? 'success' : 'info'">Members</el-tag>
          <el-tag :type="detail.capabilities.canManageExperts ? 'success' : 'info'">Experts</el-tag>
          <el-tag :type="detail.capabilities.canManageFinance ? 'success' : 'info'">Finance</el-tag>
          <el-tag :type="detail.capabilities.canManageProxy ? 'success' : 'info'">Proxy</el-tag>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="Leader" name="leader">
            <el-form label-width="96px" class="compact-panel">
              <el-form-item label="Leader">
                <el-select v-model="leaderForm.userId" placeholder="Select user" style="width: 320px">
                  <el-option
                    v-for="user in users"
                    :key="user.userId"
                    :label="`${user.realName} (${user.username})`"
                    :value="user.userId"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="Reason">
                <el-input v-model="leaderForm.reason" placeholder="Reason for reassignment" style="width: 420px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :disabled="!canManageTab('leader')" :loading="submitting" @click="submitLeader">
                  Save Leader
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="Members" name="members">
            <div class="table-toolbar">
              <el-button type="primary" :disabled="!canManageTab('members')" @click="openMemberDialog">Add Members</el-button>
            </div>
            <el-table :data="filteredMembers" border stripe>
              <el-table-column prop="user.realName" label="Member" min-width="160">
                <template #default="{ row }">{{ row.user.realName }}</template>
              </el-table-column>
              <el-table-column prop="user.username" label="Username" min-width="140">
                <template #default="{ row }">{{ row.user.username }}</template>
              </el-table-column>
              <el-table-column prop="memberRole" label="Project Role" width="120" />
              <el-table-column prop="responsibility" label="Responsibility" min-width="220">
                <template #default="{ row }">{{ row.responsibility || '-' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="Experts" name="experts">
            <div class="table-toolbar">
              <el-button type="primary" :disabled="!canManageTab('experts')" @click="openGrantDialog">Assign Experts</el-button>
              <el-button danger plain :disabled="!canManageTab('experts') || selectedGrantIds.length === 0" @click="revokeSelectedGrants">
                Revoke Selected
              </el-button>
            </div>
            <el-table :data="filteredExpertGrants" border stripe @selection-change="handleGrantSelection">
              <el-table-column type="selection" width="48" />
              <el-table-column label="Expert" min-width="160">
                <template #default="{ row }">{{ row.grantee.realName }}</template>
              </el-table-column>
              <el-table-column label="Module" min-width="120">
                <template #default="{ row }">{{ moduleTypeLabel(row.moduleType) }}</template>
              </el-table-column>
              <el-table-column label="Round" prop="roundNo" width="90" />
              <el-table-column label="Node" prop="taskNodeId" min-width="140" />
              <el-table-column label="Reason" prop="grantReason" min-width="200" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="Finance" name="finance">
            <div class="table-toolbar">
              <el-button type="primary" :disabled="!canManageTab('finance')" @click="openGrantDialog">Assign Finance</el-button>
              <el-button danger plain :disabled="!canManageTab('finance') || selectedGrantIds.length === 0" @click="revokeSelectedGrants">
                Revoke Selected
              </el-button>
            </div>
            <el-table :data="filteredFinanceGrants" border stripe @selection-change="handleGrantSelection">
              <el-table-column type="selection" width="48" />
              <el-table-column label="User" min-width="160">
                <template #default="{ row }">{{ row.grantee.realName }}</template>
              </el-table-column>
              <el-table-column label="Module" min-width="120">
                <template #default="{ row }">{{ moduleTypeLabel(row.moduleType) }}</template>
              </el-table-column>
              <el-table-column label="Round" prop="roundNo" width="90" />
              <el-table-column label="Node" prop="taskNodeId" min-width="140" />
              <el-table-column label="Reason" prop="grantReason" min-width="200" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="Proxy" name="proxy">
            <div class="table-toolbar">
              <el-button type="primary" :disabled="!canManageTab('proxy')" @click="openGrantDialog">Assign Proxy</el-button>
              <el-button danger plain :disabled="!canManageTab('proxy') || selectedGrantIds.length === 0" @click="revokeSelectedGrants">
                Revoke Selected
              </el-button>
            </div>
            <el-table :data="filteredProxyGrants" border stripe @selection-change="handleGrantSelection">
              <el-table-column type="selection" width="48" />
              <el-table-column label="User" min-width="160">
                <template #default="{ row }">{{ row.grantee.realName }}</template>
              </el-table-column>
              <el-table-column label="Module" min-width="120">
                <template #default="{ row }">{{ moduleTypeLabel(row.moduleType) }}</template>
              </el-table-column>
              <el-table-column label="Round" prop="roundNo" width="90" />
              <el-table-column label="Node" prop="taskNodeId" min-width="140" />
              <el-table-column label="Reason" prop="grantReason" min-width="200" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      </el-card>
    </div>

    <el-dialog v-model="memberDialogVisible" title="Batch Add Members" width="560px">
      <el-form label-width="96px">
        <el-form-item label="Search">
          <el-input v-model="memberSearch" placeholder="Search member by name, department, role" clearable />
        </el-form-item>
        <el-form-item label="Members">
          <el-checkbox-group v-model="memberDraft.userIds" class="checkbox-grid-list">
            <el-checkbox
              v-for="user in memberCandidateList"
              :key="user.userId"
              :label="user.userId"
              border
            >
              {{ user.realName }} - {{ user.deptName || user.username }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="Responsibility">
          <el-input v-model="memberDraft.responsibility" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMembers">Confirm</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantDialogVisible" :title="`Batch Assign ${activeTab}`" width="620px">
      <el-form label-width="96px">
        <el-form-item label="Search">
          <el-input v-model="grantSearch" placeholder="Search candidate by name, department, role" clearable />
        </el-form-item>
        <el-form-item label="Users">
          <el-checkbox-group v-model="grantDraft.userIds" class="checkbox-grid-list">
            <el-checkbox
              v-for="user in grantCandidateList"
              :key="user.userId"
              :label="user.userId"
              border
            >
              {{ user.realName }} - {{ user.deptName || user.username }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="Module">
          <el-input v-model="grantDraft.moduleType" placeholder="Leave blank for project-wide grant" />
        </el-form-item>
        <el-form-item label="Round">
          <el-input-number v-model="grantDraft.roundNo" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="Task Node">
          <el-input v-model="grantDraft.taskNodeId" />
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="grantDraft.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="submitting" @click="submitGrantBatch">Confirm</el-button>
      </template>
    </el-dialog>
  </PermissionWorkbenchLayout>
</template>


