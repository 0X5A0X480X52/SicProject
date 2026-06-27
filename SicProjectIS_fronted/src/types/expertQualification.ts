import type { UserSummary } from './project'

export interface ExpertQualificationApplication {
  applicationId: number
  applicant: UserSummary | null
  applicantDeptId: number | null
  applicantDeptName: string | null
  specialty: string
  professionalTitle: string | null
  applicationReason: string
  status: string
  deptReviewer: UserSummary | null
  deptReviewOpinion: string | null
  deptReviewRemark: string | null
  deptReviewedAt: string | null
  scienceReviewer: UserSummary | null
  scienceReviewOpinion: string | null
  scienceReviewRemark: string | null
  scienceReviewedAt: string | null
  createdAt: string
  updatedAt: string | null
}

export interface SubmitExpertQualificationRequest {
  specialty: string
  professionalTitle: string
  applicationReason: string
}

export interface ReviewExpertQualificationRequest {
  approved: boolean
  opinion: string
  remark: string
}

export interface MyExpertQualificationResponse {
  expert: boolean
  hasActiveApplication: boolean
  applications: ExpertQualificationApplication[]
}

export interface ExpertQualificationApplicationQueryResponse {
  applications: ExpertQualificationApplication[]
}
