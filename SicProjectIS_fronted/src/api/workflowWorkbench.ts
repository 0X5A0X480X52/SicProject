import { apiRequest } from './client'
import type { WorkflowWorkbenchItem } from '../types/workflow'

export function listWorkflowWorkbenchItems() {
  return apiRequest<WorkflowWorkbenchItem[]>('/workflow-workbench/items')
}

export interface WorkbenchPage { items: WorkflowWorkbenchItem[]; page: number; pageSize: number; hasMore: boolean }
export function queryWorkflowWorkbench(view: string, moduleType: string, keyword: string, page: number, pageSize: number, signal?: AbortSignal) {
  const query = new URLSearchParams({ view, page: String(page), pageSize: String(pageSize) })
  if (moduleType !== 'ALL') query.set('moduleType', moduleType)
  if (keyword.trim()) query.set('keyword', keyword.trim())
  return apiRequest<WorkbenchPage>(`/workflow-workbench/query?${query}`, { signal })
}
