import { useAuthStore } from '../stores/auth'
import { apiRequest } from './client'
import type { MaterialContextView, MaterialUploadResponse } from '../types/nodeForms'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

export function listProjectMaterials(projectId: number) {
  return apiRequest<MaterialContextView[]>(`/projects/${projectId}/materials`)
}

export function uploadMaterialVersion(projectId: number, materialTypeCode: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<MaterialUploadResponse>(
    `/projects/${projectId}/materials/${encodeURIComponent(materialTypeCode)}/versions`,
    { method: 'POST', body },
  )
}

export function deleteMaterialVersion(id: number) {
  return apiRequest<void>(`/material-versions/${id}`, { method: 'DELETE' })
}

export async function downloadMaterialVersion(id: number) {
  const auth = useAuthStore()
  const response = await fetch(`${apiBaseUrl}/material-versions/${id}/download`, {
    headers: auth.token ? { Authorization: `Bearer ${auth.token}` } : {},
  })
  if (!response.ok) {
    throw new Error('Download failed')
  }
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const match = disposition.match(/filename\*=UTF-8''([^;]+)/)
  const fileName = match ? decodeURIComponent(match[1]) : `material-${id}`
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}
