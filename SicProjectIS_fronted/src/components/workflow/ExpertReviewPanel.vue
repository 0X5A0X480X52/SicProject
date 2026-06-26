<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getProjectAuthorization } from "../../api/projects";
import {
  assignExpertToBatch,
  createExpertReviewBatch,
  getModuleBusinessData,
  removeExpertAssignment,
  submitExpertScore,
} from "../../api/expertReviews";
import { useAuthStore } from "../../stores/auth";
import { reviewBatchStatusLabel } from "../../utils/displayLabels";
import type { RuntimeViewResponse } from "../../types/nodeForms";
import type {
  ProjectAuthorizationDetail,
  UserSummary,
} from "../../types/project";

const props = withDefaults(
  defineProps<{ view: RuntimeViewResponse; embedded?: boolean }>(),
  { embedded: false },
);
const emit = defineEmits<{ changed: [] }>();
const auth = useAuthStore();
const loading = ref(false);
const detail = ref<ProjectAuthorizationDetail | null>(null);
const batch = ref<any | null>(null);
const assignments = ref<any[]>([]);
const selectedExpertIds = ref<number[]>([]);
const expertSearch = ref("");
const scoreDraft = reactive({
  assignmentId: "",
  scoreValue: 85,
  reviewResult: "PASSED",
  reviewComment: "",
});

const isExpertNode = computed(
  () => props.view.context.currentCandidateRoleCode === "EXPERT",
);
const isExpertWorkNode = computed(
  () =>
    isExpertNode.value ||
    Boolean(
      props.view.nodeForms?.some((form) => form.dataKind === "EXPERT_REVIEW"),
    ),
);
const isAssignNode = computed(() =>
  String(props.view.context.currentNodeId || "").includes("ExpertAssign"),
);
const isSummaryNode = computed(() =>
  String(props.view.context.currentNodeId || "").includes("ExpertSummary"),
);
const isScienceAdmin = computed(
  () =>
    auth.user?.roleCodes.includes("SCIENCE_ADMIN") ||
    auth.user?.roleCodes.includes("SYSTEM_ADMIN"),
);
const canManageExperts = computed(
  () =>
    isScienceAdmin.value ||
    props.view.context.currentCandidateRoleCode === "DEPT_ADMIN",
);

const activeExpertPanels = ref(['search', 'pending'])

const availableExperts = computed(() => {
  if (!detail.value?.users) return [];
  return detail.value.users.filter(
    (user) =>
      user.roleCodes.includes("EXPERT") && user.userId !== auth.user?.userId,
  );
});
const invitedExpertIds = computed(
  () =>
    new Set(
      assignments.value
        .map((item: any) => item.assignment?.expertUserId)
        .filter(Boolean),
    ),
);
const pendingExpertIds = computed(() => new Set(selectedExpertIds.value));
const searchableExperts = computed(() => {
  const keyword = expertSearch.value.trim().toLowerCase();
  return availableExperts.value
    .filter(
      (user) =>
        !invitedExpertIds.value.has(user.userId) &&
        !pendingExpertIds.value.has(user.userId),
    )
    .filter((user) => {
      if (!keyword) return true;
      return [user.realName, user.username, user.deptName].some((value) =>
        String(value ?? "")
          .toLowerCase()
          .includes(keyword),
      );
    })
    .slice(0, 12);
});
const pendingExperts = computed(() =>
  selectedExpertIds.value
    .map((id) => availableExperts.value.find((user) => user.userId === id))
    .filter((user): user is UserSummary => Boolean(user)),
);
const myAssignments = computed(() =>
  assignments.value.filter(
    (item: any) => item.assignment?.expertUserId === auth.user?.userId,
  ),
);
const unsubmittedMyAssignments = computed(() =>
  myAssignments.value.filter(
    (a: any) => a.assignment.reviewStatus !== "SUBMITTED",
  ),
);
function reviewStatusText(status?: string) {
  switch (status) {
    case 'SUBMITTED': return '已提交'
    case 'PENDING': return '待提交'
    case 'IN_PROGRESS': return '评审中'
    case 'COMPLETED': return '已完成'
    case 'CANCELLED': return '已取消'
    default: return status || '-'
  }
}

const submissionProgress = computed(() => {
  if (!batch.value) return null;
  const submitted = batch.value.submittedExpertCount ?? 0;
  const expected = batch.value.expectedExpertCount ?? 0;
  return { submitted, expected, allDone: submitted >= expected };
});

function applyLatestBatch(businessData: any) {
  const reviews = [...(businessData?.expertReviews ?? [])];
  const latest = reviews
    .reverse()
    .find(
      (item: any) =>
        item.batch?.moduleInstanceId === props.view.context.moduleInstanceId,
    );
  if (!latest) return;
  batch.value = latest.batch;
  assignments.value = latest.assignments ?? [];
}

async function loadExpertData() {
  if (!isExpertWorkNode.value) return;
  loading.value = true;
  try {
    const [authorization, businessData] = await Promise.all([
      getProjectAuthorization(props.view.context.projectId),
      getModuleBusinessData(props.view.context.moduleInstanceId),
    ]);
    detail.value = authorization;
    applyLatestBatch(businessData);
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "专家评审数据加载失败",
    );
  } finally {
    loading.value = false;
  }
}

const isPendingExpert = (userId: string | number) => {
  return pendingExperts.value.some((user) => user.userId === userId);
};

function addPendingExpert(user: UserSummary) {
  if (
    invitedExpertIds.value.has(user.userId) ||
    pendingExpertIds.value.has(user.userId)
  )
    return;
  selectedExpertIds.value = [...selectedExpertIds.value, user.userId];
  expertSearch.value = "";
}

function removePendingExpert(userId: number) {
  selectedExpertIds.value = selectedExpertIds.value.filter(
    (id) => id !== userId,
  );
}

async function ensureBatch() {
  if (batch.value?.batchId) return batch.value;
  const response = await createExpertReviewBatch({
    moduleInstanceId: props.view.context.moduleInstanceId,
    workflowNodeId: props.view.context.currentWorkflowNodeId,
    reviewType: props.view.context.moduleType,
    reviewTitle: props.view.context.currentNodeName || "专家评审",
    ruleType: "AVERAGE",
    minExpertCount: 0,
    passScore: 60,
    recommendScore: 85,
    expectedExpertCount: 0,
  });
  batch.value = response.batch;
  assignments.value = response.assignments;
  return response.batch;
}

async function inviteSelectedExperts() {
  if (!selectedExpertIds.value.length) {
    ElMessage.warning("请先从搜索结果中加入至少一名专家");
    return;
  }
  loading.value = true;
  try {
    const currentBatch = await ensureBatch();
    for (const userId of selectedExpertIds.value) {
      const user = detail.value?.users.find((u) => u.userId === userId);
      const assigned = await assignExpertToBatch(currentBatch.batchId, {
        expertUserId: userId,
        expertName: user?.realName,
        expertOrg: user?.deptName,
      });
      batch.value = assigned.batch;
      assignments.value = assigned.assignments;
    }
    selectedExpertIds.value = [];
    ElMessage.success("专家邀请已更新");
    await loadExpertData();
    emit("changed");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "专家邀请失败");
  } finally {
    loading.value = false;
  }
}

async function removeAssignedExpert(item: any) {
  const assignment = item.assignment;
  if (!assignment?.assignmentId || assignment.reviewStatus === "SUBMITTED")
    return;
  try {
    await ElMessageBox.confirm(
      `确定移除专家 ${assignment.expertName || assignment.expertUserId} 吗？`,
      "移除已邀请专家",
      { type: "warning" },
    );
  } catch {
    return;
  }
  loading.value = true;
  try {
    const response = await removeExpertAssignment(
      Number(assignment.assignmentId),
    );
    batch.value = response.batch;
    assignments.value = response.assignments;
    ElMessage.success("已移除专家邀请");
    emit("changed");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "移除专家失败");
  } finally {
    loading.value = false;
  }
}
async function submitScore() {
  if (!scoreDraft.assignmentId) return;
  const alreadySubmitted = myAssignments.value.find(
    (a: any) => String(a.assignment.assignmentId) === scoreDraft.assignmentId,
  );
  if (alreadySubmitted?.assignment?.reviewStatus === "SUBMITTED") {
    ElMessage.warning("该评分已提交，不可重复提交");
    return;
  }
  loading.value = true;
  try {
    const response = await submitExpertScore(Number(scoreDraft.assignmentId), {
      valid: true,
      conflictOfInterest: false,
      reviewResult: scoreDraft.reviewResult,
      reviewComment: scoreDraft.reviewComment,
      scores: [
        {
          itemCode: "OVERALL",
          itemName: "综合评分",
          scoreValue: scoreDraft.scoreValue,
          weight: 1,
          maxScore: 100,
        },
      ],
    });
    batch.value = response.batch;
    assignments.value = response.assignments;
    ElMessage.success("专家评分已提交");
    emit("changed");
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "专家评分提交失败",
    );
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.view.context.moduleInstanceId, props.view.context.currentNodeId],
  loadExpertData,
  { immediate: true },
);
</script>

<template>
  <component
    :is="embedded ? 'section' : 'el-card'"
    v-if="isExpertWorkNode"
    :class="[
      'workflow-card',
      'workflow-expert-review-panel',
      { 'workflow-expert-review-panel-embedded': embedded },
    ]"
    shadow="never"
  >
    <template v-if="!embedded" #header>专家评审</template>

    <template v-if="canManageExperts && isAssignNode">
      <el-divider content-position="left">创建批次并邀请专家</el-divider>

      <el-collapse
        v-model="activeExpertPanels"
        class="workflow-expert-collapse"
      >
        <!-- 可邀请专家 -->
        <el-collapse-item name="search">
          <template #title>
            <div class="workflow-expert-collapse-title">
              <strong>可邀请专家 </strong> 
              <span>共 {{ searchableExperts.length }} 位</span>
            </div>
          </template>

          <div class="workflow-expert-search-panel">
            <div class="workflow-expert-panel-header">
              <span class="workflow-expert-panel-tip">
                搜索后从表格中加入待邀请名单，双击行也可以加入
              </span>
            </div>

            <el-input
              v-model="expertSearch"
              clearable
              placeholder="搜索专家姓名、账号或单位"
            />

            <el-table
              class="workflow-expert-table"
              :data="searchableExperts"
              row-key="userId"
              border
              stripe
              size="small"
              empty-text="暂无可邀请专家"
              @row-dblclick="addPendingExpert"
            >
              <el-table-column
                prop="realName"
                label="专家姓名"
                min-width="120"
              />

              <el-table-column prop="username" label="账号" min-width="140" />

              <el-table-column label="单位 / 部门" min-width="180">
                <template #default="{ row }">
                  {{ row.deptName || "-" }}
                </template>
              </el-table-column>

              <el-table-column
                label="操作"
                width="100"
                align="center"
                fixed="right"
              >
                <template #default="{ row }">
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    :disabled="isPendingExpert(row.userId)"
                    @click.stop="addPendingExpert(row)"
                  >
                    {{ isPendingExpert(row.userId) ? "已加入" : "加入" }}
                  </el-button>
                </template>
              </el-table-column>

              <template #empty>
                <el-empty description="暂无可邀请专家" :image-size="64" />
              </template>
            </el-table>
          </div>
        </el-collapse-item>

        <!-- 待邀请专家 -->
        <el-collapse-item name="pending">
          <template #title>
            <div class="workflow-expert-collapse-title">
              <strong>待邀请专家 </strong> 
              <span>已选择 {{ pendingExperts.length }} 位</span>
            </div>
          </template>

          <div class="workflow-expert-invite-panel">
            <div class="workflow-expert-list-title">
              <span class="workflow-expert-panel-tip">
                确认待邀请专家后点击邀请
              </span>

              <el-button
                size="small"
                type="primary"
                :loading="loading"
                :disabled="!pendingExperts.length"
                @click="inviteSelectedExperts"
              >
                邀请
              </el-button>
            </div>

            <el-empty
              v-if="!pendingExperts.length"
              description="从上方表格加入专家"
              :image-size="58"
            />

            <div v-else class="workflow-expert-chip-list">
              <el-tag
                v-for="user in pendingExperts"
                :key="user.userId"
                closable
                effect="light"
                @close="removePendingExpert(user.userId)"
              >
                {{ user.realName }} · {{ user.deptName || "-" }}
              </el-tag>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </template>

    <!-- 批次详情 & 进度：分配专家节点不显示，其余节点显示 -->
    <template v-if="batch && !isAssignNode">
      <el-descriptions
        class="node-descriptions"
        :column="1"
        border
        size="small"
      >
        <el-descriptions-item label="批次"
          >#{{ batch.batchId }} {{ batch.reviewTitle }}</el-descriptions-item
        >
        <el-descriptions-item label="状态">{{
          reviewBatchStatusLabel(batch.status)
        }}</el-descriptions-item>
        <el-descriptions-item label="最终结果">{{
          batch.finalResult || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="最终分数">{{
          batch.finalScore ?? "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="提交进度"
          >{{ batch.submittedExpertCount ?? 0 }} /
          {{ batch.expectedExpertCount ?? 0 }}</el-descriptions-item
        >
      </el-descriptions>
      <el-alert
        v-if="submissionProgress"
        :title="`专家提交进度: ${submissionProgress.submitted} / ${submissionProgress.expected}`"
        :type="submissionProgress.allDone ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />

      <!-- 汇总节点：额外显示各专家提交明细表 -->
      <template v-if="isSummaryNode && assignments.length">
        <el-divider content-position="left">专家提交明细</el-divider>
        <el-collapse class="workflow-expert-collapse">
          <el-collapse-item name="submission-detail">
            <template #title>
              <div class="workflow-expert-collapse-title">
                <strong>各专家评审结果</strong>
                <span>{{ assignments.filter((a: any) => a.assignment?.reviewStatus === 'SUBMITTED').length }} / {{ assignments.length }} 已提交</span>
              </div>
            </template>
            <el-table
              :data="assignments"
              row-key="assignment.assignmentId"
              border
              size="small"
              class="workflow-expert-table"
            >
              <el-table-column label="专家" min-width="120">
                <template #default="{ row }">
                  {{ row.assignment?.expertName || row.assignment?.expertUserId || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="单位" min-width="140">
                <template #default="{ row }">
                  {{ row.assignment?.expertOrg || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="提交状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.assignment?.reviewStatus === 'SUBMITTED' ? 'success' : 'warning'"
                    effect="light"
                  >
                    {{ row.assignment?.reviewStatus === 'SUBMITTED' ? '已提交' : '待提交' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="评审结果" width="100" align="center">
                <template #default="{ row }">
                  <el-tag
                    v-if="row.assignment?.reviewStatus === 'SUBMITTED'"
                    size="small"
                    :type="row.scores?.[0]?.scoreValue >= (batch?.passScore ?? 60) ? 'success' : 'danger'"
                    effect="light"
                  >
                    {{ row.scores?.[0]?.scoreValue ?? '-' }} 分
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="评审意见" min-width="200">
                <template #default="{ row }">
                  <span v-if="row.scores?.[0]?.comment">{{ row.scores[0].comment }}</span>
                  <span v-else-if="row.assignment?.reviewStatus === 'SUBMITTED'" class="workflow-expert-empty-cell">无</span>
                  <span v-else class="workflow-expert-empty-cell">-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </template>
    </template>
    <template v-if="canManageExperts && isAssignNode">
      <el-divider content-position="left">已邀请专家</el-divider>
      <el-empty
        v-if="!assignments.length"
        description="尚未邀请专家"
        :image-size="64"
      />
      <div v-else class="workflow-expert-assigned-list">
        <div
          v-for="item in assignments"
          :key="item.assignment.assignmentId"
          class="workflow-expert-assigned-item"
        >
          <div>
            <strong>{{
              item.assignment.expertName || item.assignment.expertUserId
            }}</strong
            ><span
              >{{ item.assignment.expertOrg || "-" }} ·
              {{ reviewStatusText(item.assignment.reviewStatus) }}</span
            >
          </div>
          <el-button
            size="small"
            type="danger"
            plain
            :disabled="item.assignment.reviewStatus === 'SUBMITTED'"
            :loading="loading"
            @click="removeAssignedExpert(item)"
            >移除</el-button
          >
        </div>
      </div>
    </template>

    <template
      v-if="isExpertNode && (myAssignments.length || assignments.length)"
    >
      <el-divider content-position="left">提交专家评分</el-divider>
      <el-form label-position="top">
        <el-form-item label="评分任务">
          <el-select v-model="scoreDraft.assignmentId" style="width: 100%">
            <el-option
              v-for="item in unsubmittedMyAssignments.length
                ? unsubmittedMyAssignments
                : assignments"
              :key="item.assignment.assignmentId"
              :label="`${item.assignment.expertName || item.assignment.expertUserId} · ${reviewStatusText(item.assignment.reviewStatus)}`"
              :value="String(item.assignment.assignmentId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="综合评分">
          <el-input-number
            v-model="scoreDraft.scoreValue"
            :min="0"
            :max="100"
          />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="scoreDraft.reviewResult" style="width: 100%">
            <el-option label="通过" value="PASSED" />
            <el-option label="推荐" value="RECOMMENDED" />
            <el-option label="不通过" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="意见">
          <el-input
            v-model="scoreDraft.reviewComment"
            type="textarea"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <el-button type="primary" plain :loading="loading" @click="submitScore"
        >提交评分</el-button
      >
      <el-alert
        v-if="
          isExpertNode &&
          myAssignments.length &&
          !unsubmittedMyAssignments.length
        "
        title="您已完成评分提交"
        type="success"
        :closable="false"
        show-icon
      />
    </template>
    <el-alert
      v-if="isAssignNode && !assignments.length"
      title="请先搜索并邀请专家，未分配专家不能提交到专家审核节点。"
      type="warning"
      :closable="false"
    />
    <el-alert
      v-if="isSummaryNode && batch && batch.status !== 'COMPLETED'"
      title="评审批次尚未达到最少有效专家数，请等待专家提交评分后再汇总。"
      type="warning"
      :closable="false"
    />
  </component>
</template>
