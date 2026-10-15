/**
 * 封装专家资格申请、本人记录和部门/科研审批；取得专家角色依靠审批结果，提交申请本身不授予资格。
 */
import { apiRequest } from './client'
import type {
  ExpertQualificationApplication,
  ExpertQualificationApplicationQueryResponse,
  MyExpertQualificationResponse,
  ReviewExpertQualificationRequest,
  SubmitExpertQualificationRequest,
} from '../types/expertQualification'

export function submitExpertQualification(request: SubmitExpertQualificationRequest) {
  return apiRequest<ExpertQualificationApplication>('/expert-qualification/applications', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getMyExpertQualification() {
  return apiRequest<MyExpertQualificationResponse>('/expert-qualification/my')
}

export function getAdminExpertQualificationApplications() {
  return apiRequest<ExpertQualificationApplicationQueryResponse>('/admin/expert-qualification/applications')
}

export function reviewExpertQualificationByDept(applicationId: number, request: ReviewExpertQualificationRequest) {
  return apiRequest<ExpertQualificationApplication>(`/admin/expert-qualification/applications/${applicationId}/dept-review`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function reviewExpertQualificationByScience(applicationId: number, request: ReviewExpertQualificationRequest) {
  return apiRequest<ExpertQualificationApplication>(`/admin/expert-qualification/applications/${applicationId}/science-review`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
