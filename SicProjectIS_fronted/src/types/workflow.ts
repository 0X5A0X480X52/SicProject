/**
 * 声明工作台与实时刷新事件；模块 finished 与项目 lifecycleStage 分开解释，事件提示后仍需重新读取当前权限和状态。
 */
export type ModuleType = 'APPLICATION' | 'CONTRACT' | 'ACCEPTANCE' | string

export interface WorkflowWorkbenchItem {
  moduleInstanceId: number
  projectId: number
  projectCode?: string | null
  projectName?: string | null
  moduleType: ModuleType
  /** 项目整体生命周期，不能用某一个模块的 finished 代替。 */
  lifecycleStage?: string | null
  workflowDefinitionId: number
  currentState?: string | null
  currentNodeId?: string | null
  currentNodeName?: string | null
  candidateRoleCode?: string | null
  currentSeq?: number | null
  currentRoundNo?: number | null
  lastTransitionTime?: string | null
  /** 当前模块已结束，结束后是否仍可读取决于当前项目权限。 */
  finished: boolean
  todo: boolean
  /** 后端判定当前节点可以办理；前端入口不自行按候选角色推导。 */
  canOperate: boolean
  /** 当前账号有办理历史；历史存在不恢复已经撤销的读取权限。 */
  operated?: boolean
}

export interface WorkflowBpmnResponse {
  workflowDefinitionId: number
  bpmnXml: string
}

export interface WorkflowNodeDefinition {
  nodeId: string
  nodeName?: string | null
  nodeType?: string | null
  stateCode?: string | null
  laneName?: string | null
  responsibleActorCode?: string | null
  responsibleActorName?: string | null
  candidateRoleCode?: string | null
  operationMode?: string | null
  representedActorCode?: string | null
  representedActorName?: string | null
  materialRequirements?: unknown[]
  documentConfigs?: unknown[]
}

export interface ModuleStateChangedEvent {
  /** 刷新提示，接收后从业务接口重查，不能直接更新授权结论。 */
  type: 'MODULE_STATE_CHANGED' | 'WORKBENCH_INVALIDATED'
  projectId?: number
  moduleInstanceId?: number
  moduleType?: string
  fromState?: string
  toState?: string
  seq?: number
  eventType?: string
  occurredAt?: string
}

