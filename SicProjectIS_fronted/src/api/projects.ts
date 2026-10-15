/**
 * 封装项目列表、成员和范围授权，以及申请 Saga 入口；批量接口保持同步整体提交，不在客户端拆成逐条写入。
 */
import { apiRequest } from './client'
import type {
  AssignProjectExpertRequest,
  AssignProjectGrantRequest,
  BatchProjectGrantRequest,
  BatchProjectMemberRequest,
  BatchRevokeProjectGrantRequest,
  ChangeProjectLeaderRequest,
  ProjectAuthorizationDetail,
  ProjectAuthorizationMutation,
  ProjectSummary,
  RevokeProjectGrantRequest,
  StartProjectApplicationRequest,
  StartProjectApplicationResponse,
  UpsertProjectMemberRequest,
} from '../types/project'

export function listProjects(signal?: AbortSignal) {
  return apiRequest<ProjectSummary[]>('/projects', { signal })
}

/**
 * 提交历史项目的空部门补充及原因，已有部门的覆盖由后端拒绝；不借此修改负责人身份部门。
 */
export function assignMissingProjectDepartment(projectId: number, deptId: number, reason: string) {
  return apiRequest<{ projectId: number; deptId: number; deptName: string }>(`/projects/${projectId}/department`, {
    method: 'PUT', body: JSON.stringify({ deptId, reason }),
  })
}

export function getProjectAuthorization(projectId: number) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/authorization`)
}

export function changeProjectLeader(projectId: number, request: ChangeProjectLeaderRequest) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/leader`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function upsertProjectMember(projectId: number, request: UpsertProjectMemberRequest) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/members`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function upsertProjectMembers(projectId: number, request: BatchProjectMemberRequest) {
  return apiRequest<ProjectAuthorizationMutation>(`/projects/${projectId}/members/batch`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function removeProjectMember(projectId: number, userId: number) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/members/${userId}`, {
    method: 'DELETE',
  })
}

export function assignProjectExpert(projectId: number, request: AssignProjectExpertRequest) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/expert-grants`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function assignProjectExperts(projectId: number, request: BatchProjectGrantRequest) {
  return apiRequest<ProjectAuthorizationMutation>(`/projects/${projectId}/expert-grants/batch`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function assignProjectFinance(projectId: number, request: AssignProjectGrantRequest) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/finance-grants`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function assignProjectFinances(projectId: number, request: BatchProjectGrantRequest) {
  return apiRequest<ProjectAuthorizationMutation>(`/projects/${projectId}/finance-grants/batch`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function assignProjectProxy(projectId: number, request: AssignProjectGrantRequest) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/proxy-grants`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function assignProjectProxies(projectId: number, request: BatchProjectGrantRequest) {
  return apiRequest<ProjectAuthorizationMutation>(`/projects/${projectId}/proxy-grants/batch`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function revokeProjectGrant(
  projectId: number,
  grantId: number,
  request: RevokeProjectGrantRequest,
) {
  return apiRequest<ProjectAuthorizationDetail>(`/projects/${projectId}/grants/${grantId}/revoke`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function revokeProjectGrants(projectId: number, request: BatchRevokeProjectGrantRequest) {
  return apiRequest<ProjectAuthorizationMutation>(`/projects/${projectId}/grants/revoke-batch`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

/**
 * 通过 Project 的申请 Saga 创建项目与首个模块，不额外调用通用模块创建接口。部门及账号有效性由后端读取当前 IAM 数据。
 */
export function startProjectApplication(request: StartProjectApplicationRequest) {
  return apiRequest<StartProjectApplicationResponse>('/project-applications/start', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
