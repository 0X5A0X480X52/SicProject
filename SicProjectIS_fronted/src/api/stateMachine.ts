import { apiRequest } from './client'
import type {
  RuntimeViewResponse,
  StateTransitionRequest,
  StateTransitionResponse,
} from '../types/nodeForms'
import type { WorkflowBpmnResponse } from '../types/workflow'

export function startModuleInstance(projectId: number, moduleType: string) {
  return apiRequest<StateTransitionResponse>(`/projects/${projectId}/module-instances`, {
    method: 'POST',
    body: JSON.stringify({ moduleType }),
  })
}

export function getRuntimeView(moduleInstanceId: number) {
  return apiRequest<RuntimeViewResponse>(`/module-instances/${moduleInstanceId}/runtime-view`)
}

export function submitStateTransition(moduleInstanceId: number, request: StateTransitionRequest) {
  return apiRequest<StateTransitionResponse>(`/module-instances/${moduleInstanceId}/transitions`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getWorkflowBpmn(workflowDefinitionId: number) {
  return apiRequest<WorkflowBpmnResponse>(`/workflow-definitions/${workflowDefinitionId}/bpmn`)
}

