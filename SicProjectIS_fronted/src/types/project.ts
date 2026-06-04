export interface ProjectSummary {
  projectId: number
  projectCode: string
  projectName: string
  deptId: number | null
  deptName: string | null
  leaderUserId: number | null
  leaderRealName: string | null
  projectType: string | null
  projectLevel: string | null
  lifecycleStage: string | null
}

export interface UserSummary {
  userId: number
  username: string
  realName: string
  deptId: number | null
  deptName: string | null
  roleCodes: string[]
}

export interface ProjectMemberRecord {
  projectMemberId: number
  memberRole: string
  responsibility: string | null
  joinedAt: string | null
  user: UserSummary
}

export interface ProjectGrantRecord {
  projectRoleGrantId: number
  grantRoleCode: string
  grantScope: string | null
  moduleType: string | null
  roundNo: number | null
  taskNodeId: string | null
  status: string
  grantReason: string | null
  effectiveFrom: string | null
  effectiveTo: string | null
  grantee: UserSummary
  grantedBy: UserSummary | null
}

export interface ProjectAuthorizationCapabilities {
  canManageLeader: boolean
  canManageMembers: boolean
  canManageExperts: boolean
  canManageFinance: boolean
  canManageProxy: boolean
}

export interface ProjectAuthorizationDetail {
  project: ProjectSummary
  leader: UserSummary | null
  members: ProjectMemberRecord[]
  expertGrants: ProjectGrantRecord[]
  financeGrants: ProjectGrantRecord[]
  proxyGrants: ProjectGrantRecord[]
  users: UserSummary[]
  capabilities: ProjectAuthorizationCapabilities
}

export interface ChangeDiffSummary {
  added: string[]
  removed: string[]
}

export interface ProjectAuthorizationMutation {
  detail: ProjectAuthorizationDetail
  diff: ChangeDiffSummary
  affectedCount: number
  summaryMessage: string
}

export interface ChangeProjectLeaderRequest {
  userId: number
  reason: string
}

export interface UpsertProjectMemberRequest {
  userId: number
  responsibility: string
}

export interface BatchProjectMemberRequest {
  userIds: number[]
  responsibility: string
}

export interface AssignProjectExpertRequest {
  userId: number
  moduleType: string
  roundNo: number | null
  taskNodeId: string
  reason: string
}

export interface AssignProjectGrantRequest {
  userId: number
  moduleType: string | null
  roundNo: number | null
  taskNodeId: string | null
  reason: string
}

export interface BatchProjectGrantRequest {
  userIds: number[]
  moduleType: string | null
  roundNo: number | null
  taskNodeId: string | null
  reason: string
}

export interface RevokeProjectGrantRequest {
  reason: string
}

export interface BatchRevokeProjectGrantRequest {
  grantIds: number[]
  reason: string
}
