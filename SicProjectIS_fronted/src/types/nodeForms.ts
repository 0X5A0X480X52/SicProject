export type NodeFormModuleType = 'APPLICATION' | 'CONTRACT' | 'ACCEPTANCE'
export type NodeFormWriteMode = 'SINGLE_INSTANCE' | 'HISTORY_RECORD' | 'READ_ONLY'
export type NodeFormDataKind =
  | 'NOTICE'
  | 'APPLICATION_DRAFT'
  | 'CONTRACT_DRAFT'
  | 'ACCEPTANCE_DRAFT'
  | 'CHECK_ITEM'
  | 'EXPERT_REVIEW'
  | 'PUBLICITY'
  | 'EXTERNAL_RESULT'
  | 'SEAL'
  | 'SUBMISSION'
  | 'ARCHIVE'
  | 'FINANCIAL_SETTLEMENT'
  | 'ACHIEVEMENT'
  | 'SURPLUS_RETURN'
  | 'DOCUMENT'

export interface NodeFormDefinition {
  formCode: string
  moduleType: NodeFormModuleType
  nodeId: string
  stateCode: string
  title: string
  dataKind: NodeFormDataKind
  writeMode: NodeFormWriteMode
  supportsFiles: boolean
  materialTypeCodes: string[]
}

export interface NodeFormContext {
  projectId?: number
  moduleInstanceId?: number
  stateRecordId?: number
}

export interface NodeFormDataResponse {
  definition: NodeFormDefinition
  context: NodeFormContext
  applicationDraft?: unknown
  contractDraft?: unknown
  acceptanceDraft?: unknown
  notices: unknown[]
  checkItems: unknown[]
  externalResults: unknown[]
  sealRecords: unknown[]
  submissionRecords: unknown[]
  archiveRecords: unknown[]
  publicities: unknown[]
  financialSettlements: unknown[]
  achievements: unknown[]
  surplusReturns: unknown[]
  expertReview?: unknown
  materials: MaterialContextView[]
}

export interface NodeFormRecordResponse {
  definition: NodeFormDefinition
  recordId: number
  data: NodeFormDataResponse
}

export interface NodeFormSaveRequest {
  projectId?: number
  moduleInstanceId?: number
  stateRecordId?: number
  applicationDraft?: unknown
  contractDraft?: unknown
  acceptanceDraft?: unknown
  notice?: unknown
  runtimeRecord?: unknown
  projectRecord?: unknown
  expertReview?: unknown
  materialVersionIds?: number[]
}

export interface MaterialContextView {
  materialId: number
  projectId: number
  materialTypeId: number
  materialTypeCode: string
  materialTypeName: string
  moduleType: string
  allowedFileTypes?: string
  maxFileSizeMb?: number
  materialVersionId: number
  versionNo: number
  fileName: string
  fileUrl: string
  fileHash: string
  uploadedBy: number
  uploadedByName?: string
  uploadedAt: string
  isCurrent: boolean
}

export interface MaterialUploadResponse {
  version: unknown
  context: MaterialContextView
}

export interface ModuleStateRecord {
  stateRecordId: number
  moduleInstanceId: number
  seq: number
  roundNo: number
  eventType: string
  fromState?: string
  toState: string
  fromNodeId?: string
  toNodeId?: string
  result?: string
  summary?: string
  createdAt?: string
}

export interface TaskInstance {
  taskInstanceId: number
  moduleInstanceId: number
  nodeId: string
  stateCode: string
  assigneeUserId?: number
  candidateRoleCode?: string
  taskStatus: string
  roundNo?: number
  createdAt?: string
  completedAt?: string
}

export interface RuntimeContextView {
  moduleInstanceId: number
  projectId: number
  moduleType: NodeFormModuleType
  workflowDefinitionId: number
  currentSeq?: number
  currentRoundNo?: number
  currentState?: string
  currentNodeId?: string
  currentNodeName?: string
  currentNodeType?: string
  currentCandidateRoleCode?: string
  finishedAt?: string
}

export interface AvailableTransition {
  transitionId: string
  eventType: string
  result?: string
  targetRef: string
  targetStateCode?: string
  conditionType?: string
  conditionKey?: string
  conditionValue?: string
  conditionHandlerKey?: string
  actionKeys?: string[]
}

export interface MaterialRequirementView {
  requirementId: number
  materialTypeCode?: string
  materialTypeName?: string
  required?: boolean
  minCount?: number
  maxCount?: number
  usageType?: string
  validatorKey?: string
  description?: string
}

export interface RuntimeViewResponse {
  context: RuntimeContextView
  availableTransitions: AvailableTransition[]
  materialRequirements: MaterialRequirementView[]
  openTasks: TaskInstance[]
  history: ModuleStateRecord[]
}

export interface StateTransitionRequest {
  eventType: string
  expectedSeq?: number
  result?: string
  remark?: string
  materialVersionIds?: number[]
  formCode?: string
  nodeFormData?: NodeFormSaveRequest
}

export interface StateTransitionResponse {
  stateRecord: ModuleStateRecord
  currentNodeId: string
  currentState: string
  finished: boolean
}
