import { apiRequest } from './client'
import type { WorkflowWorkbenchItem } from '../types/workflow'

export function listWorkflowWorkbenchItems() {
  return apiRequest<WorkflowWorkbenchItem[]>('/workflow-workbench/items')
}
