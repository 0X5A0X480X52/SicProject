<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { ApiError } from '../api/client'
import {
  assignProjectExpert,
  assignProjectFinance,
  assignProjectProxy,
  changeProjectLeader,
  getProjectAuthorization,
  removeProjectMember,
  revokeProjectGrant,
  upsertProjectMember,
} from '../api/projects'
import { lifecycleLabel, moduleTypeLabel } from '../utils/displayLabels'
import type { ProjectAuthorizationDetail, ProjectGrantRecord, UserSummary } from '../types/project'

const route = useRoute()
const projectId = computed(() => Number(route.params.projectId))

const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const successMessage = ref('')
const detail = ref<ProjectAuthorizationDetail | null>(null)
const activeTab = ref<'leader' | 'members' | 'experts' | 'finance' | 'proxy'>('leader')

const leaderForm = reactive({ userId: '', reason: '' })
const memberForm = reactive({ userId: '', responsibility: '' })
const expertForm = reactive({ userId: '', moduleType: 'APPLICATION', roundNo: '1', taskNodeId: 'expert_review', reason: '' })
const financeForm = reactive({ userId: '', moduleType: '', roundNo: '', taskNodeId: '', reason: '' })
const proxyForm = reactive({ userId: '', moduleType: '', roundNo: '', taskNodeId: '', reason: '' })

const users = computed(() => detail.value?.users ?? [])
const expertCandidates = computed(() => users.value.filter((user) => user.roleCodes.includes('EXPERT')))
const financeCandidates = computed(() => users.value.filter((user) => user.roleCodes.includes('FINANCE_ADMIN')))

const currentGrants = computed(() => {
  if (!detail.value) return []
  if (activeTab.value === 'experts') return detail.value.expertGrants
  if (activeTab.value === 'finance') return detail.value.financeGrants
  if (activeTab.value === 'proxy') return detail.value.proxyGrants
  return []
})

async function loadDetail() {
  loading.value = true
  error.value = ''
  successMessage.value = ''
  try {
    const nextDetail = await getProjectAuthorization(projectId.value)
    applyDetail(nextDetail)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'Failed to load authorization detail'
  } finally {
    loading.value = false
  }
}

function applyDetail(nextDetail: ProjectAuthorizationDetail) {
  detail.value = nextDetail
  leaderForm.userId = nextDetail.project.leaderUserId ? String(nextDetail.project.leaderUserId) : ''
  memberForm.userId = ''
  memberForm.responsibility = ''
}

async function submitAction(action: () => Promise<ProjectAuthorizationDetail>, message: string) {
  submitting.value = true
  error.value = ''
  successMessage.value = ''
  try {
    applyDetail(await action())
    successMessage.value = message
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : 'Request failed'
  } finally {
    submitting.value = false
  }
}

async function submitLeaderChange() {
  if (!leaderForm.userId) return
  await submitAction(
    () =>
      changeProjectLeader(projectId.value, {
        userId: Number(leaderForm.userId),
        reason: leaderForm.reason,
      }),
    'Project leader updated',
  )
}

async function submitMember() {
  if (!memberForm.userId) return
  await submitAction(
    () =>
      upsertProjectMember(projectId.value, {
        userId: Number(memberForm.userId),
        responsibility: memberForm.responsibility,
      }),
    'Project member saved',
  )
}

async function deleteMember(userId: number) {
  await submitAction(() => removeProjectMember(projectId.value, userId), 'Project member removed')
}

async function submitExpert() {
  if (!expertForm.userId) return
  await submitAction(
    () =>
      assignProjectExpert(projectId.value, {
        userId: Number(expertForm.userId),
        moduleType: expertForm.moduleType,
        roundNo: expertForm.roundNo ? Number(expertForm.roundNo) : null,
        taskNodeId: expertForm.taskNodeId,
        reason: expertForm.reason,
      }),
    'Expert assignment saved',
  )
}

async function submitFinance() {
  if (!financeForm.userId) return
  await submitAction(
    () =>
      assignProjectFinance(projectId.value, {
        userId: Number(financeForm.userId),
        moduleType: financeForm.moduleType || null,
        roundNo: financeForm.roundNo ? Number(financeForm.roundNo) : null,
        taskNodeId: financeForm.taskNodeId || null,
        reason: financeForm.reason,
      }),
    'Finance handler assignment saved',
  )
}

async function submitProxy() {
  if (!proxyForm.userId) return
  await submitAction(
    () =>
      assignProjectProxy(projectId.value, {
        userId: Number(proxyForm.userId),
        moduleType: proxyForm.moduleType || null,
        roundNo: proxyForm.roundNo ? Number(proxyForm.roundNo) : null,
        taskNodeId: proxyForm.taskNodeId || null,
        reason: proxyForm.reason,
      }),
    'Proxy recorder assignment saved',
  )
}

async function removeGrant(grant: ProjectGrantRecord) {
  await submitAction(
    () =>
      revokeProjectGrant(projectId.value, grant.projectRoleGrantId, {
        reason: `Manual revoke for ${grant.grantee.realName}`,
      }),
    'Grant revoked',
  )
}

function canManageTab(tab: typeof activeTab.value) {
  if (!detail.value) return false
  if (tab === 'leader') return detail.value.capabilities.canManageLeader
  if (tab === 'members') return detail.value.capabilities.canManageMembers
  if (tab === 'experts') return detail.value.capabilities.canManageExperts
  if (tab === 'finance') return detail.value.capabilities.canManageFinance
  return detail.value.capabilities.canManageProxy
}

function findUser(userId: string): UserSummary | undefined {
  return users.value.find((user) => user.userId === Number(userId))
}

onMounted(loadDetail)
</script>

<template>
  <AppShell>
    <section class="page-header">
      <div>
        <p class="eyebrow">Project Authorization</p>
        <h2>{{ detail?.project.projectName ?? 'Authorization Workspace' }}</h2>
      </div>
      <button type="button" class="secondary-button" @click="loadDetail">Refresh</button>
    </section>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="successMessage" class="form-success">{{ successMessage }}</p>
    <p v-if="loading" class="helper-text">Loading authorization data...</p>

    <template v-else-if="detail">
      <section class="detail-grid">
        <article class="panel summary-panel">
          <h3>Project Summary</h3>
          <dl class="summary-list">
            <div>
              <dt>Code</dt>
              <dd>{{ detail.project.projectCode }}</dd>
            </div>
            <div>
              <dt>Department</dt>
              <dd>{{ detail.project.deptName ?? '-' }}</dd>
            </div>
            <div>
              <dt>Current Leader</dt>
              <dd>{{ detail.leader?.realName ?? '-' }}</dd>
            </div>
            <div>
              <dt>Stage</dt>
              <dd>{{ lifecycleLabel(detail.project.lifecycleStage) }}</dd>
            </div>
          </dl>
        </article>

        <article class="panel capability-panel">
          <h3>Capabilities</h3>
          <div class="tag-list">
            <span class="tag" :class="{ muted: !detail.capabilities.canManageLeader }">Leader</span>
            <span class="tag" :class="{ muted: !detail.capabilities.canManageMembers }">Members</span>
            <span class="tag" :class="{ muted: !detail.capabilities.canManageExperts }">Experts</span>
            <span class="tag" :class="{ muted: !detail.capabilities.canManageFinance }">Finance</span>
            <span class="tag" :class="{ muted: !detail.capabilities.canManageProxy }">Proxy</span>
          </div>
        </article>
      </section>

      <section class="tabs-bar">
        <button type="button" class="tab-button" :class="{ active: activeTab === 'leader' }" @click="activeTab = 'leader'">Leader</button>
        <button type="button" class="tab-button" :class="{ active: activeTab === 'members' }" @click="activeTab = 'members'">Members</button>
        <button type="button" class="tab-button" :class="{ active: activeTab === 'experts' }" @click="activeTab = 'experts'">Experts</button>
        <button type="button" class="tab-button" :class="{ active: activeTab === 'finance' }" @click="activeTab = 'finance'">Finance</button>
        <button type="button" class="tab-button" :class="{ active: activeTab === 'proxy' }" @click="activeTab = 'proxy'">Proxy</button>
      </section>

      <section v-if="activeTab === 'leader'" class="panel">
        <form class="stack-form" @submit.prevent="submitLeaderChange">
          <label>
            New Leader
            <select v-model="leaderForm.userId" :disabled="!canManageTab('leader') || submitting">
              <option value="">Choose a user</option>
              <option v-for="user in users" :key="user.userId" :value="String(user.userId)">
                {{ user.realName }} ({{ user.username }})
              </option>
            </select>
          </label>
          <label>
            Reason
            <input v-model="leaderForm.reason" :disabled="!canManageTab('leader') || submitting" />
          </label>
          <button type="submit" :disabled="!canManageTab('leader') || submitting">Save Leader</button>
        </form>
      </section>

      <section v-else-if="activeTab === 'members'" class="panel">
        <div class="record-list">
          <article v-for="member in detail.members" :key="member.projectMemberId" class="record-row">
            <div>
              <strong>{{ member.user.realName }}</strong>
              <p>{{ member.memberRole }} · {{ member.responsibility || 'No responsibility note' }}</p>
            </div>
            <button
              v-if="canManageTab('members') && member.memberRole !== 'LEADER'"
              type="button"
              class="danger-button"
              :disabled="submitting"
              @click="deleteMember(member.user.userId)"
            >
              Remove
            </button>
          </article>
        </div>

        <form class="stack-form compact-form" @submit.prevent="submitMember">
          <label>
            Member
            <select v-model="memberForm.userId" :disabled="!canManageTab('members') || submitting">
              <option value="">Choose a user</option>
              <option v-for="user in users" :key="user.userId" :value="String(user.userId)">
                {{ user.realName }} ({{ user.username }})
              </option>
            </select>
          </label>
          <label>
            Responsibility
            <input
              v-model="memberForm.responsibility"
              :placeholder="findUser(memberForm.userId)?.realName ? `Notes for ${findUser(memberForm.userId)?.realName}` : 'Responsibility notes'"
              :disabled="!canManageTab('members') || submitting"
            />
          </label>
          <button type="submit" :disabled="!canManageTab('members') || submitting">Save Member</button>
        </form>
      </section>

      <section v-else class="panel">
        <div class="record-list">
          <article v-for="grant in currentGrants" :key="grant.projectRoleGrantId" class="record-row">
            <div>
              <strong>{{ grant.grantee.realName }}</strong>
              <p>{{ moduleTypeLabel(grant.moduleType) === '-' ? '项目级' : moduleTypeLabel(grant.moduleType) }} · Round {{ grant.roundNo ?? '-' }} · {{ grant.taskNodeId || 'No node' }}</p>
              <p>{{ grant.grantReason || 'No reason provided' }}</p>
            </div>
            <button
              v-if="canManageTab(activeTab)"
              type="button"
              class="danger-button"
              :disabled="submitting"
              @click="removeGrant(grant)"
            >
              Revoke
            </button>
          </article>
        </div>

        <form v-if="activeTab === 'experts'" class="expert-form" @submit.prevent="submitExpert">
          <label>
            Expert
            <select v-model="expertForm.userId" :disabled="!canManageTab('experts') || submitting">
              <option value="">Choose an expert</option>
              <option v-for="user in expertCandidates" :key="user.userId" :value="String(user.userId)">
                {{ user.realName }} ({{ user.username }})
              </option>
            </select>
          </label>
          <label>
            Module
            <input v-model="expertForm.moduleType" :disabled="!canManageTab('experts') || submitting" />
          </label>
          <label>
            Round
            <input v-model="expertForm.roundNo" type="number" min="1" :disabled="!canManageTab('experts') || submitting" />
          </label>
          <label>
            Node
            <input v-model="expertForm.taskNodeId" :disabled="!canManageTab('experts') || submitting" />
          </label>
          <label class="full-width">
            Reason
            <input v-model="expertForm.reason" :disabled="!canManageTab('experts') || submitting" />
          </label>
          <button type="submit" :disabled="!canManageTab('experts') || submitting">Assign Expert</button>
        </form>

        <form v-else-if="activeTab === 'finance'" class="expert-form" @submit.prevent="submitFinance">
          <label>
            Finance
            <select v-model="financeForm.userId" :disabled="!canManageTab('finance') || submitting">
              <option value="">Choose a finance user</option>
              <option v-for="user in financeCandidates" :key="user.userId" :value="String(user.userId)">
                {{ user.realName }} ({{ user.username }})
              </option>
            </select>
          </label>
          <label>
            Module
            <input v-model="financeForm.moduleType" :disabled="!canManageTab('finance') || submitting" />
          </label>
          <label>
            Round
            <input v-model="financeForm.roundNo" type="number" min="1" :disabled="!canManageTab('finance') || submitting" />
          </label>
          <label>
            Node
            <input v-model="financeForm.taskNodeId" :disabled="!canManageTab('finance') || submitting" />
          </label>
          <label class="full-width">
            Reason
            <input v-model="financeForm.reason" :disabled="!canManageTab('finance') || submitting" />
          </label>
          <button type="submit" :disabled="!canManageTab('finance') || submitting">Assign Finance</button>
        </form>

        <form v-else class="expert-form" @submit.prevent="submitProxy">
          <label>
            Proxy
            <select v-model="proxyForm.userId" :disabled="!canManageTab('proxy') || submitting">
              <option value="">Choose a user</option>
              <option v-for="user in users" :key="user.userId" :value="String(user.userId)">
                {{ user.realName }} ({{ user.username }})
              </option>
            </select>
          </label>
          <label>
            Module
            <input v-model="proxyForm.moduleType" :disabled="!canManageTab('proxy') || submitting" />
          </label>
          <label>
            Round
            <input v-model="proxyForm.roundNo" type="number" min="1" :disabled="!canManageTab('proxy') || submitting" />
          </label>
          <label>
            Node
            <input v-model="proxyForm.taskNodeId" :disabled="!canManageTab('proxy') || submitting" />
          </label>
          <label class="full-width">
            Reason
            <input v-model="proxyForm.reason" :disabled="!canManageTab('proxy') || submitting" />
          </label>
          <button type="submit" :disabled="!canManageTab('proxy') || submitting">Assign Proxy</button>
        </form>
      </section>
    </template>
  </AppShell>
</template>

