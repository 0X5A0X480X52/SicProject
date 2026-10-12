/**
 * 封装评审批次、专家指派、评分以及个人历史。个人记录接口与通用批次接口权限不同，评分完成后应读取本人记录。
 */
import { ApiError, apiRequest } from './client'

export interface CreateExpertReviewBatchRequest {
  moduleInstanceId: number
  workflowNodeId?: number | null
  reviewType: string
  reviewTitle: string
  ruleType?: string | null
  minExpertCount?: number | null
  passScore?: number | null
  recommendScore?: number | null
  removeHighestLowest?: boolean | null
  expectedExpertCount?: number | null
}

export interface AssignExpertRequest {
  expertUserId: number
  expertName?: string | null
  expertOrg?: string | null
  expertTitle?: string | null
}

export interface SubmitExpertScoreRequest {
  conflictOfInterest?: boolean
  valid?: boolean
  reviewResult?: string
  reviewComment?: string
  scores: Array<{
    itemCode: string
    itemName: string
    weight?: number
    maxScore?: number
    scoreValue: number
    comment?: string
  }>
}

export interface ExpertReviewBatchDetailResponse {
  batch: any
  assignments: any[]
}

/** 本人任务及历史；SUBMITTED 是个人已提交，COMPLETED 才表示所在评审已结束。canReview 由当前权限单独计算。 */
export interface PersonalReview {
  assignmentId: number; batchId: number; projectId: number; projectCode: string; projectName: string
  moduleInstanceId: number; moduleType: string; roundNo: number; reviewTitle: string
  status: 'PENDING' | 'WAITING_REVIEW' | 'SUBMITTED' | 'COMPLETED' | 'EXPIRED'; canReview: boolean
  totalScore?: number; reviewComment?: string; reviewResult?: string; assignedAt?: string; submittedAt?: string
  scores?: Array<{ scoreItemName: string; scoreValue: number; maxScore: number }>
}
export function listMyReviews(status: string, page: number, pageSize = 10, signal?: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (status) query.set('status', status)
  return apiRequest<{ items: PersonalReview[]; hasMore: boolean }>(`/expert-reviews/my-assignments?${query}`, { signal })
}
/**
 * 读取当前账号自己的 assignment，不依赖整个项目仍可读；调用时传入 assignmentId，而不是 batchId。
 */
export function getMyReview(id: number, signal?: AbortSignal) {
  return apiRequest<PersonalReview>(`/expert-reviews/my-assignments/${id}`, { signal })
}

export function createExpertReviewBatch(request: CreateExpertReviewBatchRequest) {
  return apiRequest<ExpertReviewBatchDetailResponse>('/expert-reviews/batches', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getExpertReviewBatch(batchId: number) {
  return apiRequest<ExpertReviewBatchDetailResponse>(`/expert-reviews/batches/${batchId}`)
}

export function assignExpertToBatch(batchId: number, request: AssignExpertRequest) {
  return apiRequest<ExpertReviewBatchDetailResponse>(`/expert-reviews/batches/${batchId}/assignments`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

/**
 * 提交本人的一次评分；返回视图可能已裁剪为本人记录，不能据它读取其他专家分数或最终汇总。
 */
export function submitExpertScore(assignmentId: number, request: SubmitExpertScoreRequest) {
  return apiRequest<ExpertReviewBatchDetailResponse>(`/expert-reviews/assignments/${assignmentId}/scores`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
/**
 * 优先使用当前 DELETE 路由，仅在 404 时兼容旧 remove 路由；这不是对失败业务写入的通用自动重试。
 */
export async function removeExpertAssignment(assignmentId: number) {
  try {
    return await apiRequest<ExpertReviewBatchDetailResponse>(`/expert-reviews/assignments/${assignmentId}`, {
      method: 'DELETE',
    })
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return apiRequest<ExpertReviewBatchDetailResponse>(`/expert-reviews/assignments/${assignmentId}/remove`, {
        method: 'POST',
      })
    }
    throw error
  }
}

export function getModuleBusinessData(moduleInstanceId: number) {
  return apiRequest<any>(`/module-instances/${moduleInstanceId}/business-data`)
}

