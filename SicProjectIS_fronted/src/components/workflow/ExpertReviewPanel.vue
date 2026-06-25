<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectAuthorization } from '../../api/projects'
import { assignExpertToBatch, createExpertReviewBatch, getModuleBusinessData, submitExpertScore } from '../../api/expertReviews'
import { useAuthStore } from '../../stores/auth'
import type { RuntimeViewResponse } from '../../types/nodeForms'
import type { ProjectAuthorizationDetail } from '../../types/project'

const props = defineProps<{ view: RuntimeViewResponse }>()
const auth = useAuthStore()
const loading = ref(false)
const detail = ref<ProjectAuthorizationDetail | null>(null)
const batch = ref<any | null>(null)
const assignments = ref<any[]>([])
const selectedExpertIds = ref<number[]>([])
const scoreDraft = reactive({ assignmentId: '', scoreValue: 85, reviewResult: 'PASSED', reviewComment: '' })

const isExpertNode = computed(() => props.view.context.currentCandidateRoleCode === 'EXPERT')
const isExpertWorkNode = computed(() =>
  isExpertNode.value || Boolean(props.view.nodeForms?.some((form) => form.dataKind === 'EXPERT_REVIEW')),
)
const isAssignNode = computed(() => String(props.view.context.currentNodeId || '').includes('ExpertAssign'))
const isSummaryNode = computed(() => String(props.view.context.currentNodeId || '').includes('ExpertSummary'))
const isScienceAdmin = computed(() => auth.user?.roleCodes.includes('SCIENCE_ADMIN') || auth.user?.roleCodes.includes('SYSTEM_ADMIN'))
const canManageExperts = computed(() => isScienceAdmin.value || props.view.context.currentCandidateRoleCode === 'DEPT_ADMIN')
const targetReviewNodeId = computed(() => {
  const nodeId = props.view.context.currentNodeId
  if (nodeId === 'DeptExpertAssignTask' || nodeId === 'DeptExpertSummaryTask') return 'DeptExpertReviewTask'
  if (nodeId === 'ScienceExpertAssignTask' || nodeId === 'ScienceExpertSummaryTask') return 'ScienceExpertReviewTask'
  if (nodeId === 'ExpertAssignTask' || nodeId === 'ExpertSummaryTask') return 'ExpertReviewTask'
  return nodeId || null
})
const expertGrants = computed(() => (detail.value?.expertGrants ?? []).filter((grant) =>
  grant.moduleType === props.view.context.moduleType &&
  (!grant.roundNo || grant.roundNo === props.view.context.currentRoundNo) &&
  (!grant.taskNodeId || grant.taskNodeId === targetReviewNodeId.value || grant.taskNodeId === 'expert_review'),
))
const availableExperts = computed(() => {
  if (!detail.value?.users) return []
  return detail.value.users.filter(
    (user) => user.roleCodes.includes('EXPERT') && user.userId !== auth.user?.userId,
  )
})
const myAssignments = computed(() => assignments.value.filter((item: any) => item.assignment?.expertUserId === auth.user?.userId))
const unsubmittedMyAssignments = computed(() =>
  myAssignments.value.filter((a: any) => a.assignment.reviewStatus !== 'SUBMITTED'),
)
const submissionProgress = computed(() => {
  if (!batch.value) return null
  const submitted = batch.value.submittedExpertCount ?? 0
  const expected = batch.value.expectedExpertCount ?? 0
  return { submitted, expected, allDone: submitted >= expected }
})

function applyLatestBatch(businessData: any) {
  const reviews = [...(businessData?.expertReviews ?? [])]
  const latest = reviews.reverse().find((item: any) => item.batch?.moduleInstanceId === props.view.context.moduleInstanceId)
  if (!latest) return
  batch.value = latest.batch
  assignments.value = latest.assignments ?? []
}

async function loadExpertData() {
  if (!isExpertWorkNode.value) return
  loading.value = true
  try {
    const [authorization, businessData] = await Promise.all([
      getProjectAuthorization(props.view.context.projectId),
      getModuleBusinessData(props.view.context.moduleInstanceId),
    ])
    detail.value = authorization
    applyLatestBatch(businessData)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '专家评审数据加载失败')
  } finally {
    loading.value = false
  }
}

async function createBatch() {
  if (!selectedExpertIds.value.length) {
    ElMessage.warning('请先选择至少一名专家')
    return
  }
  loading.value = true
  try {
    const response = await createExpertReviewBatch({
      moduleInstanceId: props.view.context.moduleInstanceId,
      workflowNodeId: props.view.context.currentWorkflowNodeId,
      reviewType: props.view.context.moduleType,
      reviewTitle: props.view.context.currentNodeName || '专家评审',
      ruleType: 'AVERAGE',
      minExpertCount: selectedExpertIds.value.length,
      passScore: 60,
      recommendScore: 85,
      expectedExpertCount: selectedExpertIds.value.length,
    })
    batch.value = response.batch
    assignments.value = response.assignments
    for (const userId of selectedExpertIds.value) {
      const user = detail.value?.users.find((u) => u.userId === userId)
      const assigned = await assignExpertToBatch(response.batch.batchId, {
        expertUserId: userId,
        expertName: user?.realName,
        expertOrg: user?.deptName,
      })
      batch.value = assigned.batch
      assignments.value = assigned.assignments
    }
    ElMessage.success('专家评审批次已创建')
    await loadExpertData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '专家评审批次创建失败')
  } finally {
    loading.value = false
  }
}

async function submitScore() {
  if (!scoreDraft.assignmentId) return
  const alreadySubmitted = myAssignments.value.find((a: any) => String(a.assignment.assignmentId) === scoreDraft.assignmentId)
  if (alreadySubmitted?.assignment?.reviewStatus === 'SUBMITTED') {
    ElMessage.warning('该评分已提交，不可重复提交')
    return
  }
  loading.value = true
  try {
    const response = await submitExpertScore(Number(scoreDraft.assignmentId), {
      valid: true,
      conflictOfInterest: false,
      reviewResult: scoreDraft.reviewResult,
      reviewComment: scoreDraft.reviewComment,
      scores: [{ itemCode: 'OVERALL', itemName: '综合评分', scoreValue: scoreDraft.scoreValue, weight: 1, maxScore: 100 }],
    })
    batch.value = response.batch
    assignments.value = response.assignments
    ElMessage.success('专家评分已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '专家评分提交失败')
  } finally {
    loading.value = false
  }
}

watch(() => [props.view.context.moduleInstanceId, props.view.context.currentNodeId], loadExpertData, { immediate: true })
</script>

<template>
  <el-card v-if="isExpertWorkNode" class="workflow-card" shadow="never">
    <template #header>专家评审</template>
    <el-alert
      title="专家授权决定谁能办理当前专家节点；评审批次决定本轮实际评分与汇总结果。"
      type="info"
      show-icon
      :closable="false"
    />

    <template v-if="canManageExperts && isAssignNode">
      <el-divider content-position="left">创建批次并邀请专家</el-divider>
      <el-select v-model="selectedExpertIds" multiple filterable placeholder="选择专家（需具备EXPERT角色）" style="width: 100%">
        <el-option
          v-for="user in availableExperts"
          :key="user.userId"
          :label="`${user.realName} (${user.username})`"
          :value="user.userId"
        />
      </el-select>
      <el-button class="workflow-panel-action" type="primary" :loading="loading" :disabled="!selectedExpertIds.length" @click="createBatch">创建评审批次</el-button>
    </template>

    <el-descriptions v-if="batch" class="node-descriptions" :column="1" border size="small">
      <el-descriptions-item label="批次">#{{ batch.batchId }} {{ batch.reviewTitle }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ batch.status }}</el-descriptions-item>
      <el-descriptions-item label="最终结果">{{ batch.finalResult || '-' }}</el-descriptions-item>
      <el-descriptions-item label="最终分数">{{ batch.finalScore ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="提交进度">{{ batch.submittedExpertCount ?? 0 }} / {{ batch.expectedExpertCount ?? 0 }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="submissionProgress" :title="`专家提交进度: ${submissionProgress.submitted} / ${submissionProgress.expected}`" :type="submissionProgress.allDone ? 'success' : 'warning'" :closable="false" show-icon />

    <template v-if="isExpertNode && (myAssignments.length || assignments.length)">
      <el-divider content-position="left">提交专家评分</el-divider>
      <el-form label-position="top">
        <el-form-item label="评分任务">
          <el-select v-model="scoreDraft.assignmentId" style="width: 100%">
            <el-option
              v-for="item in (unsubmittedMyAssignments.length ? unsubmittedMyAssignments : assignments)"
              :key="item.assignment.assignmentId"
              :label="`${item.assignment.expertName || item.assignment.expertUserId} · ${item.assignment.reviewStatus}`"
              :value="String(item.assignment.assignmentId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="综合评分">
          <el-input-number v-model="scoreDraft.scoreValue" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="scoreDraft.reviewResult" style="width: 100%">
            <el-option label="通过" value="PASSED" />
            <el-option label="推荐" value="RECOMMENDED" />
            <el-option label="不通过" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="意见">
          <el-input v-model="scoreDraft.reviewComment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <el-button type="primary" plain :loading="loading" @click="submitScore">提交评分</el-button>
      <el-alert v-if="isExpertNode && myAssignments.length && !unsubmittedMyAssignments.length" title="您已完成评分提交" type="success" :closable="false" show-icon />
    </template>
    <el-alert v-if="isAssignNode && !assignments.length" title="请先选择专家并创建评审批次，未分配专家不能提交到专家审核节点。" type="warning" :closable="false" />
    <el-alert v-if="isSummaryNode && batch && batch.status !== 'COMPLETED'" title="评审批次尚未达到最少有效专家数，请等待专家提交评分后再汇总。" type="warning" :closable="false" />
  </el-card>
</template>
