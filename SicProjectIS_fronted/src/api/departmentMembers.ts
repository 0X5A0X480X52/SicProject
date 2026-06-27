import { apiRequest } from './client'
import type {
  DepartmentMember,
  DepartmentMemberCandidateResponse,
  DepartmentMemberQueryResponse,
  DepartmentOption,
  SaveDepartmentRequest,
  UpdateDepartmentMemberRolesRequest,
  UpsertDepartmentMemberRequest,
} from '../types/departmentMembers'

export function getDepartments() {
  return apiRequest<DepartmentOption[]>('/admin/departments')
}

export function createDepartment(request: SaveDepartmentRequest) {
  return apiRequest<DepartmentOption>('/admin/departments', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateDepartment(deptId: number, request: SaveDepartmentRequest) {
  return apiRequest<DepartmentOption>(`/admin/departments/${deptId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteDepartment(deptId: number) {
  return apiRequest<void>(`/admin/departments/${deptId}`, { method: 'DELETE' })
}

export function getDepartmentMembers(deptId?: number | null) {
  const params = new URLSearchParams()
  if (deptId != null) params.set('deptId', String(deptId))
  const text = params.toString()
  return apiRequest<DepartmentMemberQueryResponse>(`/admin/departments/members${text ? `?${text}` : ''}`)
}

export function searchDepartmentMemberCandidates(keyword?: string) {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  const text = params.toString()
  return apiRequest<DepartmentMemberCandidateResponse>(`/admin/departments/candidates${text ? `?${text}` : ''}`)
}

export function upsertDepartmentMember(deptId: number, userId: number, request: UpsertDepartmentMemberRequest) {
  return apiRequest<DepartmentMember>(`/admin/departments/${deptId}/members/${userId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function updateDepartmentMemberRoles(userId: number, request: UpdateDepartmentMemberRolesRequest) {
  return apiRequest<DepartmentMember>(`/admin/departments/members/${userId}/roles`, {
    method: 'PATCH',
    body: JSON.stringify(request),
  })
}

export function removeDepartmentMember(userId: number) {
  return apiRequest<DepartmentMember>(`/admin/departments/members/${userId}`, { method: 'DELETE' })
}