export interface DepartmentOption {
  deptId: number
  deptCode: string | null
  deptName: string | null
  parentDeptId: number | null
  enabled: boolean
}

export interface DepartmentMember {
  userId: number
  username: string
  realName: string
  deptId: number | null
  deptName: string | null
  phone: string | null
  email: string | null
  enabled: boolean
  roleCodes: string[]
  editableRoleCodes: string[]
}

export interface DepartmentMemberQueryResponse {
  departments: DepartmentOption[]
  assignableRoleCodes: string[]
  members: DepartmentMember[]
  canManageDepartments: boolean
  memberManagementDisabled: boolean
  disabledReason: string | null
}

export interface DepartmentMemberCandidateResponse {
  users: DepartmentMember[]
}

export interface SaveDepartmentRequest {
  deptCode: string
  deptName: string
  parentDeptId: number | null
  enabled: boolean
}

export interface UpsertDepartmentMemberRequest {
  roleCodes?: string[]
}

export interface UpdateDepartmentMemberRolesRequest {
  roleCodes: string[]
}