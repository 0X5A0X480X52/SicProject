<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectAuthorization } from '../../api/projects'
import { assignExpertToBatch, createExpertReviewBatch, submitExpertScore } from '../../api/expertReviews'
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
const isScienceAdmin = computed(() => auth.user?.roleCodes.includes('SCIENCE_ADMIN') || auth.user?.roleCodes.includes('SYSTEM_ADMIN'))
const expertGrants = computed(() => (detail.value?.expertGrants ?? []).filter((grant) =>
  grant.moduleType === props.view.context.moduleType &&
  (!grant.roundNo || grant.roundNo === props.view.context.currentRoundNo) &&
  (!grant.taskNodeId || grant.taskNodeId === props.view.context.currentNodeId),
))
const myAssignments = computed(() => assignments.value.filter((item: any) => item.assignment?.expertUserId === auth.user?.userId))

async function loadAuthorization() {
  if (!isExpertNode.value) return
  loading.value = true
  try {
    detail.value = await getProjectAuthorization(props.view.context.projectId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '专家授权加载失败')
  } finally {
    loading.value = false
  }
}

async function createBatch() {
  loading.value = true
  try {
    const response = await createExpertReviewBatch({
      moduleInstanceId: props.view.context.moduleInstanceId,
      workflowNodeId: props.view.context.currentWorkflowNodeId,
      reviewType: props.view.context.moduleType,
      reviewTitle: props.view.context.currentNodeName || '专家评审',
      ruleType: 'AVERAGE',
      minExpertCount: Math.min(3, Math.max(1, selectedExpertIds.value.length || 1)),
      passScore: 60,
      recommendScore: 85,
      expectedExpertCount: selectedExpertIds.value.length || undefined,
    })
    batch.value = response.batch
    assignments.value = response.assignments
    for (const userId of selectedExpertIds.value) {
      const grant = expertGrants.value.find((item) => item.grantee.userId === userId)
      const assigned = await assignExpertToBatch(response.batch.batchId, {
        expertUserId: userId,
        expertName: grant?.grantee.realName,
        expertOrg: grant?.grantee.deptName,
      })
      batch.value = assigned.batch
      assignments.value = assigned.assignments
    }
    ElMessage.success('专家评审批次已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '专家评审批次创建失败')
  } finally {
    loading.value = false
  }
}

async function submitScore() {
  if (!scoreDraft.assignmentId) return
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

watch(() => props.view.context.moduleInstanceId, loadAuthorization, { immediate: true })
</script>

<template>
  <el-card v-if="isExpertNode" class="workflow-card" shadow="never">
    <template #header>专家评审</template>
    <el-alert
      title="专家授权决定谁能办理当前专家节点；评审批次决定本轮实际评分与汇总结果。"
      type="info"
      show-icon
      :closable="false"
    />

    <template v-if="isScienceAdmin">
      <el-divider content-position="left">创建批次并邀请专家</el-divider>
      <el-select v-model="selectedExpertIds" multiple filterable placeholder="选择已授权专家" style="width: 100%">
        <el-option
          v-for="grant in expertGrants"
          :key="grant.projectRoleGrantId"
          :label="`${grant.grantee.realName} (${grant.grantee.username})`"
          :value="grant.grantee.userId"
        />
      </el-select>
      <el-button class="workflow-panel-action" type="primary" :loading="loading" @click="createBatch">创建评审批次</el-button>
    </template>

    <el-descriptions v-if="batch" class="node-descriptions" :column="1" border size="small">
      <el-descriptions-item label="批次">#{{ batch.batchId }} {{ batch.reviewTitle }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ batch.status }}</el-descriptions-item>
      <el-descriptions-item label="最终结果">{{ batch.finalResult || '-' }}</el-descriptions-item>
      <el-descriptions-item label="最终分数">{{ batch.finalScore ?? '-' }}</el-descriptions-item>
    </el-descriptions>

    <template v-if="myAssignments.length || assignments.length">
      <el-divider content-position="left">提交专家评分</el-divider>
      <el-form label-position="top">
        <el-form-item label="评分任务">
          <el-select v-model="scoreDraft.assignmentId" style="width: 100%">
            <el-option
              v-for="item in (myAssignments.length ? myAssignments : assignments)"
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
    </template>
  </el-card>
</template>
