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
