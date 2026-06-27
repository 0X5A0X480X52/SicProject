<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../layouts/AppShell.vue'
import { ApiError } from '../api/client'
import { getMyExpertQualification, submitExpertQualification } from '../api/expertQualification'
import { useAuthStore } from '../stores/auth'
import ExpertQualificationReviewModule from '../components/expert-qualification/ExpertQualificationReviewModule.vue'
import type { MyExpertQualificationResponse } from '../types/expertQualification'

const auth = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const data = ref<MyExpertQualificationResponse | null>(null)
const form = reactive({
  specialty: '',
  professionalTitle: '',
  applicationReason: '',
})

const applications = computed(() => data.value?.applications ?? [])
const canSubmit = computed(() => !data.value?.expert && !data.value?.hasActiveApplication)
const canReviewExpertQualification = computed(() => {
  const roles = auth.user?.roleCodes ?? []
  const permissions = auth.user?.permissionCodes ?? []
  return roles.includes('SYSTEM_ADMIN') || roles.includes('SCIENCE_ADMIN') || roles.includes('DEPT_ADMIN') || permissions.includes('expert:qualification:review:dept') || permissions.includes('expert:qualification:review:science')
})

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

async function loadData() {
  loading.value = true
  try {
    data.value = await getMyExpertQualification()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '加载申请数据失败')
  } finally {
    loading.value = false
  }
}

async function submitApplication() {
  if (!form.specialty.trim() || !form.applicationReason.trim()) {
    ElMessage.warning('请填写专业领域和申请理由')
    return
  }
  submitting.value = true
  try {
    await submitExpertQualification({
      specialty: form.specialty,
      professionalTitle: form.professionalTitle,
      applicationReason: form.applicationReason,
    })
    form.specialty = ''
    form.professionalTitle = ''
    form.applicationReason = ''
    ElMessage.success('申请已提交')
    await loadData()
  } catch (err) {
    ElMessage.error(err instanceof ApiError ? err.message : '提交申请失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <AppShell>
    <div class="page-stack">
      <section class="page-header">
        <div>
          <p class="shell-eyebrow">专家资格</p>
          <h2 class="section-heading">我的申请</h2>
        </div>
        <el-button @click="loadData">刷新</el-button>
      </section>

      <el-card shadow="never" class="workbench-card">
        <template #header>
          <div class="page-header">
            <div>
              <h3 class="card-heading">申请信息</h3>
              <p class="section-subtle">提交专家资格申请，审核通过后获得专家角色</p>
            </div>
            <el-tag v-if="data?.expert" type="success">已是专家</el-tag>
            <el-tag v-else-if="data?.hasActiveApplication" type="warning">审核中</el-tag>
            <el-tag v-else type="info">未申请</el-tag>
          </div>
        </template>

        <el-skeleton v-if="loading" :rows="8" animated />
        <template v-else>
          <el-alert
            v-if="data?.expert"
            type="success"
            show-icon
            :closable="false"
            title="您已具备专家资格，可以参与项目评审工作"
          />

          <el-form v-if="canSubmit" label-width="96px" class="compact-panel">
            <el-form-item label="专业领域" required>
              <el-input v-model="form.specialty" placeholder="请输入您的专业研究领域" />
            </el-form-item>
            <el-form-item label="职称">
              <el-input v-model="form.professionalTitle" placeholder="例如：教授、研究员" />
            </el-form-item>
            <el-form-item label="申请理由" required>
              <el-input v-model="form.applicationReason" type="textarea" :rows="5" placeholder="请说明您的专业背景与申请理由" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submitApplication">提交申请</el-button>
            </el-form-item>
          </el-form>

          <el-divider content-position="left">申请记录</el-divider>
          <el-table :data="applications" border stripe empty-text="暂无申请记录">
            <el-table-column prop="specialty" label="专业领域" min-width="160" />
            <el-table-column prop="professionalTitle" label="职称" min-width="120">
              <template #default="{ row }">{{ row.professionalTitle || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="二级单位审核" min-width="220">
              <template #default="{ row }">
                <div class="expert-review-note">
                  <strong>{{ row.deptReviewOpinion || '-' }}</strong>
                  <span>备注：{{ row.deptReviewRemark || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="科技处审核" min-width="220">
              <template #default="{ row }">
                <div class="expert-review-note">
                  <strong>{{ row.scienceReviewOpinion || '-' }}</strong>
                  <span>备注：{{ row.scienceReviewRemark || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" min-width="170" />
          </el-table>
        </template>
      </el-card>

      <section v-if="canReviewExpertQualification" class="expert-qualification-review-section">
        <div class="page-header">
          <div>
            <p class="shell-eyebrow">审核</p>
            <h3 class="card-heading">专家资格审核</h3>
            <p class="section-subtle">审核申请人的专家资格，决定是否授予专家角色</p>
          </div>
        </div>
        <ExpertQualificationReviewModule />
      </section>
    </div>
  </AppShell>
</template>
