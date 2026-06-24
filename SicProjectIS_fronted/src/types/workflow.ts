export type ModuleType = 'APPLICATION' | 'CONTRACT' | 'ACCEPTANCE' | string

export interface WorkflowWorkbenchItem {
  moduleInstanceId: number
  projectId: number
  projectCode?: string | null
  projectName?: string | null
  moduleType: ModuleType
  lifecycleStage?: string | null
  workflowDefinitionId: number
  currentState?: string | null
  currentNodeId?: string | null
  currentNodeName?: string | null
  candidateRoleCode?: string | null
  currentSeq?: number | null
  currentRoundNo?: number | null
  lastTransitionTime?: string | null
  finished: boolean
  todo: boolean
  canOperate: boolean
}

export interface WorkflowBpmnResponse {
  workflowDefinitionId: number
  bpmnXml: string
}

export interface ModuleStateChangedEvent {
  type: 'MODULE_STATE_CHANGED'
  projectId?: number
  moduleInstanceId?: number
  moduleType?: string
  fromState?: string
  toState?: string
  seq?: number
  eventType?: string
  occurredAt?: string
}
