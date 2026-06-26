import type { NodeFormDataKind } from '../types/nodeForms'

/**
 * All fields for each dataKind — extracted from NodeFormsDebugView.vue.
 * Each entry is [fieldKey, chineseLabel].
 */
export function fieldsForKind(kind?: NodeFormDataKind): Array<[string, string]> {
  switch (kind) {
    case 'APPLICATION_DRAFT':
      return [
        ['applicationTitle', '申请标题'],
        ['applicationCategory', '申请类别'],
        ['expectedBudget', '预算'],
        ['isLimitedProject', '限项'],
        ['applicationSummary', '摘要'],
        ['researchBackground', '研究背景'],
        ['researchObjective', '研究目标'],
        ['researchContent', '研究内容'],
        ['expectedOutcomes', '预期成果'],
      ]
    case 'CONTRACT_DRAFT':
      return [
        ['contractCode', '合同编号'],
        ['contractName', '合同名称'],
        ['contractAmount', '合同金额'],
        ['contractSource', '合同来源'],
        ['partyAName', '甲方'],
        ['partyBName', '乙方'],
        ['sealStatus', '用印状态'],
      ]
    case 'ACCEPTANCE_DRAFT':
      return [
        ['acceptanceType', '结题类型'],
        ['acceptanceBatchNo', '批次号'],
        ['taskCompletionRate', '完成率'],
        ['certificateNo', '证书编号'],
        ['conclusion', '结论'],
      ]
    case 'NOTICE':
      return [
        ['noticeType', '通知类型'],
        ['noticeTitle', '通知标题'],
        ['noticeNo', '通知编号'],
        ['publishUnit', '发布单位'],
        ['noticeScope', '范围'],
        ['materialRequirementSummary', '材料要求'],
        ['contentSummary', '内容摘要'],
      ]
    case 'CHECK_ITEM':
      return [
        ['itemCode', '检查项编码'],
        ['itemName', '检查项名称'],
        ['itemType', '类型'],
        ['itemValue', '值'],
        ['itemResult', '结果'],
        ['required', '必填'],
        ['passed', '通过'],
        ['remark', '备注'],
        ['sortNo', '排序'],
      ]
    case 'EXTERNAL_RESULT':
      return [
        ['resultType', '结果类型'],
        ['externalActorCode', '外部主体编码'],
        ['externalActorName', '外部主体'],
        ['externalResult', '结果'],
        ['externalFileNo', '文号'],
        ['externalSystemNo', '系统编号'],
        ['approvedAmount', '批准金额'],
        ['summary', '摘要'],
      ]
    case 'SEAL':
      return [
        ['sealSubject', '用印事项'],
        ['sealType', '印章类型'],
        ['sealReason', '原因'],
        ['copyCount', '份数'],
        ['leaderSigned', '负责人签字'],
        ['schoolSealed', '学校盖章'],
        ['externalSealed', '外部盖章'],
        ['sealStatus', '状态'],
        ['remark', '备注'],
      ]
    case 'SUBMISSION':
      return [
        ['submissionType', '报送类型'],
        ['targetActorCode', '目标编码'],
        ['targetActorName', '目标名称'],
        ['submissionMethod', '方式'],
        ['submissionNo', '报送编号'],
        ['receiptNo', '回执编号'],
        ['remark', '备注'],
      ]
    case 'ARCHIVE':
      return [
        ['archiveType', '归档类型'],
        ['archiveNo', '归档编号'],
        ['archiveLocation', '位置'],
        ['paperCopyCount', '纸质份数'],
        ['electronicCopyCount', '电子份数'],
        ['archiveStatus', '状态'],
        ['remark', '备注'],
      ]
    case 'PUBLICITY':
      return [
        ['publicityTitle', '公示标题'],
        ['publicityScope', '公示范围'],
        ['recommendedRank', '推荐排名'],
        ['recommendedReason', '推荐理由'],
        ['hasObjection', '异议'],
        ['objectionContent', '异议内容'],
        ['publicityResult', '公示结果'],
      ]
    case 'FINANCIAL_SETTLEMENT':
      return [
        ['approvedAmount', '批准经费'],
        ['receivedAmount', '到账经费'],
        ['spentAmount', '支出经费'],
        ['settlementResult', '决算结果'],
        ['financeReviewComment', '财务意见'],
      ]
    case 'ACHIEVEMENT':
      return [
        ['achievementType', '成果类型'],
        ['achievementTitle', '成果名称'],
        ['authorList', '作者'],
        ['achievementLevel', '级别'],
        ['proofMaterialVersionId', '证明材料版本'],
        ['remark', '备注'],
      ]
    case 'SURPLUS_RETURN':
      return [
        ['surplusAmount', '结余金额'],
        ['returnRequired', '需要退还'],
        ['returnAccountName', '账户名'],
        ['returnAccountNo', '账号'],
        ['returnBankName', '开户行'],
        ['returnStatus', '状态'],
        ['returnedAmount', '已退金额'],
        ['remark', '备注'],
      ]
    case 'EXPERT_REVIEW':
      return [
        ['reviewTitle', '评审标题'],
        ['ruleType', '汇总规则'],
        ['minExpertCount', '最少专家数'],
        ['expectedExpertCount', '预计专家数'],
        ['passScore', '通过分'],
        ['recommendScore', '推荐分'],
        ['removeHighestLowest', '去高低分'],
        ['batchId', '批次 ID'],
        ['expertUserId', '专家用户 ID'],
        ['expertName', '专家姓名'],
        ['assignmentId', '分配 ID'],
        ['itemCode', '评分项编码'],
        ['itemName', '评分项名称'],
        ['scoreValue', '得分'],
        ['reviewResult', '结论'],
        ['reviewComment', '意见'],
      ]
    default:
      return []
  }
}

/**
 * Determine the appropriate input type for a field key.
 */
export function fieldType(key: string): 'text' | 'number' | 'boolean' {
  if (key === 'noticeNo') return 'text'
  if (
    key.startsWith('is') ||
    key.startsWith('has') ||
    key.endsWith('Required') ||
    [
      'required',
      'passed',
      'leaderSigned',
      'schoolSealed',
      'externalSealed',
      'removeHighestLowest',
      'valid',
      'conflictOfInterest',
    ].includes(key)
  ) {
    return 'boolean'
  }
  if (/(Amount|Count|Rate|Rank|Score|Id|No|sortNo|minExpertCount|expectedExpertCount)$/i.test(key)) {
    return 'number'
  }
  return 'text'
}

/**
 * Fields that are system-generated and should NOT be exposed to frontend users.
 */
export const SYSTEM_FIELDS = new Set<string>([
  'itemCode',
  'itemName',
  'itemType',
  'itemResult',
  'passed',
  'required',
  'sortNo',
  'batchId',
  'assignmentId',
  'expertUserId',
  'expertName',
  'proofMaterialVersionId',
])

/**
 * Returns user-writable fields for a dataKind (all fields minus system-generated ones).
 */
export function filteredFieldsForKind(kind?: NodeFormDataKind): Array<[string, string]> {
  return fieldsForKind(kind).filter(([key]) => !SYSTEM_FIELDS.has(key))
}

/**
 * Default values for each dataKind, used when initializing a new form.
 */
export function defaultsForKind(kind?: NodeFormDataKind): Record<string, string | number | boolean | undefined> {
  const result: Record<string, string | number | boolean | undefined> = {}
  for (const [key] of fieldsForKind(kind)) result[key] = undefined
  if (kind === 'CHECK_ITEM') {
    result.required = true
    result.passed = true
  }
  if (kind === 'FINANCIAL_SETTLEMENT') {
    result.settlementResult = 'APPROVED'
  }
  if (kind === 'EXPERT_REVIEW') {
    result.ruleType = 'AVERAGE'
    result.minExpertCount = 3
    result.removeHighestLowest = false
  }
  return result
}

export function requiredFieldsForKind(kind?: NodeFormDataKind): string[] {
  switch (kind) {
    case 'FINANCIAL_SETTLEMENT':
      return ['receivedAmount', 'spentAmount']
    case 'ACHIEVEMENT':
      return ['achievementType', 'achievementTitle']
    default:
      return []
  }
}

/**
 * Determine whether a field key renders as a textarea based on its name.
 */
export function isTextareaField(key: string): boolean {
  return (
    key.includes('Content') ||
    key.includes('Summary') ||
    key.includes('summary') ||
    key === 'remark' ||
    key === 'reviewComment' ||
    key === 'financeReviewComment' ||
    key === 'objectionContent' ||
    key === 'researchBackground' ||
    key === 'researchObjective' ||
    key === 'researchContent' ||
    key === 'expectedOutcomes' ||
    key === 'recommendedReason' ||
    key === 'sealReason' ||
    key === 'conclusion' ||
    key === 'materialRequirementSummary' ||
    key === 'contentSummary' ||
    key === 'applicationSummary'
  )
}

export type FlatFormData = Record<string, string | number | boolean | null | undefined>

