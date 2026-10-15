<script setup lang="ts">
/**
 * 专家专用任务与本人历史界面。提交后等待批次完成，保留个人记录；材料仅在当前 canReview 时加载，刷新时可保留尚未提交草稿。
 */
import { ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppShell from '../layouts/AppShell.vue'
import { listMyReviews, getMyReview, submitExpertScore, type PersonalReview } from '../api/expertReviews'
import { listProjectMaterials, downloadMaterialVersion } from '../api/materials'
import type { MaterialContextView } from '../types/nodeForms'
import { useWorkflowEvents } from '../composables/useWorkflowEvents'

const rows = ref<PersonalReview[]>([])
const selected = ref<PersonalReview | null>(null)
const materials = ref<MaterialContextView[]>([])
const status = ref('')
const page = ref(1)
const hasMore = ref(false)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const draft = reactive({ score: 85, result: 'PASSED', comment: '' })
const labels: Record<string, string> = { PENDING: '待评审', WAITING_REVIEW: '等待评审开始', SUBMITTED: '已提交，等待其他专家', COMPLETED: '评审已结束', EXPIRED: '任务已失效' }
let listAbort: AbortController | null = null
let detailAbort: AbortController | null = null
/**
 * 查询本人任务与历史，取消上一轮列表请求；后台授权变化后重新计算可评分状态。
 */
async function loadList() {
  listAbort?.abort(); listAbort = new AbortController()
  const signal = listAbort.signal; loading.value = true; error.value = ''
  try {
    const response = await listMyReviews(status.value, page.value, 10, signal)
    if (!signal.aborted) { rows.value = response.items; hasMore.value = response.hasMore }
  } catch (err) { if (!signal.aborted) error.value = err instanceof Error ? err.message : '评审任务加载失败' }
  finally { if (!signal.aborted) loading.value = false }
}
/**
 * 查询本人记录；事件触发刷新时 preserveDraft 保留未提交输入，只有当前可评分才读取项目材料。
 */
async function selectReview(id: number, preserveDraft = false) {
  detailAbort?.abort(); detailAbort = new AbortController()
  const signal = detailAbort.signal
  try {
    const review = await getMyReview(id, signal)
    if (signal.aborted) return
    selected.value = review; materials.value = []
    if (!preserveDraft) {
      draft.score = review.totalScore ?? 85; draft.result = review.reviewResult ?? 'PASSED'; draft.comment = review.reviewComment ?? ''
    }
    if (review.canReview) {
      const files = await listProjectMaterials(review.projectId, signal)
      if (!signal.aborted) materials.value = files.filter(file => file.moduleType === review.moduleType && file.isCurrent)
    }
  } catch (err) {
    if (!signal.aborted) ElMessage.error(err instanceof Error ? err.message : '评审记录加载失败')
  }
}
/**
 * 确认后提交本人评分，先切换为已提交并清除材料，再读取个人记录；批次结束不会要求继续访问通用项目详情。
 */
async function submit() {
  const review = selected.value
  if (!review?.canReview || submitting.value) return
  try { await ElMessageBox.confirm('提交后不可修改评分，是否提交本次评审？', '提交评审') } catch { return }
  submitting.value = true
  try {
    await submitExpertScore(review.assignmentId, { valid: true, conflictOfInterest: false,
      reviewResult: draft.result, reviewComment: draft.comment,
      scores: [{ itemCode: 'OVERALL', itemName: '综合评分', scoreValue: draft.score, maxScore: 100, weight: 1 }] })
    materials.value = []; selected.value = { ...review, status: 'SUBMITTED', canReview: false }
    ElMessage.success('评审已提交，可在此查看本人的提交记录')
    await selectReview(review.assignmentId); await loadList()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '评审提交失败')
    await selectReview(review.assignmentId, true)
  } finally { submitting.value = false }
}
async function download(id: number) {
  try { await downloadMaterialVersion(id) } catch (err) { ElMessage.error(err instanceof Error ? err.message : '材料下载失败') }
}
const events = useWorkflowEvents(() => {
  loadList()
  if (selected.value) selectReview(selected.value.assignmentId, true)
})
watch(status, () => { if (page.value !== 1) page.value = 1; else loadList() })
watch(page, loadList)
onMounted(() => { loadList(); events.connect() })
onBeforeUnmount(() => { listAbort?.abort(); detailAbort?.abort() })
</script>

<template>
  <AppShell>
    <section class="page-header">
      <div><h2>我的专家评审</h2><p class="helper-text">查看分配给您的评审任务和本人提交记录。评审结束后，材料访问权限自动收回。</p></div>
      <el-button @click="loadList">刷新</el-button>
    </section>
    <el-card shadow="never">
      <el-select v-model="status" style="width: 230px"><el-option label="全部评审" value="" /><el-option v-for="(label, key) in labels" :key="key" :label="label" :value="key" /></el-select>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-table :data="rows" v-loading="loading" empty-text="暂无分配给您的评审任务">
        <el-table-column prop="projectCode" label="项目编号" min-width="120" />
        <el-table-column prop="projectName" label="项目名称" min-width="170" />
        <el-table-column prop="reviewTitle" label="评审事项" min-width="180" />
        <el-table-column prop="roundNo" label="轮次" width="70" />
        <el-table-column label="状态" min-width="180"><template #default="{ row }">{{ labels[row.status] }}</template></el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" min-width="170" />
        <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" @click="selectReview(row.assignmentId)">{{ row.canReview ? '进入评审' : '查看本人记录' }}</el-button></template></el-table-column>
      </el-table>
      <div class="workflow-pagination"><el-button :disabled="loading || page === 1" @click="page--">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || !hasMore" @click="page++">下一页</el-button></div>
    </el-card>
    <el-card v-if="selected" shadow="never" style="margin-top: 20px">
      <template #header><h3>{{ selected.projectName }} · {{ selected.reviewTitle }}</h3></template>
      <el-alert :title="labels[selected.status] ?? selected.status" :type="selected.canReview ? 'info' : 'success'" :closable="false" />
      <template v-if="selected.canReview">
        <h4>评审材料</h4>
        <p v-if="!materials.length" class="helper-text">当前无可下载的本业务材料。</p>
        <p v-for="file in materials" :key="file.materialVersionId"><el-button link type="primary" @click="download(file.materialVersionId)">{{ file.materialTypeName }} · {{ file.fileName }}</el-button></p>
        <el-form label-position="top">
          <el-form-item label="综合评分（0–100）"><el-input-number v-model="draft.score" :min="0" :max="100" :precision="2" /></el-form-item>
          <el-form-item label="评审结论"><el-select v-model="draft.result"><el-option label="通过" value="PASSED" /><el-option label="推荐" value="RECOMMENDED" /><el-option label="不通过" value="REJECTED" /></el-select></el-form-item>
          <el-form-item label="评审意见"><el-input v-model="draft.comment" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交评审</el-button>
        </el-form>
      </template>
      <el-descriptions v-else :column="1" border style="margin-top: 16px">
        <el-descriptions-item label="本人评分">{{ selected.totalScore ?? '尚未提交' }}</el-descriptions-item>
        <el-descriptions-item label="本人结论">{{ selected.reviewResult ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="本人意见">{{ selected.reviewComment ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ selected.submittedAt ?? '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </AppShell>
</template>
