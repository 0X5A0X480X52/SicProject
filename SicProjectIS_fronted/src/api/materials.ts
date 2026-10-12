/**
 * 按具体材料版本上传、下载或删除文件；文件请求附带认证，主材料 ID 与 materialVersionId 不能混用。
 */
import { useAuthStore } from '../stores/auth'
import { apiRequest } from './client'
import type { MaterialContextView, MaterialUploadResponse } from '../types/nodeForms'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

export function listProjectMaterials(projectId: number, signal?: AbortSignal) {
  return apiRequest<MaterialContextView[]>(`/projects/${projectId}/materials`, { signal })
}

/**
 * 上传 multipart 文件并返回实际版本 ID；界面随后选择这个版本，流程提交时才建立业务引用。
 */
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

/**
 * 二进制下载绕开 JSON 解包，手动携带当前 token；解析文件名并在点击下载后释放 blob URL。这里的错误处理不复用 apiRequest 的 401 清理分支。
 */
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
