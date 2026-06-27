<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { ApiError } from '../../api/client'
import {
  getAdminExpertQualificationApplications,
  reviewExpertQualificationByDept,
  reviewExpertQualificationByScience,
} from '../../api/expertQualification'
import { useAuthStore } from '../../stores/auth'
import type { ExpertQualificationApplication } from '../../types/expertQualification'

const auth = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const applications = ref<ExpertQualificationApplication[]>([])
const statusFilter = ref('')
const reviewDialogVisible = ref(false)
const selected = ref<ExpertQualificationApplication | null>(null)
const reviewStage = ref<'dept' | 'science'>('dept')
const reviewForm = reactive({ approved: true, opinion: '', remark: '' })

const roleCodes = computed(() => auth.user?.roleCodes ?? [])
const permissionCodes = computed(() => auth.user?.permissionCodes ?? [])
const isSystemAdmin = computed(() => roleCodes.value.includes('SYSTEM_ADMIN'))
const isDeptAdmin = computed(() => roleCodes.value.includes('DEPT_ADMIN'))
const isScienceAdmin = computed(() => roleCodes.value.includes('SCIENCE_ADMIN'))
const filteredApplications = computed(() => {
  if (!statusFilter.value) return applications.value
  return applications.value.filter((item) => item.status === statusFilter.value)
})
const pendingCount = computed(() => applications.value.filter((item) => item.status === 'PENDING_DEPT_REVIEW' || item.status === 'PENDING_SCIENCE_REVIEW').length)
const approvedCount = computed(() => applications.value.filter((item) => item.status === 'APPROVED').length)
const rejectedCount = computed(() => applications.value.filter((item) => item.status === 'DEPT_REJECTED' || item.status === 'SCIENCE_REJECTED').length)
const dialogTitle = computed(() => reviewStage.value === 'dept' ? '二级单位审核' : '科技处审核')

function statusLabel(status: string) {
  switch (status) {
    case 'PENDING_DEPT_REVIEW': return '待二级单位审核'
    case 'DEPT_REJECTED': return '二级单位退回'
    case 'PENDING_SCIENCE_REVIEW': return '待科技处审核'
    case 'SCIENCE_REJECTED': return '科技处退回'
    case 'APPROVED': return '已通过'
    default: return status || '-'
  }
}

function statusType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'DEPT_REJECTED' || status === 'SCIENCE_REJECTED') return 'danger'
  return 'warning'
}

function canDeptReview(row: ExpertQualificationApplication) {
  return (isSystemAdmin.value || isDeptAdmin.value || permissionCodes.value.includes('expert:qualification:review:dept')) && row.status === 'PENDING_DEPT_REVIEW'
}

function canScienceReview(row: ExpertQualificationApplication) {
  return (isSystemAdmin.value || isScienceAdmin.value || permissionCodes.value.includes('expert:qualification:review:science')) && row.status === 'PENDING_SCIENCE_REVIEW'
}

async function loadApplications() {
  loading.value = true
  try {
    const response = await getAdminExpertQualificationApplications()
    applications.value = response.applications
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '加载申请列表失败')
  } finally {
    loading.value = false
  }
}

function openReview(row: ExpertQualificationApplication, stage: 'dept' | 'science') {
  selected.value = row
  reviewStage.value = stage
  reviewForm.approved = true
  reviewForm.opinion = ''
  reviewForm.remark = ''
  reviewDialogVisible.value = true
}

function handleDialogClosed() {
  selected.value = null
  reviewForm.approved = true
  reviewForm.opinion = ''
  reviewForm.remark = ''
}

async function submitReview() {
  if (!selected.value) return
  const opinion = reviewForm.opinion.trim()
  const remark = reviewForm.remark.trim()
  if (!opinion) {
    ElMessage.warning('请填写审核意见')
    return
  }
  submitting.value = true
  try {
    const payload = {
      approved: reviewForm.approved,
      opinion,
      remark,
    }
    if (reviewStage.value === 'dept') {
      await reviewExpertQualificationByDept(selected.value.applicationId, payload)
    } else {
      await reviewExpertQualificationByScience(selected.value.applicationId, payload)
      await auth.refreshCurrentUser()
    }
    reviewDialogVisible.value = false
    ElMessage.success('审核操作已完成')
    await loadApplications()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '审核操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadApplications)
</script>

<template>
  <el-card shadow="never" class="workbench-card expert-review-module">
    <template #header>
      <div class="page-header expert-review-module-header">
        <div>
          <h3 class="card-heading">专家资格审核列表</h3>
          <p class="section-subtle">审核申请人的专家资格申请，决定是否授予专家角色与权限</p>
        </div>
        <div class="expert-review-stats">
          <div>
            <span>待审</span>
            <strong>{{ pendingCount }}</strong>
          </div>
          <div>
            <span>已通过</span>
            <strong>{{ approvedCount }}</strong>
          </div>
          <div>
            <span>已退回</span>
            <strong>{{ rejectedCount }}</strong>
          </div>
        </div>
      </div>
    </template>

    <el-form :inline="true" class="toolbar-form toolbar-form-plain">
      <el-form-item label="状态">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 220px">
          <el-option label="待二级单位审核" value="PENDING_DEPT_REVIEW" />
          <el-option label="待科技处审核" value="PENDING_SCIENCE_REVIEW" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="二级单位退回" value="DEPT_REJECTED" />
          <el-option label="科技处退回" value="SCIENCE_REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="loadApplications">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="filteredApplications" v-loading="loading" border stripe class="expert-review-table">
      <el-table-column label="申请人" min-width="160">
        <template #default="{ row }">{{ row.applicant?.realName || row.applicant?.username || '-' }}</template>
      </el-table-column>
      <el-table-column prop="applicantDeptName" label="单位" min-width="180">
        <template #default="{ row }">{{ row.applicantDeptName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="specialty" label="专业领域" min-width="160" />
      <el-table-column prop="professionalTitle" label="职称" min-width="120">
        <template #default="{ row }">{{ row.professionalTitle || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="二级单位审核" min-width="220">
        <template #default="{ row }">
          <div class="expert-review-note">
            <strong>{{ row.deptReviewOpinion || '-' }}</strong>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="科技处审核" min-width="220">
        <template #default="{ row }">
          <div class="expert-review-note">
            <strong>{{ row.scienceReviewOpinion || '-' }}</strong>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="申请时间" min-width="170" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canDeptReview(row)" link type="primary" @click="openReview(row, 'dept')">二级单位审核</el-button>
          <el-button v-if="canScienceReview(row)" link type="primary" @click="openReview(row, 'science')">科技处审核</el-button>
          <span v-if="!canDeptReview(row) && !canScienceReview(row)" class="section-subtle">无可操作</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="reviewDialogVisible" :title="dialogTitle" width="820px" class="expert-review-dialog" @closed="handleDialogClosed">
    <div v-if="selected" class="expert-review-dialog-grid">
      <section class="expert-review-summary-card">
        <p class="expert-review-eyebrow">申请信息</p>
        <strong class="expert-review-name">{{ selected.applicant?.realName || selected.applicant?.username || '-' }}</strong>
        <span class="expert-review-subline">{{ selected.applicantDeptName || '未知单位' }}</span>
        <dl class="expert-review-meta-list">
          <div>
            <dt>专业领域</dt>
            <dd>{{ selected.specialty }}</dd>
          </div>
          <div>
            <dt>职称</dt>
            <dd>{{ selected.professionalTitle || '-' }}</dd>
          </div>
          <div>
            <dt>申请时间</dt>
            <dd>{{ selected.createdAt }}</dd>
          </div>
        </dl>
        <div class="expert-review-reason">
          <span>申请理由</span>
          <p>{{ selected.applicationReason }}</p>
        </div>
      </section>

      <section class="expert-review-form-card">
        <p class="expert-review-eyebrow">审核决定</p>
        <div class="expert-review-decision-grid">
          <button
            type="button"
            class="expert-review-decision-card expert-review-approve"
            :class="{ 'is-active': reviewForm.approved }"
            @click="reviewForm.approved = true"
          >
            <el-icon><CircleCheckFilled /></el-icon>
            <strong>通过</strong>
            <span>同意该申请并授予专家资格</span>
          </button>
          <button
            type="button"
            class="expert-review-decision-card expert-review-reject"
            :class="{ 'is-active': !reviewForm.approved }"
            @click="reviewForm.approved = false"
          >
            <el-icon><CircleCloseFilled /></el-icon>
            <strong>退回</strong>
            <span>退回该申请并要求修改补充</span>
          </button>
        </div>

        <el-form label-width="84px" class="compact-panel expert-review-form">
          <el-form-item label="审核意见" required>
            <el-input v-model="reviewForm.opinion" type="textarea" :rows="4" placeholder="请填写审核意见与理由" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="reviewForm.remark" type="textarea" :rows="3" placeholder="可选：填写内部备注或补充说明" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>

        <el-alert type="info" show-icon :closable="false" title="审核通过后将自动为申请人分配专家角色，请谨慎操作" />

        <div class="expert-review-summary">
          <div>
            <span>审核结果</span>
            <strong>{{ reviewForm.approved ? '通过' : '退回' }}</strong>
          </div>
          <div>
            <span>审核意见</span>
            <strong>{{ reviewForm.opinion.trim() || '未填写' }}</strong>
          </div>
          <div>
            <span>备注</span>
            <strong>{{ reviewForm.remark.trim() || '无' }}</strong>
          </div>
        </div>
      </section>
    </div>

    <template #footer>
      <el-button @click="reviewDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!reviewForm.opinion.trim()" @click="submitReview">确认提交</el-button>
    </template>
  </el-dialog>
</template>
