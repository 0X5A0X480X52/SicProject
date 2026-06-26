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
  // 通用结果
  APPROVED: '通过',
  PASSED: '通过',
  REJECTED: '驳回',
  RETURNED: '退回',
  RETURN: '退回',
  FAILED: '不通过',
  SUBMITTED: '已提交',
  PENDING: '待处理',
  // 审核/评审结果
  REVIEW_FINISHED: '审核完成',
  REVIEW_SUBMITTED: '评审已提交',
  RECOMMENDED: '已推荐',
  // 分配/指派
  ASSIGNED: '已分配',
  // 签字/盖章
  SIGNED: '已签字',
  SEALED: '已盖章',
  // 通知/发布
  NOTICE_PUBLISHED: '通知已发布',
  // 决算
  SETTLED: '已决算',
  // 流程完成/驳回
  PROCESS_COMPLETED: '流程完成',
  PROCESS_REJECTED: '流程驳回',
  // 限项
  LIMITED: '限项',
  NON_LIMITED: '不限项',
  // 归档
  ARCHIVED: '已归档',
  // 证书
  CERTIFICATE_ISSUED: '证书已发放',
  // 结余退还
  SURPLUS_RETURNED: '结余已退还',
  // 启动
  STARTED: '已启动',
  // 完成（通用）
  COMPLETED: '已完成',
  FINISHED: '已完成',
  ACCEPTED: '验收通过',
  // 补充
  FAIL_FILE_ISSUED: '不通过文件已下达',
  NOTIFIED: '已通知',
  PDF_PRINTED: 'PDF已打印',
  PUBLICITY_FINISHED: '公示完成',
  SCHOOL_LEVEL: '校级',
  NON_SCHOOL_LEVEL: '非校级',
  START: '启动',
}

export const eventLabels: LabelDictionary = {
  // 通用事件
  START: '流程启动',
  SUBMIT: '提交',
  RESUBMIT: '重新提交',
  APPROVE: '审核通过',
  REJECT: '驳回',
  RETURN: '退回补正',
  COMPLETE: '完成',
  ARRIVE: '任务到达',
  TRANSFER: '转交',
  // 二级单位
  DEPT_REVIEW_FINISHED: '二级单位审核完成',
  DEPT_REVIEW_APPROVED: '二级单位审核通过',
  DEPT_REVIEW_REJECTED: '二级单位审核驳回',
  // 科技处
  SCIENCE_INITIAL_APPROVE: '科技处初审通过',
  SCIENCE_REVIEW_FINISHED: '科技处审核完成',
  SCIENCE_REVIEW_APPROVED: '科技处审核通过',
  SCIENCE_REVIEW_REJECTED: '科技处审核驳回',
  // 主管部门
  AUTHORITY_REVIEW_FINISHED: '主管部门审核完成',
  AUTHORITY_REVIEW_APPROVED: '主管部门审核通过',
  AUTHORITY_REVIEW_REJECTED: '主管部门审核驳回',
  // 项目负责人
  USER_CONFIRMED_SUBMIT: '负责人确认提交',
  DRAFT_SUBMITTED: '草稿已提交',
  // 公示
  PUBLICITY_FINISHED: '公示完成',
  PUBLICITY_PASSED: '公示通过',
  PUBLICITY_OBJECTION: '公示有异议',
  // 专家评审
  EXPERT_REVIEW_FINISHED: '专家评审完成',
  EXPERT_ASSIGN_FINISHED: '专家分配完成',
  EXPERT_SCORE_SUBMITTED: '专家评分已提交',
  // 财务
  FINANCIAL_SETTLEMENT_FINISHED: '财务决算完成',
  // 用印/签字
  SEAL_FINISHED: '用印完成',
  LEADER_SIGN_FINISHED: '负责人签字完成',
  // 报送
  SUBMISSION_FINISHED: '报送完成',
  // 归档
  ARCHIVE_FINISHED: '归档完成',
  // 证书
  CERTIFICATE_ISSUED: '证书已发放',
  // 结余退还
  SURPLUS_RETURN_FINISHED: '结余退还完成',
  // 通知
  NOTICE_PUBLISHED: '通知已发布',
  // ===== 以下为 BPMN 特定事件类型（补充通用标签） =====
  APPLICATION_SELF_STARTED: '项目负责人发起申报',
  APPLICATION_NOTICE_PUBLISHED: '申报通知已发布',
  APPLICATION_PROCESS_STARTED: '申报流程启动',
  CONTRACT_PROCESS_STARTED: '合同流程启动',
  CONTRACT_PDF_PRINTED: '合同 PDF 已打印',
  CONTRACT_ARCHIVED: '合同已归档',
  CONTRACT_CONFIRMED_SUBMIT: '合同确认提交',
  ACCEPTANCE_PROCESS_STARTED: '结题流程启动',
  ACCEPTANCE_NOTICE_PUBLISHED: '结题通知已发布',
  ACCEPTANCE_CONFIRMED_SUBMIT: '结题确认提交',
  ACCEPTANCE_CERTIFICATE_ISSUED: '验收证书已发放',
  ACCEPTANCE_FAIL_FILE_ISSUED: '不通过文件已下达',
  ACCEPTANCE_FINAL_MATERIALS_SUBMITTED: '最终材料已报送',
  ACCEPTANCE_SIGN_SEAL_COMPLETED: '签字用印完成',
  DEPT_APPROVE: '二级单位通过',
  DEPT_REJECT: '二级单位驳回',
  DEPT_ACCEPTANCE_APPROVE: '二级单位结题通过',
  DEPT_ACCEPTANCE_REJECT: '二级单位结题驳回',
  DEPT_ACCEPTANCE_REVIEW_FINISHED: '二级单位结题审核完成',
  DEPT_CONTRACT_APPROVE: '二级单位合同通过',
  DEPT_CONTRACT_REJECT: '二级单位合同驳回',
  DEPT_CONTRACT_REVIEW_FINISHED: '二级单位合同审核完成',
  DEPT_EXPERT_APPROVE: '二级单位专家评审通过',
  DEPT_EXPERT_REJECT: '二级单位专家评审驳回',
  DEPT_EXPERT_ASSIGNED: '二级单位专家已分配',
  DEPT_EXPERT_REVIEW_FINISHED: '二级单位专家评审完成',
  DEPT_EXPERT_REVIEW_SUBMITTED: '二级单位专家评分已提交',
  DEPT_LEADER_NOTIFIED: '已通知项目负责人',
  SCIENCE_INITIAL_REJECT: '科技处初审驳回',
  SCIENCE_INITIAL_REVIEW_FINISHED: '科技处初审完成',
  SCIENCE_ACCEPTANCE_APPROVE: '科技处结题通过',
  SCIENCE_ACCEPTANCE_REJECT: '科技处结题驳回',
  SCIENCE_ACCEPTANCE_REVIEW_FINISHED: '科技处结题审核完成',
  SCIENCE_CONTRACT_APPROVE: '科技处合同通过',
  SCIENCE_CONTRACT_REJECT: '科技处合同驳回',
  SCIENCE_CONTRACT_REVIEW_FINISHED: '科技处合同审核完成',
  SCIENCE_EXPERT_APPROVE: '科技处专家评审通过',
  SCIENCE_EXPERT_REJECT: '科技处专家评审驳回',
  SCIENCE_EXPERT_ASSIGNED: '科技处专家已分配',
  SCIENCE_EXPERT_REVIEW_FINISHED: '科技处专家评审完成',
  SCIENCE_EXPERT_REVIEW_SUBMITTED: '科技处专家评分已提交',
  SCIENCE_SUBMIT_TO_AUTHORITY: '科技处审核上报',
  AUTHORITY_APPROVE: '主管部门通过',
  AUTHORITY_REJECT: '主管部门驳回',
  AUTHORITY_ACCEPTANCE_APPROVE: '主管部门结题通过',
  AUTHORITY_ACCEPTANCE_REJECT: '主管部门结题驳回',
  AUTHORITY_ACCEPTANCE_REVIEW_FINISHED: '主管部门结题审核完成',
  AUTHORITY_CONTRACT_APPROVE: '主管部门合同通过',
  AUTHORITY_CONTRACT_REJECT: '主管部门合同驳回',
  AUTHORITY_CONTRACT_REVIEW_FINISHED: '主管部门合同审核完成',
  AUTHORITY_SEAL_COMPLETED: '主管部门盖章完成',
  EXPERT_ACCEPTANCE_APPROVE: '专家结题通过',
  EXPERT_ACCEPTANCE_REJECT: '专家结题驳回',
  EXPERT_ACCEPTANCE_ASSIGNED: '专家已分配',
  EXPERT_ACCEPTANCE_REVIEW_FINISHED: '专家结题评审完成',
  EXPERT_ACCEPTANCE_REVIEW_SUBMITTED: '专家结题评分已提交',
  LEADER_SIGN_COMPLETED: '负责人签字完成',
  PUBLICITY_APPROVE: '公示通过',
  PUBLICITY_REJECT: '公示驳回',
  FINANCIAL_SETTLEMENT_COMPLETED: '财务决算完成',
  SURPLUS_FUNDS_RETURNED: '结余经费已退还',
  SIGN_AND_SEAL_COMPLETED: '签字用印完成',
  SCHOOL_SEAL_COMPLETED: '学校盖章完成',
  FINAL_MATERIALS_SUBMITTED: '最终材料已报送',
  LIMITED_PROJECT_SELECTED: '已选择限项项目',
  NON_LIMITED_PROJECT_SELECTED: '已选择非限项项目',
  SCHOOL_LEVEL_ACCEPTANCE_SELECTED: '已选择校级结题',
  NON_SCHOOL_LEVEL_ACCEPTANCE_SELECTED: '已选择非校级结题',
  PROJECT_APPROVAL_REGISTERED: '立项已登记',
}

export const operationModeLabels: LabelDictionary = {
  SINGLE: '单人办理',
  ANY: '任一办理',
  ALL: '会签',
  SEQUENTIAL: '顺序办理',
  PARALLEL: '并行办理',
  MANUAL: '人工办理',
  AUTO: '自动处理',
  SELF_OPERATE: '本人办理',
  PROXY_INPUT: '代理录入',
  SYSTEM_AUTO: '系统自动',
}

export const lifecycleLabels: LabelDictionary = {
  // displayLabels 旧键（保持兼容）
  APPLICATION_PROCESSING: '申报办理中',
  CONTRACT_PROCESSING: '合同办理中',
  ACCEPTANCE_PROCESSING: '结题办理中',
  RUNNING: '进行中',
  FINISHED: '已完成',
  CLOSED: '已关闭',
  // 后端实际使用的 lifecycleStage 值
  APPLICATION: '申报办理中',
  APPLICATION_FINISHED: '已立项',
  CONTRACT: '合同办理中',
  CONTRACT_FINISHED: '合同已完成',
  ACCEPTANCE: '结题办理中',
  ACCEPTANCE_FINISHED: '已结题',
}

/**
 * BPMN 状态码 → 中文标签（覆盖 3 个流程共 46 个状态码）
 */
export const stateLabels: LabelDictionary = {
  // ========== 项目申请流程 ==========
  APPLICATION_START: '流程开始',
  APPLICATION_NOTICE_PUBLISHING: '发布申报通知',
  APPLICATION_DRAFT: '填报申请书',
  APPLICATION_DEPT_REVIEWING: '二级单位形式审核',
  APPLICATION_DEPT_EXPERT_ASSIGNING: '二级单位分配评审专家',
  APPLICATION_DEPT_EXPERT_REVIEWING: '二级单位专家审核',
  APPLICATION_DEPT_EXPERT_SUMMARIZING: '二级单位汇总专家评审',
  APPLICATION_SCIENCE_INITIAL_REVIEWING: '科技处初审',
  APPLICATION_SCIENCE_EXPERT_ASSIGNING: '科技处分配评审专家',
  APPLICATION_SCIENCE_EXPERT_REVIEWING: '科技处专家审核',
  APPLICATION_SCIENCE_EXPERT_SUMMARIZING: '科技处汇总专家评审',
  APPLICATION_PUBLICITY: '择优推荐并公示',
  APPLICATION_SCIENCE_SUBMITTING: '科技处审核上报',
  APPLICATION_AUTHORITY_REVIEWING: '主管部门审核',
  APPLICATION_SIGN_SEALING: '签字并申请用章',
  APPLICATION_FINAL_MATERIAL_SUBMITTING: '正式报送',
  APPLICATION_APPROVED: '申报完成',

  // ========== 纵向项目合同流程 ==========
  CONTRACT_START: '流程开始',
  CONTRACT_PROJECT_APPROVED: '批准立项',
  CONTRACT_DRAFT: '填写项目合同',
  CONTRACT_DEPT_REVIEWING: '二级单位审核',
  CONTRACT_SCIENCE_REVIEWING: '科技处审核',
  CONTRACT_AUTHORITY_REVIEWING: '主管部门审核',
  CONTRACT_PDF_PRINTING: '打印合同书',
  CONTRACT_LEADER_SIGNING: '项目负责人签字',
  CONTRACT_SCHOOL_SEALING: '学校盖章',
  CONTRACT_AUTHORITY_SEALING: '主管部门盖章',
  CONTRACT_ARCHIVING: '合同归档',
  CONTRACT_APPROVED: '合同流程完成',

  // ========== 项目结题流程 ==========
  ACCEPTANCE_START: '流程开始',
  ACCEPTANCE_NOTICE_PUBLISHING: '发布结题验收通知',
  ACCEPTANCE_DEPT_NOTIFYING: '通知项目负责人',
  ACCEPTANCE_FINANCIAL_SETTLEMENT: '经费决算',
  ACCEPTANCE_MATERIAL_DRAFT: '填报结题验收材料',
  ACCEPTANCE_DEPT_REVIEWING: '二级单位审核',
  ACCEPTANCE_SCIENCE_REVIEWING: '科技处审核',
  ACCEPTANCE_AUTHORITY_REVIEWING: '主管部门审核',
  ACCEPTANCE_SIGN_SEALING: '签字并申请用章',
  ACCEPTANCE_FINAL_MATERIAL_SUBMITTING: '最终报送',
  ACCEPTANCE_EXPERT_ASSIGNING: '分配评审专家',
  ACCEPTANCE_EXPERT_REVIEWING: '专家审核提交',
  ACCEPTANCE_EXPERT_SUMMARIZING: '汇总专家评审结果',
  ACCEPTANCE_CERTIFICATE_ISSUING: '发放验收证书',
  ACCEPTANCE_FAIL_FILE_ISSUING: '下达不通过文件',
  ACCEPTANCE_SURPLUS_FUNDS_RETURNING: '退还结余经费',
  ACCEPTANCE_ACCEPTED: '结题验收通过',
  ACCEPTANCE_REJECTED: '结题未通过',

  // ========== 通用 ==========
  START: '开始',
  END: '结束',
}

/**
 * 任务状态 → 中文标签
 */
export const taskStatusLabels: LabelDictionary = {
  PENDING: '待领取',
  CLAIMED: '已领取',
  IN_PROGRESS: '办理中',
  SUBMITTED: '已提交',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
}

/**
 * 评审批次状态 → 中文标签
 */
export const reviewBatchStatusLabels: LabelDictionary = {
  CREATED: '已创建',
  IN_PROGRESS: '评审中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
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

// ========== 标签函数 ==========

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

export function stateLabel(value: string | null | undefined) {
  return labelOf(value, stateLabels)
}

export function dataKindLabel(value: string | null | undefined) {
  return labelOf(value, dataKindLabels)
}

export function taskStatusLabel(value: string | null | undefined) {
  return labelOf(value, taskStatusLabels)
}

export function reviewBatchStatusLabel(value: string | null | undefined) {
  return labelOf(value, reviewBatchStatusLabels)
}
