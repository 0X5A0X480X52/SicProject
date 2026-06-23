import { apiRequest } from './client'
import type {
  NodeFormContext,
  NodeFormDataResponse,
  NodeFormDefinition,
  NodeFormRecordResponse,
  NodeFormSaveRequest,
} from '../types/nodeForms'

function query(context: NodeFormContext) {
  const params = new URLSearchParams()
  if (context.projectId) params.set('projectId', String(context.projectId))
  if (context.moduleInstanceId) params.set('moduleInstanceId', String(context.moduleInstanceId))
  if (context.stateRecordId) params.set('stateRecordId', String(context.stateRecordId))
  const value = params.toString()
  return value ? `?${value}` : ''
}

export function getNodeFormDefinitions() {
  return apiRequest<NodeFormDefinition[]>('/node-forms/definitions')
}

export function getNodeForm(formCode: string, context: NodeFormContext) {
  return apiRequest<NodeFormDataResponse>(`/node-forms/${formCode}${query(context)}`)
}

export function saveNodeForm(formCode: string, request: NodeFormSaveRequest) {
  return apiRequest<NodeFormDataResponse>(`/node-forms/${formCode}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function createNodeFormRecord(formCode: string, request: NodeFormSaveRequest) {
  return apiRequest<NodeFormRecordResponse>(`/node-forms/${formCode}/records`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateNodeFormRecord(formCode: string, recordId: number, request: NodeFormSaveRequest) {
  return apiRequest<NodeFormRecordResponse>(`/node-forms/${formCode}/records/${recordId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteNodeFormRecord(formCode: string, recordId: number, context: NodeFormContext) {
  return apiRequest<void>(`/node-forms/${formCode}/records/${recordId}${query(context)}`, {
    method: 'DELETE',
  })
}
