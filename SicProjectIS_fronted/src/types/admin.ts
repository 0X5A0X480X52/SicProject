import type { ProjectSummary } from './project'

export interface AdminRoleOption {
  roleId: number
  roleCode: string
  roleName: string
  roleDesc: string | null
  enabled: boolean
}

export interface AdminCountItem {
  key: string
  label: string
  count: number
}

export interface AdminOverview {
  totalUsers: number
  enabledUsers: number
  totalRoles: number
  totalPermissions: number
  activeProjectGrants: number
  auditLogCount: number
  grantTypeCounts: AdminCountItem[]
  roleCounts: AdminCountItem[]
}

export interface AdminUserListItem {
  userId: number
  username: string
  realName: string
  deptId: number | null
  deptName: string | null
  enabled: boolean
  roleCodes: string[]
  canEditRoles: boolean
  canToggleStatus: boolean
}

export interface AdminUserDetail extends AdminUserListItem {
  phone: string | null
  email: string | null
}

export interface AdminUserQuery {
  username?: string
  realName?: string
  deptId?: number | null
  enabled?: boolean | null
  roleCode?: string
}

export interface AdminUserQueryResponse {
  roles: AdminRoleOption[]
  users: AdminUserListItem[]
}

export interface ChangeDiffSummary {
  added: string[]
  removed: string[]
}

export interface UpdateUserRolesRequest {
  roleCodes: string[]
}

export interface UserRoleUpdateResponse {
  user: AdminUserDetail
  diff: ChangeDiffSummary
  query: AdminUserQueryResponse
}

export interface UpdateUserStatusRequest {
  enabled: boolean
}

export interface PermissionDefinition {
  permissionId: number
  permissionCode: string
  permissionName: string
  permissionGroup: string
  permissionDesc: string | null
}

export interface RolePermissionMatrixRow {
  roleCode: string
  roleName: string
  enabled: boolean
  permissionCodes: string[]
}

export interface RolePermissionMatrix {
  roles: AdminRoleOption[]
  permissions: PermissionDefinition[]
  matrix: RolePermissionMatrixRow[]
}

export interface UpdateRolePermissionsRequest {
  permissionCodes: string[]
}

export interface RolePermissionUpdateResponse {
  roleCode: string
  diff: ChangeDiffSummary
  matrix: RolePermissionMatrix
}

export interface AdminUserSummary {
  userId: number
  username: string
  realName: string
}

export interface AuditLogRecord {
  logId: string
  scopeType: string
  actionType: string
  projectId: number | null
  projectName: string | null
  operatorUser: AdminUserSummary | null
  targetUser: AdminUserSummary | null
  grantType: string | null
  roleCode: string | null
  permissionCode: string | null
  beforeSnapshot: string | null
  afterSnapshot: string | null
  remark: string | null
  createdAt: string
}

export interface AuditLogQueryResponse {
  logs: AuditLogRecord[]
}

export interface AdminProjectAuthorizationIndex {
  projects: ProjectSummary[]
  grantTypeCounts: AdminCountItem[]
}
