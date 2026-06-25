import { apiRequest } from './client'

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

export function getModuleBusinessData(moduleInstanceId: number) {
  return apiRequest<any>(`/module-instances/${moduleInstanceId}/business-data`)
}
