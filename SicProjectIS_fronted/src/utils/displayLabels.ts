export type LabelDictionary = Record<string, string>

export const roleLabels: LabelDictionary = {
  SYSTEM_ADMIN: '系统管理员',
  SCIENCE_ADMIN: '科技处管理员',
  DEPT_ADMIN: '二级单位管理员',
  PROJECT_LEADER: '项目负责人',
  EXPERT: '专家',
  FINANCE_ADMIN: '财务管理员',
  AUDITOR: '审计员',
  USER: '普通用户',
}

export const moduleTypeLabels: LabelDictionary = {
  APPLICATION: '项目申报',
  CONTRACT: '纵向合同',
  ACCEPTANCE: '项目结题',
}

export const resultLabels: LabelDictionary = {
  APPROVED: '通过',
  PASSED: '通过',
  REJECTED: '驳回',
  RETURNED: '退回',
  RETURN: '退回',
  FAILED: '不通过',
  SUBMITTED: '已提交',
  PENDING: '待处理',
}

export const eventLabels: LabelDictionary = {
  START: '流程启动',
  SUBMIT: '提交',
  RESUBMIT: '重新提交',
  APPROVE: '审核通过',
  REJECT: '驳回',
  RETURN: '退回补正',
  COMPLETE: '完成',
  ARRIVE: '任务到达',
  TRANSFER: '转交',
}

export const operationModeLabels: LabelDictionary = {
  SINGLE: '单人办理',
  ANY: '任一办理',
  ALL: '会签',
  SEQUENTIAL: '顺序办理',
  PARALLEL: '并行办理',
  MANUAL: '人工办理',
  AUTO: '自动处理',
}

export const lifecycleLabels: LabelDictionary = {
  APPLICATION_PROCESSING: '申报办理中',
  CONTRACT_PROCESSING: '合同办理中',
  ACCEPTANCE_PROCESSING: '结题办理中',
  RUNNING: '进行中',
  FINISHED: '已完成',
  CLOSED: '已关闭',
}

export const dataKindLabels: LabelDictionary = {
  NOTICE: '通知',
  APPLICATION_DRAFT: '申报书',
  CONTRACT_DRAFT: '合同书',
  ACCEPTANCE_DRAFT: '验收材料',
  CHECK_ITEM: '审核项',
  EXPERT_REVIEW: '专家评审',
  PUBLICITY: '公示',
  EXTERNAL_RESULT: '外部结果',
  SEAL: '盖章',
  SUBMISSION: '报送',
  ARCHIVE: '归档',
  FINANCIAL_SETTLEMENT: '财务结算',
  ACHIEVEMENT: '成果',
  SURPLUS_RETURN: '结余退回',
  DOCUMENT: '文档',
}

export function labelOf(value: string | number | null | undefined, dictionary: LabelDictionary) {
  if (value == null || value === '') return '-'
  const text = String(value)
  return dictionary[text] ?? text
}

export function roleLabel(value: string | null | undefined) {
  return labelOf(value, roleLabels)
}

export function moduleTypeLabel(value: string | null | undefined) {
  return labelOf(value, moduleTypeLabels)
}

export function resultLabel(value: string | null | undefined) {
  return labelOf(value, resultLabels)
}

export function eventLabel(value: string | null | undefined) {
  return labelOf(value, eventLabels)
}

export function operationModeLabel(value: string | null | undefined) {
  return labelOf(value, operationModeLabels)
}

export function lifecycleLabel(value: string | null | undefined) {
  return labelOf(value, lifecycleLabels)
}

export function dataKindLabel(value: string | null | undefined) {
  return labelOf(value, dataKindLabels)
}
