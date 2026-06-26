export const processData = {
  moduleName: '科研项目结题验收流程',
  projectName: '基于知识图谱的科研项目智能管理系统',
  projectNo: 'SIC-2026-004',
  applicationNo: 'APP-20260625165339-1',
  statusText: '进行中',
  lifecycle: 'ACCEPTANCE_PROCESSING',
  currentRoundNo: 3,
  updatedAt: '2026-06-25 20:36',
  owner: '张恒睿',
  department: '管理学院',
  projectType: '纵向科研项目',
  projectLevel: '校级',
  currentNodeId: 'expert_review',
  budget: '30.00 万元',
  startDate: '2025-01-01',
  endDate: '2026-06-30',
  contact: 'zhr20050305@outlook.com',
  collaborators: ['管理学院', '信息中心', '校外合作单位 A'],
  keywords: ['知识图谱', '科研管理', '流程审批', '智能检索'],
  currentTask: {
    nodeName: '专家评审',
    assignee: '张老师',
    roleName: '专家',
    arrivedAt: '2026-06-25 14:30',
    deadline: '2026-06-28 18:00',
    mode: '会签',
    instruction: '请对项目创新性、成果材料完整性和经费使用合理性进行评审。'
  },
  nodes: [
    { id: 'notice', name: '发布通知', status: 'done', statusText: '已完成', roundCount: 1, actor: '科技处', finishedAt: '06-20 09:20', desc: '启动结题验收流程' },
    { id: 'submit', name: '提交材料', status: 'done', statusText: '已完成', roundCount: 3, actor: '项目负责人', finishedAt: '06-24 16:40', desc: '补正后重新提交材料' },
    { id: 'dept_review', name: '学院审核', status: 'done', statusText: '已完成', roundCount: 2, actor: '二级单位', finishedAt: '06-25 09:10', desc: '学院审核通过' },
    { id: 'office_review', name: '科技处审核', status: 'done', statusText: '已完成', roundCount: 3, actor: '科技处', finishedAt: '06-25 10:20', desc: '材料齐全，进入专家评审' },
    { id: 'expert_review', name: '专家评审', status: 'current', statusText: '处理中', roundCount: 1, actor: '专家', finishedAt: '', desc: '当前办理节点' },
    { id: 'funds_review', name: '经费审核', status: 'waiting', statusText: '待进行', actor: '财务', desc: '等待专家评审完成' },
    { id: 'archive', name: '归档确认', status: 'waiting', statusText: '待进行', actor: '科技处', desc: '流程结束前归档确认' },
    { id: 'finish', name: '完成', status: 'waiting', statusText: '待进行', actor: '系统', desc: '生成最终状态' }
  ],
  materials: [
    { id: 1, name: '结题验收申请书.pdf', type: '必交', status: '已提交', version: 'v3', updatedAt: '06-24 16:40', owner: '项目负责人' },
    { id: 2, name: '项目成果清单.docx', type: '必交', status: '已提交', version: 'v3', updatedAt: '06-24 16:40', owner: '项目负责人' },
    { id: 3, name: '经费使用说明.xlsx', type: '补充', status: '已补正', version: 'v2', updatedAt: '06-24 16:38', owner: '项目负责人' },
    { id: 4, name: '专家评审意见表.docx', type: '节点材料', status: '待生成', version: '-', updatedAt: '-', owner: '专家' }
  ],
  materialChecks: [
    { label: '必交材料', value: '2/2', status: 'success' },
    { label: '补正材料', value: '1/1', status: 'success' },
    { label: '当前节点材料', value: '0/1', status: 'warning' }
  ],
  opinions: [
    { id: 1, role: '二级单位', operator: '王老师', conclusion: '通过', time: '2026-06-25 09:10', content: '学院确认材料完整，建议进入科技处审核。' },
    { id: 2, role: '科技处', operator: '李老师', conclusion: '通过', time: '2026-06-25 10:20', content: '材料齐全，进入专家评审。' },
    { id: 3, role: '专家', operator: '张老师', conclusion: '待填写', time: '-', content: '当前节点待专家填写评审意见。' }
  ],
  risks: [
    { id: 1, type: 'warning', title: '当前节点还有 3 天到期', desc: '建议在截止时间前完成专家评审。' },
    { id: 2, type: 'info', title: '历史退回 2 次', desc: '退回原因集中在材料附件和经费说明，当前轮已补正。' },
    { id: 3, type: 'warning', title: '专家评审意见表待生成', desc: '通过后需要自动归档或要求专家上传意见表。' }
  ],
  availableActions: [
    { name: '通过', type: 'primary' },
    { name: '退回补正', type: 'warning' },
    { name: '转交', type: 'default' },
    { name: '查看材料', type: 'default' }
  ],
  currentRound: {
    roundNo: 3,
    statusText: '当前轮次',
    records: [
      { id: 301, nodeName: '提交材料', action: 'submit', actionText: '重新提交', operator: '张恒睿', role: '项目负责人', time: '2026-06-24 16:40', remark: '已补充经费使用说明和成果佐证材料。' },
      { id: 302, nodeName: '学院审核', action: 'approve', actionText: '审核通过', operator: '王老师', role: '二级单位', time: '2026-06-25 09:10', remark: '学院确认材料完整，建议进入科技处审核。' },
      { id: 303, nodeName: '科技处审核', action: 'approve', actionText: '审核通过', operator: '李老师', role: '科技处', time: '2026-06-25 10:20', remark: '材料齐全，进入专家评审。' },
      { id: 304, nodeName: '专家评审', action: 'arrive', actionText: '任务到达', operator: '系统', role: '系统', time: '2026-06-25 14:30', remark: '已分配给专家张老师，会签模式。' }
    ]
  },
  historyRounds: [
    {
      roundNo: 2,
      result: 'returned',
      resultText: '已退回',
      summary: '科技处退回：经费说明不完整',
      startAt: '2026-06-23 09:00',
      endAt: '2026-06-24 10:15',
      records: [
        { id: 201, nodeName: '提交材料', action: 'submit', actionText: '提交材料', operator: '张恒睿', role: '项目负责人', time: '2026-06-23 09:00', remark: '提交第二版结题材料。' },
        { id: 202, nodeName: '学院审核', action: 'approve', actionText: '审核通过', operator: '王老师', role: '二级单位', time: '2026-06-23 15:20', remark: '学院审核通过。' },
        { id: 203, nodeName: '科技处审核', action: 'return', actionText: '退回补正', operator: '李老师', role: '科技处', time: '2026-06-24 10:15', remark: '经费使用说明缺少明细，请补充。' }
      ]
    },
    {
      roundNo: 1,
      result: 'returned',
      resultText: '已退回',
      summary: '学院退回：成果清单附件缺失',
      startAt: '2026-06-20 09:20',
      endAt: '2026-06-22 17:30',
      records: [
        { id: 101, nodeName: '发布通知', action: 'start', actionText: '流程启动', operator: '系统', role: '系统', time: '2026-06-20 09:20', remark: '结题流程启动。' },
        { id: 102, nodeName: '提交材料', action: 'submit', actionText: '提交材料', operator: '张恒睿', role: '项目负责人', time: '2026-06-22 10:05', remark: '提交第一版结题材料。' },
        { id: 103, nodeName: '学院审核', action: 'return', actionText: '退回补正', operator: '王老师', role: '二级单位', time: '2026-06-22 17:30', remark: '成果清单附件缺失，请补齐后重新提交。' }
      ]
    }
  ]
}
