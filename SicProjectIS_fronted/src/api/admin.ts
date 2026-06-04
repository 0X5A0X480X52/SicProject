import { apiRequest } from './client'
import type {
  AdminOverview,
  AdminProjectAuthorizationIndex,
  AdminUserDetail,
  AdminUserQuery,
  AdminUserQueryResponse,
  AuditLogQueryResponse,
  PermissionDefinition,
  RolePermissionMatrix,
  RolePermissionUpdateResponse,
  UpdateRolePermissionsRequest,
  UpdateUserRolesRequest,
  UpdateUserStatusRequest,
  UserRoleUpdateResponse,
} from '../types/admin'

function toQueryString(query: AdminUserQuery) {
  const params = new URLSearchParams()
  if (query.username) params.set('username', query.username)
  if (query.realName) params.set('realName', query.realName)
  if (query.deptId != null) params.set('deptId', String(query.deptId))
  if (query.enabled != null) params.set('enabled', String(query.enabled))
  if (query.roleCode) params.set('roleCode', query.roleCode)
  const text = params.toString()
  return text ? `?${text}` : ''
}

export function getAdminOverview() {
  return apiRequest<AdminOverview>('/admin/overview')
}

export function getAdminUsers(query: AdminUserQuery = {}) {
  return apiRequest<AdminUserQueryResponse>(`/admin/users${toQueryString(query)}`)
}

export function getAdminUserDetail(userId: number) {
  return apiRequest<AdminUserDetail>(`/admin/users/${userId}`)
}

export function updateUserRoles(userId: number, request: UpdateUserRolesRequest) {
  return apiRequest<UserRoleUpdateResponse>(`/admin/users/${userId}/roles`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function updateUserStatus(userId: number, request: UpdateUserStatusRequest) {
  return apiRequest<AdminUserDetail>(`/admin/users/${userId}/status`, {
    method: 'PATCH',
    body: JSON.stringify(request),
  })
}

export function getRolePermissionMatrix() {
  return apiRequest<RolePermissionMatrix>('/admin/roles/permissions')
}

export function getPermissions() {
  return apiRequest<PermissionDefinition[]>('/admin/permissions')
}

export function updateRolePermissions(roleCode: string, request: UpdateRolePermissionsRequest) {
  return apiRequest<RolePermissionUpdateResponse>(`/admin/roles/${roleCode}/permissions`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export interface AuditLogQueryParams {
  keyword?: string
  actionType?: string
  scopeType?: string
  projectId?: number | null
  operatorUserId?: number | null
  dateFrom?: string
  dateTo?: string
}

export function getAuditLogs(query: AuditLogQueryParams = {}) {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.actionType) params.set('actionType', query.actionType)
  if (query.scopeType) params.set('scopeType', query.scopeType)
  if (query.projectId != null) params.set('projectId', String(query.projectId))
  if (query.operatorUserId != null) params.set('operatorUserId', String(query.operatorUserId))
  if (query.dateFrom) params.set('dateFrom', query.dateFrom)
  if (query.dateTo) params.set('dateTo', query.dateTo)
  const text = params.toString()
  return apiRequest<AuditLogQueryResponse>(`/admin/audit-logs${text ? `?${text}` : ''}`)
}

export function getAdminProjectAuthorizations() {
  return apiRequest<AdminProjectAuthorizationIndex>('/admin/projects/authorizations')
}
