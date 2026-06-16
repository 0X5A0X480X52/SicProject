# 科研项目流程管理系统数据表设计清单 V2

## 1. 设计口径

本文是在 V1“流程节点表单与交付物细化设计”的基础上，进一步整理得到的完整数据表设计清单。

V1 中大量表单字段通过以下机制组合形成“虚拟表单”：

```text
project
project_application / project_contract / project_acceptance
project_member
module_state_record
state_record_remark
state_record_material
material / material_version
process_document / process_document_file
```

V2 继续保留上述主干机制，但将 V1 中大量依赖 `payload_json` 的字段尽可能转化为结构化数据表。

V2 的核心原则是：

```text
1. 核心业务事实进入正式业务表；
2. 稳定扩展字段进入业务扩展表；
3. 审核检查项进入通用检查项表；
4. 外部主管部门/第三方结果进入外部结果表；
5. 签字盖章/用印进入用印记录表；
6. 上报和归档进入上报/归档记录表；
7. 专家评审进入独立专家评审表；
8. 文件材料继续由 material / material_version 管理；
9. 正式归档单据继续由 process_document 固化；
10. payload_json 仅保留为兜底、临时上下文、原始请求摘要。
```

---

## 2. 数据表总览

### 2.1 权限与组织表

| 表名 | 中文含义 | 说明 |
|---|---|---|
| `department` | 部门表 | 学院、二级单位、科技处、财务处等组织 |
| `app_user` | 用户表 | 系统登录用户 |
| `role` | 角色表 | 项目负责人、二级单位管理员、科技处管理员、专家等 |
| `permission` | 权限表 | 菜单、按钮、接口权限 |
| `user_role` | 用户角色关联表 | 一个用户可拥有多个角色 |
| `role_permission` | 角色权限关联表 | 一个角色可拥有多个权限 |

---

### 2.2 BPMN 流程定义表

| 表名 | 中文含义 | 说明 |
|---|---|---|
| `workflow_definition` | 流程定义表 | 保存 BPMN 文件和流程版本 |
| `workflow_node` | 流程节点表 | 保存 BPMN 节点状态、候选角色、操作模式 |
| `workflow_node_material_requirement` | 节点材料要求表 | 描述某节点需要哪些材料 |
| `workflow_node_document_config` | 节点单据配置表 | 描述某节点结束后生成哪些单据 |
| `workflow_node_config_view` | 节点配置视图 | 聚合节点、材料、单据配置 |

---

### 2.3 项目业务表

| 表名 | 中文含义 | 说明 |
|---|---|---|
| `project` | 项目主表 | 项目基础信息与生命周期状态 |
| `project_member` | 项目成员表 | 项目负责人、成员、财务联系人等 |
| `project_application` | 项目申报主表 | 申报模块核心字段 |
| `project_application_ext` | 项目申报扩展表 | 申报批次、限项、推荐结果、主管部门批复等 |
| `project_application_detail` | 项目申请书详细内容表 | 在线填写申请书详细字段 |
| `project_application_publicity` | 项目申报公示表 | 推荐名单公示与异议处理 |
| `project_contract` | 纵向合同主表 | 合同核心字段 |
| `project_contract_ext` | 纵向合同扩展表 | 甲乙方、签字盖章、归档等扩展字段 |
| `project_acceptance` | 项目结题主表 | 结题核心字段 |
| `project_acceptance_ext` | 项目结题扩展表 | 任务完成率、成果统计、证书、结余经费等 |
| `acceptance_financial_settlement` | 结题经费决算表 | 经费到账、支出、结余、执行率 |
| `project_achievement` | 项目成果表 | 论文、专利、软著、奖励等成果 |
| `surplus_funds_return_record` | 结余经费退还表 | 结余经费退还记录 |

---

### 2.4 状态机运行时表

| 表名 | 中文含义 | 说明 |
|---|---|---|
| `project_module_instance` | 项目模块实例表 | 一个项目的一次申报、合同或结题流程实例 |
| `module_state_record` | 模块状态记录表 | 状态迁移事实，只追加不修改 |
| `state_record_remark` | 状态记录意见表 | 操作人、审核意见、代录说明、专家文字意见 |
| `state_record_material` | 状态记录材料关联表 | 状态迁移与材料版本关联 |
| `state_record_check_item` | 状态记录检查项表 | 审核清单、检查项、布尔判断等 |
| `module_runtime_context_view` | 模块运行上下文视图 | 聚合当前状态、项目、节点、任务等 |
| `state_record_context_view` | 状态记录上下文视图 | 聚合状态记录、意见、操作人等 |

---

### 2.5 通用流程业务记录表

| 表名 | 中文含义 | 说明 |
|---|---|---|
| `notice_record` | 通知发布记录表 | 申报通知、结题通知 |
| `external_result_record` | 外部结果登记表 | 主管部门/第三方审核、批复、盖章、生效等 |
| `seal_record` | 签字盖章/用印记录表 | 签字、学校盖章、外部盖章、用印份数 |
| `submission_record` | 材料上报记录表 | 上报对象、方式、编号、回执 |
| `archive_record` | 归档记录表 | 合同归档、材料归档、档案编号与位置 |

---

### 2.6 材料与单据表

| 表名 | 中文含义 | 说明 |
|---|---|---|
| `material_type` | 材料类型表 | 申请书、合同、批复文件、证书等材料类型 |
| `material` | 材料主表 | 某项目下的一个材料对象 |
| `material_version` | 材料版本表 | 文件版本、文件路径、哈希、上传人 |
| `material_context_view` | 材料上下文视图 | 聚合材料、版本、类型、上传人等 |
| `process_document` | 流程单据表 | 固化审批单、归档单、证书等正式单据 |
| `process_document_file` | 流程单据文件表 | 单据关联的 PDF、扫描件、附件等文件 |

---

### 2.7 待办与专家评审表

| 表名 | 中文含义 | 说明 |
|---|---|---|
| `task_instance` | 待办任务表 | 当前节点待办、候选角色、处理人 |
| `expert_review_batch` | 专家评审批次表 | 一次专家评审或验收的汇总 |
| `expert_review_assignment` | 专家分配表 | 某专家被分配到某评审批次 |
| `expert_review_score` | 专家评分项表 | 每位专家对各评分项的得分 |

---

## 3. 权限与组织表

---

### 3.1 `department` 部门表

用途：

```text
保存学校组织结构，包括学院、二级单位、科技处、财务处、外部协作部门等。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `dept_id` | 部门 ID |
| `dept_code` | 部门编码 |
| `dept_name` | 部门名称 |
| `parent_dept_id` | 上级部门 |
| `dept_type` | 学院 / 科技处 / 财务处 / 其他 |
| `enabled` | 是否启用 |
| `sort_no` | 排序 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

主要关联：

```text
app_user.dept_id
project.dept_id
```

---

### 3.2 `app_user` 用户表

用途：

```text
保存系统用户，包括项目负责人、二级单位管理员、科技处管理员、财务人员、专家等。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 用户 ID |
| `username` | 登录名 |
| `password_hash` | 密码哈希 |
| `real_name` | 真实姓名 |
| `dept_id` | 所属部门 |
| `phone` | 手机号 |
| `email` | 邮箱 |
| `enabled` | 是否启用 |
| `review_status` | 注册审核状态 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

主要关联：

```text
user_role.user_id
project.leader_user_id
project_member.user_id
state_record_remark.participant_user_id
task_instance.assignee_user_id
```

---

### 3.3 `role` 角色表

用途：

```text
保存系统角色。
```

典型角色：

```text
PROJECT_LEADER
DEPT_ADMIN
SCIENCE_OFFICE_ADMIN
FINANCE_ADMIN
EXPERT
SYSTEM_ADMIN
```

核心字段：

| 字段 | 说明 |
|---|---|
| `role_id` | 角色 ID |
| `role_code` | 角色编码 |
| `role_name` | 角色名称 |
| `role_type` | 系统角色 / 业务角色 |
| `enabled` | 是否启用 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 3.4 `permission` 权限表

用途：

```text
保存接口、菜单、按钮权限。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `permission_id` | 权限 ID |
| `permission_code` | 权限编码 |
| `permission_name` | 权限名称 |
| `permission_type` | MENU / BUTTON / API |
| `parent_permission_id` | 上级权限 |
| `enabled` | 是否启用 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 3.5 `user_role` 用户角色关联表

用途：

```text
实现用户与角色的多对多关系。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `user_role_id` | 主键 |
| `user_id` | 用户 ID |
| `role_id` | 角色 ID |
| `created_at` | 创建时间 |

唯一约束：

```text
(user_id, role_id)
```

---

### 3.6 `role_permission` 角色权限关联表

用途：

```text
实现角色与权限的多对多关系。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `role_permission_id` | 主键 |
| `role_id` | 角色 ID |
| `permission_id` | 权限 ID |
| `created_at` | 创建时间 |

唯一约束：

```text
(role_id, permission_id)
```

---

## 4. BPMN 流程定义表

---

### 4.1 `workflow_definition` 流程定义表

用途：

```text
保存 BPMN 流程定义、流程版本、流程类型和发布状态。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `workflow_definition_id` | 流程定义 ID |
| `workflow_code` | 流程编码 |
| `workflow_name` | 流程名称 |
| `module_type` | APPLICATION / CONTRACT / ACCEPTANCE |
| `version_no` | 版本号 |
| `bpmn_xml` | BPMN XML 原文 |
| `state_machine_rules_json` | 状态机规则快照 |
| `status` | DRAFT / ACTIVE / DISABLED |
| `published_by` | 发布人 |
| `published_at` | 发布时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

说明：

```text
第一版仍可保留 state_machine_rules_json，作为 BPMN 发布后的规则快照；
运行时不依赖前端状态，而是以后端发布配置为准。
```

---

### 4.2 `workflow_node` 流程节点表

用途：

```text
保存 BPMN 节点对应的状态、候选角色、操作模式、外部代录角色等配置。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `workflow_node_id` | 节点配置 ID |
| `workflow_definition_id` | 流程定义 ID |
| `bpmn_node_id` | BPMN 节点 ID |
| `node_name` | 节点名称 |
| `node_type` | START / USER_TASK / SERVICE_TASK / EXCLUSIVE_GATEWAY / END |
| `state_code` | 对应状态编码 |
| `candidate_role_code` | 候选处理角色 |
| `responsible_actor_type` | INTERNAL / EXTERNAL |
| `responsible_actor_name` | 责任主体名称 |
| `operation_mode` | USER_OPERATE / SYSTEM_AUTO / PROXY_REGISTER |
| `represented_actor_name` | 代录代表的外部主体 |
| `sort_no` | 排序 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 4.3 `workflow_node_material_requirement` 节点材料要求表

用途：

```text
定义某个节点完成前需要哪些材料。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `requirement_id` | 主键 |
| `workflow_node_id` | 节点 ID |
| `material_type_id` | 材料类型 |
| `required` | 是否必填 |
| `min_count` | 最少数量 |
| `max_count` | 最大数量 |
| `requirement_desc` | 材料要求说明 |
| `sort_no` | 排序 |

---

### 4.4 `workflow_node_document_config` 节点单据配置表

用途：

```text
定义某节点结束后需要生成哪些正式单据。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `document_config_id` | 主键 |
| `workflow_node_id` | 节点 ID |
| `document_type_code` | 单据类型编码 |
| `document_name` | 单据名称 |
| `generate_timing` | NODE_END / PROCESS_END |
| `snapshot_schema_json` | 单据快照结构 |
| `required` | 是否必须生成 |
| `sort_no` | 排序 |

说明：

```text
snapshot_schema_json 只描述单据快照结构，不替代业务数据表。
```

---

## 5. 项目业务表

---

### 5.1 `project` 项目主表

用途：

```text
保存项目基础信息，是申报、合同、结题三个模块的共同主表。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `project_id` | 项目 ID |
| `project_code` | 项目编号 |
| `project_name` | 项目名称 |
| `project_type` | 项目类型 |
| `project_level` | 项目级别 |
| `dept_id` | 所属部门 |
| `leader_user_id` | 项目负责人 |
| `approved_amount` | 批准经费 |
| `start_date` | 开始日期 |
| `end_date` | 结束日期 |
| `lifecycle_stage` | 当前生命周期阶段 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.2 `project_member` 项目成员表

用途：

```text
保存项目成员和分工。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `project_member_id` | 主键 |
| `project_id` | 项目 ID |
| `user_id` | 用户 ID |
| `member_name` | 成员姓名 |
| `member_role` | 负责人 / 成员 / 财务联系人 |
| `responsibility` | 分工说明 |
| `sort_no` | 排序 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.3 `project_application` 项目申报主表

用途：

```text
保存项目申报模块核心字段。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `application_id` | 申报 ID |
| `project_id` | 项目 ID |
| `application_title` | 申报标题 |
| `application_summary` | 申报摘要 |
| `is_limited_project` | 是否限项项目 |
| `application_status` | 申报业务状态 |
| `submitted_at` | 提交时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

说明：

```text
稳定扩展字段进入 project_application_ext；
申请书详细在线字段进入 project_application_detail；
公示字段进入 project_application_publicity。
```

---

### 5.4 `project_application_ext` 项目申报扩展表

用途：

```text
保存项目申报流程中的稳定扩展字段，替代原 payload_json 中的申报批次、推荐排序、主管部门批复等字段。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `application_ext_id` | 主键 |
| `application_id` | 申报 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 ID |
| `application_category` | 申报类别 |
| `application_batch_no` | 申报批次号 |
| `application_notice_id` | 申报通知 ID |
| `application_notice_no` | 申报通知编号 |
| `is_limited_project` | 是否限项 |
| `limit_group` | 限项分组 |
| `expected_budget` | 预期预算 |
| `expected_start_date` | 预期开始时间 |
| `expected_end_date` | 预期结束时间 |
| `dept_recommend_rank` | 院级推荐排名 |
| `dept_recommend_score` | 院级推荐得分 |
| `dept_recommend_result` | 院级推荐结果 |
| `science_recommend_rank` | 校级推荐排名 |
| `science_recommend_score` | 校级推荐得分 |
| `science_recommend_result` | 校级推荐结果 |
| `authority_approval_no` | 主管部门批复文号 |
| `authority_approval_date` | 主管部门批复日期 |
| `authority_result` | 主管部门结果 |
| `authority_approved_amount` | 主管部门批准金额 |
| `final_submission_no` | 最终上报编号 |
| `final_submission_at` | 最终上报时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

唯一约束：

```text
application_id
```

---

### 5.5 `project_application_detail` 项目申请书详细内容表

用途：

```text
保存在线填写的项目申请书详细内容。
如果第一版申请书完全通过文件上传，该表可以暂缓。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `application_detail_id` | 主键 |
| `application_id` | 申报 ID |
| `project_id` | 项目 ID |
| `research_background` | 研究背景 |
| `research_objective` | 研究目标 |
| `research_content` | 研究内容 |
| `innovation_points` | 创新点 |
| `technical_route` | 技术路线 |
| `schedule_plan` | 阶段计划 |
| `budget_description` | 经费预算说明 |
| `expected_outcomes` | 预期成果 |
| `feasibility_analysis` | 可行性分析 |
| `risk_analysis` | 风险分析 |
| `applicant_commitment` | 申请人承诺 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.6 `project_application_publicity` 项目申报公示表

用途：

```text
保存科技处择优推荐后的公示信息、异议处理和最终公示结果。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `publicity_id` | 公示 ID |
| `application_id` | 申报 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `publicity_title` | 公示标题 |
| `publicity_scope` | 公示范围 |
| `publicity_start_date` | 公示开始日期 |
| `publicity_end_date` | 公示结束日期 |
| `recommended_rank` | 推荐排名 |
| `recommended_reason` | 推荐理由 |
| `has_objection` | 是否有异议 |
| `objection_content` | 异议内容 |
| `objection_handling_result` | 异议处理结果 |
| `objection_handling_comment` | 异议处理说明 |
| `publicity_result` | 公示结果 |
| `confirmed_by` | 确认人 |
| `confirmed_at` | 确认时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.7 `project_contract` 纵向合同主表

用途：

```text
保存纵向项目合同核心字段。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `contract_id` | 合同 ID |
| `project_id` | 项目 ID |
| `contract_code` | 合同编号 |
| `contract_name` | 合同名称 |
| `contract_amount` | 合同金额 |
| `contract_start_date` | 合同开始日期 |
| `contract_end_date` | 合同结束日期 |
| `contract_status` | 合同状态 |
| `seal_status` | 盖章状态 |
| `signed_at` | 签署时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.8 `project_contract_ext` 纵向合同扩展表

用途：

```text
保存合同甲乙方、外部审核、签字盖章、归档等稳定扩展字段。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `contract_ext_id` | 主键 |
| `contract_id` | 合同 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `contract_source` | 合同来源 |
| `party_a_name` | 甲方名称 |
| `party_a_contact` | 甲方联系人 |
| `party_a_phone` | 甲方电话 |
| `party_b_name` | 乙方名称 |
| `party_b_contact` | 乙方联系人 |
| `party_b_phone` | 乙方电话 |
| `authority_review_result` | 主管部门审核结果 |
| `authority_review_date` | 主管部门审核日期 |
| `authority_review_opinion` | 主管部门审核意见 |
| `leader_signed_at` | 负责人签字时间 |
| `school_sealed_at` | 学校盖章时间 |
| `authority_sealed_at` | 主管部门盖章时间 |
| `effective_date` | 合同生效日期 |
| `archive_no` | 归档编号 |
| `archive_location` | 归档位置 |
| `archive_copies` | 归档份数 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.9 `project_acceptance` 项目结题主表

用途：

```text
保存项目结题核心字段。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `acceptance_id` | 结题 ID |
| `project_id` | 项目 ID |
| `acceptance_title` | 结题标题 |
| `acceptance_status` | 结题状态 |
| `conclusion` | 结题结论 |
| `certificate_no` | 结题证书编号 |
| `submitted_at` | 提交时间 |
| `completed_at` | 完成时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.10 `project_acceptance_ext` 项目结题扩展表

用途：

```text
保存结题扩展字段，替代原 payload_json 中的任务完成率、成果统计、专家最终结果、结余经费状态等。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `acceptance_ext_id` | 主键 |
| `acceptance_id` | 结题 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `is_school_level_acceptance` | 是否校级结题 |
| `acceptance_type` | 结题类型 |
| `acceptance_batch_no` | 结题批次号 |
| `task_completion_rate` | 任务完成率 |
| `paper_count` | 论文数量 |
| `patent_count` | 专利数量 |
| `software_copyright_count` | 软件著作权数量 |
| `other_achievement_count` | 其他成果数量 |
| `science_review_result` | 科技处审核结果 |
| `authority_review_result` | 主管部门审核结果 |
| `authority_review_date` | 主管部门审核日期 |
| `authority_file_no` | 主管部门文件编号 |
| `expert_final_score` | 专家最终得分 |
| `expert_final_result` | 专家最终结论 |
| `certificate_no` | 证书编号 |
| `certificate_issue_date` | 证书发放日期 |
| `surplus_amount` | 结余金额 |
| `surplus_return_required` | 是否需要退还 |
| `surplus_return_status` | 退还状态 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.11 `acceptance_financial_settlement` 结题经费决算表

用途：

```text
保存结题时的经费决算信息。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `settlement_id` | 决算 ID |
| `acceptance_id` | 结题 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `approved_amount` | 批准经费 |
| `received_amount` | 到账经费 |
| `spent_amount` | 已支出经费 |
| `surplus_amount` | 结余经费 |
| `execution_rate` | 经费执行率 |
| `settlement_result` | 决算结果 |
| `finance_operator_id` | 财务经办人 |
| `finance_review_comment` | 财务审核意见 |
| `settled_at` | 决算时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 5.12 `project_achievement` 项目成果表

用途：

```text
保存结题阶段提交的成果清单。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `achievement_id` | 成果 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `acceptance_id` | 结题 ID |
| `achievement_type` | 成果类型 |
| `achievement_title` | 成果名称 |
| `author_list` | 作者列表 |
| `achievement_level` | 成果级别 |
| `publish_or_grant_date` | 发表或授权日期 |
| `proof_material_version_id` | 佐证材料版本 ID |
| `remark` | 备注 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

典型 `achievement_type`：

```text
PAPER
PATENT
SOFTWARE_COPYRIGHT
AWARD
STANDARD
REPORT
OTHER
```

---

### 5.13 `surplus_funds_return_record` 结余经费退还记录表

用途：

```text
保存结题后结余经费退还信息。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `return_id` | 主键 |
| `acceptance_id` | 结题 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `surplus_amount` | 结余金额 |
| `return_required` | 是否需要退还 |
| `return_account_name` | 退还账户名 |
| `return_account_no` | 退还账号 |
| `return_bank_name` | 开户行 |
| `return_status` | 退还状态 |
| `returned_amount` | 已退金额 |
| `returned_at` | 退还时间 |
| `finance_operator_id` | 财务经办人 |
| `remark` | 备注 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

## 6. 状态机运行时表

---

### 6.1 `project_module_instance` 项目模块实例表

用途：

```text
表示一个项目的一次业务流程实例，例如项目申报实例、合同实例、结题实例。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `module_instance_id` | 模块实例 ID |
| `project_id` | 项目 ID |
| `module_type` | APPLICATION / CONTRACT / ACCEPTANCE |
| `workflow_definition_id` | 使用的流程定义 |
| `business_id` | 对应业务表 ID |
| `module_status` | RUNNING / FINISHED / CANCELED |
| `started_by` | 发起人 |
| `started_at` | 发起时间 |
| `finished_at` | 完成时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

说明：

```text
当前状态不建议直接冗余在该表中，而由 module_state_record 最新记录或视图计算。
```

---

### 6.2 `module_state_record` 模块状态记录表

用途：

```text
记录模块实例的状态迁移事实，只追加，不修改。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `state_record_id` | 状态记录 ID |
| `module_instance_id` | 模块实例 ID |
| `workflow_definition_id` | 流程定义 ID |
| `from_node_id` | 来源节点 |
| `to_node_id` | 目标节点 |
| `from_state` | 来源状态 |
| `to_state` | 目标状态 |
| `event_type` | 触发事件 |
| `seq` | 模块内递增序号 |
| `round_no` | 轮次 |
| `transition_result` | SUCCESS / FAILED |
| `summary` | 操作摘要 |
| `payload_json` | V2 中仅作临时上下文 / 原始请求摘要 |
| `created_at` | 创建时间 |

V2 对 `payload_json` 的约束：

```text
允许保存：
- 原始请求摘要；
- 前端临时状态；
- 调试信息；
- 非核心字段；
- 尚未结构化的过渡字段。

不允许长期保存：
- 审核检查项；
- 外部文号；
- 批准金额；
- 公示起止时间；
- 用印份数；
- 经费金额；
- 成果数量；
- 专家评分；
- 证书编号；
- 归档位置；
- 上报编号。
```

---

### 6.3 `state_record_remark` 状态记录意见表

用途：

```text
保存某次状态迁移相关的人、角色、操作意见、审核结论和文字说明。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `remark_id` | 意见 ID |
| `state_record_id` | 状态记录 ID |
| `participant_type` | USER / EXPERT / PROXY_OPERATOR / SYSTEM |
| `participant_user_id` | 参与人 ID |
| `participant_role_code` | 参与角色 |
| `action_type` | SUBMIT / APPROVE / RETURN / REGISTER_RESULT |
| `result` | APPROVED / REJECTED / RETURNED / PASSED |
| `remark_content` | 意见内容 |
| `is_operator` | 是否为实际触发状态迁移的人 |
| `is_final` | 是否最终有效 |
| `created_at` | 创建时间 |

---

### 6.4 `state_record_material` 状态记录材料关联表

用途：

```text
记录某次状态迁移关联了哪些材料版本。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `record_material_id` | 主键 |
| `state_record_id` | 状态记录 ID |
| `material_version_id` | 材料版本 ID |
| `material_type_id` | 材料类型 ID |
| `link_type` | SUBMITTED / GENERATED / REVIEW_ATTACHMENT |
| `created_at` | 创建时间 |

---

### 6.5 `state_record_check_item` 状态记录检查项表

用途：

```text
结构化保存审核节点中的检查项，替代 payload_json。
```

适用示例：

```text
申请书是否完整；
附件是否齐全；
预算是否合理；
合同内容是否与立项一致；
知识产权条款是否存在风险；
结题材料是否完整；
成果真实性是否确认；
经费决算是否完成。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `check_item_id` | 检查项 ID |
| `state_record_id` | 状态记录 ID |
| `module_instance_id` | 模块实例 |
| `node_id` | 节点 ID |
| `state_code` | 状态编码 |
| `item_code` | 检查项编码 |
| `item_name` | 检查项名称 |
| `item_type` | BOOLEAN / TEXT / NUMBER / ENUM |
| `item_value` | 检查项值 |
| `item_result` | PASS / FAIL / WARNING |
| `required` | 是否必查 |
| `passed` | 是否通过 |
| `remark` | 检查项说明 |
| `sort_no` | 排序 |
| `created_at` | 创建时间 |

---

## 7. 通用流程业务记录表

---

### 7.1 `notice_record` 通知发布记录表

用途：

```text
结构化保存申报通知、结题通知，替代通知节点中的 payload_json。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `notice_id` | 通知 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `notice_type` | 通知类型 |
| `notice_title` | 通知标题 |
| `notice_no` | 通知编号 |
| `publish_unit` | 发布单位 |
| `publish_user_id` | 发布人 |
| `publish_time` | 发布时间 |
| `notice_scope` | 通知范围 |
| `target_dept_scope` | 面向部门范围 |
| `target_user_scope` | 面向用户范围 |
| `project_category` | 项目类别 |
| `is_limited_project` | 是否限项 |
| `limit_count` | 限项数量 |
| `start_time` | 开始时间 |
| `deadline_time` | 截止时间 |
| `material_requirement_summary` | 材料要求摘要 |
| `content_summary` | 通知内容摘要 |
| `remark` | 备注 |

---

### 7.2 `external_result_record` 外部结果登记表

用途：

```text
保存主管部门或第三方机构的审核、批复、盖章、生效等结果。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `external_result_id` | 外部结果 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `result_type` | 结果类型 |
| `external_actor_code` | 外部主体编码 |
| `external_actor_name` | 外部主体名称 |
| `external_result` | 外部结果 |
| `external_result_date` | 外部结果日期 |
| `external_file_no` | 外部文号 |
| `external_system_no` | 外部系统编号 |
| `approved_amount` | 批准金额 |
| `effective_date` | 生效日期 |
| `summary` | 外部意见摘要 |
| `registered_by` | 代录人 |
| `registered_at` | 代录时间 |

典型 `result_type`：

```text
APPLICATION_AUTHORITY_REVIEW
CONTRACT_AUTHORITY_REVIEW
CONTRACT_AUTHORITY_SEAL
ACCEPTANCE_AUTHORITY_REVIEW
```

---

### 7.3 `seal_record` 签字盖章 / 用印记录表

用途：

```text
保存签字盖章、用印份数、用印状态等结构化信息。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `seal_record_id` | 用印记录 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `seal_subject` | 用印对象 |
| `seal_type` | 用印类型 |
| `seal_reason` | 用印原因 |
| `copy_count` | 份数 |
| `applicant_user_id` | 申请人 |
| `handled_by` | 经办人 |
| `leader_signed` | 负责人是否签字 |
| `leader_signed_at` | 负责人签字时间 |
| `school_sealed` | 学校是否盖章 |
| `school_sealed_at` | 学校盖章时间 |
| `external_sealed` | 外部主体是否盖章 |
| `external_actor_name` | 外部主体名称 |
| `external_sealed_at` | 外部盖章时间 |
| `seal_status` | 用印状态 |
| `remark` | 备注 |

---

### 7.4 `submission_record` 材料上报记录表

用途：

```text
保存材料上报对象、方式、编号、回执等信息。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `submission_id` | 上报 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `submission_type` | 上报类型 |
| `target_actor_code` | 上报对象编码 |
| `target_actor_name` | 上报对象名称 |
| `submission_method` | 上报方式 |
| `submission_no` | 上报编号 |
| `external_system_no` | 外部系统编号 |
| `receipt_no` | 回执编号 |
| `submitted_by` | 上报人 |
| `submitted_at` | 上报时间 |
| `material_summary` | 材料摘要 |
| `remark` | 备注 |

---

### 7.5 `archive_record` 归档记录表

用途：

```text
保存归档编号、归档位置、归档份数、归档状态。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `archive_id` | 归档 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `archive_type` | 归档类型 |
| `archive_no` | 档案编号 |
| `archive_location` | 归档位置 |
| `paper_copy_count` | 纸质份数 |
| `electronic_copy_count` | 电子份数 |
| `archived_by` | 归档人 |
| `archived_at` | 归档时间 |
| `archive_status` | 归档状态 |
| `remark` | 备注 |

---

## 8. 材料与单据表

---

### 8.1 `material_type` 材料类型表

用途：

```text
定义系统中的材料类型。
```

示例：

```text
PROJECT_APPLICATION_FORM
BUDGET_FORM
CONTRACT_DRAFT
SIGNED_CONTRACT
AUTHORITY_APPROVAL_FILE
ACCEPTANCE_REPORT
FINANCIAL_SETTLEMENT_FORM
ACCEPTANCE_CERTIFICATE
```

核心字段：

| 字段 | 说明 |
|---|---|
| `material_type_id` | 材料类型 ID |
| `material_type_code` | 材料类型编码 |
| `material_type_name` | 材料类型名称 |
| `module_type` | 所属模块 |
| `description` | 描述 |
| `enabled` | 是否启用 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 8.2 `material` 材料主表

用途：

```text
表示项目下的一个材料对象。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `material_id` | 材料 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `material_type_id` | 材料类型 |
| `material_name` | 材料名称 |
| `current_version_id` | 当前版本 |
| `material_status` | NORMAL / DELETED |
| `created_by` | 创建人 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

---

### 8.3 `material_version` 材料版本表

用途：

```text
保存材料文件的具体版本。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `material_version_id` | 材料版本 ID |
| `material_id` | 材料 ID |
| `version_no` | 版本号 |
| `file_name` | 文件名 |
| `file_path` | 文件路径 |
| `file_size` | 文件大小 |
| `file_hash` | 文件哈希 |
| `mime_type` | 文件类型 |
| `uploaded_by` | 上传人 |
| `uploaded_at` | 上传时间 |
| `is_current` | 是否当前版本 |
| `remark` | 备注 |

---

### 8.4 `process_document` 流程单据表

用途：

```text
保存正式单据快照，例如项目申报审批单、合同归档单、结题验收汇总单、验收证书等。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `document_id` | 单据 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `document_type_code` | 单据类型 |
| `document_name` | 单据名称 |
| `document_no` | 单据编号 |
| `snapshot_json` | 单据快照 |
| `document_status` | DRAFT / GENERATED / ARCHIVED |
| `generated_by` | 生成人 |
| `generated_at` | 生成时间 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

说明：

```text
snapshot_json 是正式单据快照，不是业务数据主存储。
其作用是在节点或流程结束时固化当时的数据，避免后续业务数据变化导致历史单据漂移。
```

---

### 8.5 `process_document_file` 流程单据文件表

用途：

```text
关联正式单据与实际文件。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `document_file_id` | 主键 |
| `document_id` | 单据 ID |
| `material_version_id` | 材料版本 ID |
| `file_role` | MAIN / ATTACHMENT |
| `is_main_file` | 是否主文件 |
| `created_at` | 创建时间 |

---

## 9. 待办与专家评审表

---

### 9.1 `task_instance` 待办任务表

用途：

```text
保存当前待办任务。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `task_id` | 待办 ID |
| `module_instance_id` | 模块实例 |
| `workflow_node_id` | 当前节点 |
| `state_code` | 当前状态 |
| `task_name` | 任务名称 |
| `candidate_role_code` | 候选角色 |
| `assignee_user_id` | 实际处理人 |
| `task_status` | PENDING / CLAIMED / COMPLETED / CANCELED |
| `created_at` | 创建时间 |
| `claimed_at` | 领取时间 |
| `completed_at` | 完成时间 |

说明：

```text
task_instance 表示当前待办；
module_state_record 表示历史事实；
二者职责不同，不应混用。
```

---

### 9.2 `expert_review_batch` 专家评审批次表

用途：

```text
保存一次专家评审或专家验收的汇总信息。
```

适用场景：

```text
二级单位专家评审
科技处专家评审
结题专家验收
```

核心字段：

| 字段 | 说明 |
|---|---|
| `batch_id` | 批次 ID |
| `module_instance_id` | 模块实例 |
| `workflow_node_id` | 流程节点 |
| `state_record_id` | 状态记录 |
| `review_type` | 评审类型 |
| `review_title` | 评审标题 |
| `rule_type` | 汇总规则 |
| `min_expert_count` | 最少专家数 |
| `pass_score` | 通过分数线 |
| `recommend_score` | 推荐分数线 |
| `remove_highest_lowest` | 是否去最高最低 |
| `expected_expert_count` | 应评专家数 |
| `submitted_expert_count` | 已提交专家数 |
| `valid_expert_count` | 有效专家数 |
| `highest_score` | 最高分 |
| `lowest_score` | 最低分 |
| `final_score` | 最终得分 |
| `final_result` | 最终结果 |
| `rank_no` | 排名 |
| `summary_comment` | 汇总意见 |
| `status` | 状态 |
| `created_by` | 创建人 |
| `created_at` | 创建时间 |
| `completed_at` | 完成时间 |

典型 `rule_type`：

```text
AVERAGE
REMOVE_HIGHEST_LOWEST_AVERAGE
MAJORITY_PASS
WEIGHTED_AVERAGE
```

---

### 9.3 `expert_review_assignment` 专家分配表

用途：

```text
记录某位专家被分配到某个评审批次，以及该专家的总分、结论和意见。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `assignment_id` | 分配 ID |
| `batch_id` | 批次 ID |
| `expert_user_id` | 专家用户 ID |
| `expert_name` | 专家姓名 |
| `expert_org` | 专家单位 |
| `expert_title` | 专家职称 |
| `assigned_at` | 分配时间 |
| `submitted_at` | 提交时间 |
| `review_status` | 评审状态 |
| `conflict_of_interest` | 是否回避 |
| `is_valid` | 是否有效 |
| `total_score` | 总分 |
| `review_result` | 专家结论 |
| `review_comment` | 专家意见 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

唯一约束：

```text
(batch_id, expert_user_id)
```

---

### 9.4 `expert_review_score` 专家评分项表

用途：

```text
保存每位专家对每个评分项的得分。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `score_id` | 评分项 ID |
| `assignment_id` | 专家分配 ID |
| `score_item_code` | 评分项编码 |
| `score_item_name` | 评分项名称 |
| `weight` | 权重 |
| `max_score` | 最高分 |
| `score_value` | 实际得分 |
| `comment` | 该项说明 |
| `created_at` | 创建时间 |

---

## 10. 视图清单

---

### 10.1 `v_user_role_detail`

用途：

```text
查询用户拥有的角色、部门和权限上下文。
```

建议聚合：

```text
app_user
department
user_role
role
```

---

### 10.2 `v_workflow_node_config`

用途：

```text
查询流程节点配置，包括节点基本信息、候选角色、材料要求、单据配置。
```

建议聚合：

```text
workflow_definition
workflow_node
workflow_node_material_requirement
workflow_node_document_config
```

---

### 10.3 `v_project_module_current_state`

用途：

```text
查询每个模块实例的最新状态。
```

建议基于：

```text
module_state_record
```

按：

```text
module_instance_id + 最大 seq
```

计算。

---

### 10.4 `v_module_runtime_context`

用途：

```text
状态机运行时上下文视图。
```

建议聚合：

```text
project_module_instance
project
workflow_definition
workflow_node
module_state_record 最新记录
task_instance 当前待办
```

---

### 10.5 `v_state_record_context`

用途：

```text
查询流程历史时间线。
```

建议聚合：

```text
module_state_record
state_record_remark
state_record_material
app_user
role
workflow_node
```

---

### 10.6 `v_material_context`

用途：

```text
查询材料清单和版本信息。
```

建议聚合：

```text
material_type
material
material_version
app_user
```

---

## 11. payload_json 在 V2 中的保留边界

V2 仍可在 `module_state_record` 中保留 `payload_json` 字段，但需要明确使用边界。

### 11.1 允许保存

```text
1. 原始请求摘要；
2. 前端临时状态；
3. 调试信息；
4. 非业务关键字段；
5. 尚未结构化的过渡字段；
6. 状态机执行过程中的临时变量。
```

### 11.2 不建议长期保存

```text
1. 审核检查项；
2. 是否通过；
3. 外部文号；
4. 外部审核结果；
5. 批准金额；
6. 用印份数；
7. 公示起止时间；
8. 经费金额；
9. 成果数量；
10. 专家评分；
11. 证书编号；
12. 归档位置；
13. 上报编号。
```

这些字段在 V2 中应进入结构化表。

---

## 12. 第一版落地优先级

### 12.1 P0：状态机闭环必须表

```text
project
project_module_instance
module_state_record
state_record_remark
state_record_material
task_instance
material_type
material
material_version
workflow_definition
workflow_node
workflow_node_material_requirement
workflow_node_document_config
```

### 12.2 P1：替代 payload 的核心结构化表

```text
state_record_check_item
external_result_record
seal_record
submission_record
expert_review_batch
expert_review_assignment
expert_review_score
```

### 12.3 P2：三大业务模块扩展表

```text
project_application_ext
project_contract_ext
project_acceptance_ext
acceptance_financial_settlement
project_achievement
```

### 12.4 P3：通知、公示、归档、结余经费等增强表

```text
notice_record
project_application_publicity
archive_record
surplus_funds_return_record
```

### 12.5 P4：在线申请书细粒度字段

```text
project_application_detail
```

如果第一版申请书主要走文件上传，`project_application_detail` 可以暂缓。

---

## 13. 最终建议

V2 数据表设计可以概括为：

```text
权限组织：department / app_user / role / permission
流程定义：workflow_definition / workflow_node / workflow_node_*
项目业务：project / project_application / project_contract / project_acceptance
业务扩展：project_application_ext / project_contract_ext / project_acceptance_ext
运行时：project_module_instance / module_state_record / state_record_*
通用记录：notice_record / external_result_record / seal_record / submission_record / archive_record
材料单据：material_* / process_document_*
专家评审：expert_review_*
待办任务：task_instance
```

这套设计相比 V1 的主要改进是：

```text
1. 大幅减少 payload_json 中的正式业务字段；
2. 保留虚拟表单的组装能力；
3. 保留 process_document 的正式单据快照能力；
4. 增强查询、统计、导出和字段校验能力；
5. 避免每个节点单独建一张专用表导致表爆炸；
6. 保持状态机运行时、业务数据、审核意见、材料版本之间职责清晰。
```
