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

export function submitExpertScore(assignmentId: number, request: SubmitExpertScoreRequest) {
  return apiRequest<ExpertReviewBatchDetailResponse>(`/expert-reviews/assignments/${assignmentId}/scores`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
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

