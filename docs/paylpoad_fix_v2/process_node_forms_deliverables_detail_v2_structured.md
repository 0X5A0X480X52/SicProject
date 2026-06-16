# 三类科研项目流程节点表单与交付物细化设计 V2：结构化表版本

## 1. V2 修订目标

V1 文档中大量节点字段使用了 `payload_json` 作为灵活上下文，例如通知标题、审核检查项、外部文号、公示时间、用印份数、上报方式、经费金额、成果数量等。V2 的目标是：**尽可能将这些稳定字段转化为明确的数据表和字段**，让 `payload_json` 只保留为临时上下文、调试信息和非核心兜底字段。

当前已有主干表继续保留：

```text
project
project_application
project_contract
project_acceptance
project_module_instance
module_state_record
state_record_remark
state_record_material
material_type
material
material_version
task_instance
process_document
process_document_file
```

V2 新增结构化表：

```text
notice_record
state_record_check_item
external_result_record
seal_record
submission_record
archive_record

project_application_ext
project_application_detail
project_application_publicity

project_contract_ext

project_acceptance_ext
acceptance_financial_settlement
project_achievement
surplus_funds_return_record

expert_review_batch
expert_review_assignment
expert_review_score
```

---

## 2. V2 数据分层

| 数据类型 | V1 可能存储位置 | V2 推荐存储位置 |
|---|---|---|
| 项目基础信息 | `project` | `project` |
| 项目申报核心信息 | `project_application` | `project_application` |
| 项目申报扩展字段 | `payload_json` | `project_application_ext` |
| 申请书详细在线字段 | `payload_json` / 文件 | `project_application_detail` |
| 申请公示信息 | `payload_json` | `project_application_publicity` |
| 合同扩展字段 | `payload_json` | `project_contract_ext` |
| 结题扩展字段 | `payload_json` | `project_acceptance_ext` |
| 经费决算 | `payload_json` | `acceptance_financial_settlement` |
| 成果清单 | `payload_json` | `project_achievement` |
| 结余经费退还 | `payload_json` | `surplus_funds_return_record` |
| 审核检查项 | `payload_json` | `state_record_check_item` |
| 外部审核 / 批复 / 回执 | `payload_json` | `external_result_record` |
| 签字盖章 / 用印 | `payload_json` | `seal_record` |
| 上报记录 | `payload_json` | `submission_record` |
| 归档记录 | `payload_json` | `archive_record` |
| 通知发布 | `payload_json` | `notice_record` |
| 专家评审 | `payload_json` / `state_record_remark` | `expert_review_*` |
| 审核意见文本 | `state_record_remark` | `state_record_remark` |
| 文件材料 | `material_version` | `material_version` |
| 正式单据快照 | `process_document.snapshot_json` | `process_document.snapshot_json` |

---

## 3. 通用结构化表设计

### 3.1 `notice_record`：通知发布记录表

用于替代通知节点中的 `payload_json` 字段：

```text
通知标题、通知编号、通知类型、通知对象、限项数量、起止时间、截止时间、材料要求说明。
```

适用节点：

```text
项目申请：发布申报通知
项目结题：发布结题验收通知
```

核心字段：

| 字段 | 说明 |
|---|---|
| `module_instance_id` | 模块实例 |
| `state_record_id` | 对应状态记录 |
| `module_type` | APPLICATION / ACCEPTANCE |
| `notice_type` | APPLICATION_NOTICE / ACCEPTANCE_NOTICE |
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

附件仍通过 `material_version` + `state_record_material` 管理。

---

### 3.2 `state_record_check_item`：状态记录检查项表

用于替代所有审核节点中的检查项 `payload_json`。

适用场景：

```text
二级单位形式审核
科技处初审
合同二级单位审核
合同科技处审核
结题二级单位审核
结题科技处审核
```

核心字段：

| 字段 | 说明 |
|---|---|
| `state_record_id` | 对应状态迁移 |
| `module_instance_id` | 模块实例 |
| `node_id` | BPMN 节点 ID |
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

示例：

| item_code | item_name | item_type | item_value | passed |
|---|---|---|---|---|
| `APPLICATION_COMPLETE` | 申请书是否完整 | `BOOLEAN` | `true` | `true` |
| `BUDGET_REASONABLE` | 经费预算是否合理 | `BOOLEAN` | `false` | `false` |
| `CONTRACT_IP_RISK` | 知识产权条款是否存在风险 | `BOOLEAN` | `true` | `false` |

审核结论仍然放在 `state_record_remark.result`，退回原因仍然放在 `state_record_remark.remark_content`。

---

### 3.3 `external_result_record`：外部结果登记表

用于替代外部主体代录节点中的 `payload_json`：

```text
外部审核时间、外部文号、外部结果、批准经费、外部系统编号、生效日期。
```

适用节点：

```text
项目申请：主管部门或第三方机构审核结果登记
纵向合同：主管部门或专业机构审核
纵向合同：主管部门或专业机构盖章
项目结题：主管部门或第三方机构审核
```

核心字段：

| 字段 | 说明 |
|---|---|
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `result_type` | 外部结果类型 |
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

---

### 3.4 `seal_record`：签字盖章 / 用印记录表

用于替代用印节点中的 `payload_json`：

```text
用印原因、用印类型、用印份数、负责人签字状态、学校盖章状态、外部盖章状态。
```

适用节点：

```text
项目申请：打印申请书、签字并申请用章
纵向合同：项目负责人签字
纵向合同：学校盖章
纵向合同：主管部门盖章
项目结题：打印结题材料签字并申请用章
```

核心字段：

| 字段 | 说明 |
|---|---|
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `seal_subject` | 用印对象，如 APPLICATION_FORM / CONTRACT / ACCEPTANCE_REPORT |
| `seal_type` | 学校章 / 合同章 / 部门章 |
| `seal_reason` | 用印原因 |
| `copy_count` | 份数 |
| `applicant_user_id` | 申请人 |
| `handled_by` | 经办人 |
| `leader_signed` | 负责人是否签字 |
| `leader_signed_at` | 负责人签字时间 |
| `school_sealed` | 学校是否盖章 |
| `school_sealed_at` | 学校盖章时间 |
| `external_sealed` | 外部主体是否盖章 |
| `external_actor_name` | 外部盖章主体 |
| `external_sealed_at` | 外部盖章时间 |
| `seal_status` | 用印状态 |
| `remark` | 备注 |

文件仍通过 `material_version` 管理。

---

### 3.5 `submission_record`：材料上报记录表

用于替代上报节点中的 `payload_json`：

```text
上报对象、上报方式、上报编号、外部平台编号、回执编号。
```

适用节点：

```text
项目申请：科技处审核上报
项目申请：报送主管部门或第三方机构
项目结题：报送主管部门或第三方机构
```

核心字段：

| 字段 | 说明 |
|---|---|
| `module_instance_id` | 模块实例 |
| `state_record_id` | 状态记录 |
| `module_type` | 模块类型 |
| `submission_type` | 上报类型 |
| `target_actor_code` | 上报对象编码 |
| `target_actor_name` | 上报对象名称 |
| `submission_method` | 平台 / 邮件 / 线下 |
| `submission_no` | 上报编号 |
| `external_system_no` | 外部系统编号 |
| `receipt_no` | 回执编号 |
| `submitted_by` | 上报人 |
| `submitted_at` | 上报时间 |
| `material_summary` | 材料摘要 |
| `remark` | 备注 |

材料清单通过 `state_record_material` + `material_version` 关联，不在该表重复保存文件路径。

---

### 3.6 `archive_record`：归档记录表

用于替代归档节点中的 `payload_json`：

```text
归档编号、归档位置、纸质份数、电子份数、归档状态。
```

适用节点：

```text
纵向合同：返回科技处、项目负责人存档
后续可扩展到申报归档、结题归档
```

核心字段：

| 字段 | 说明 |
|---|---|
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

## 4. 业务模块扩展表设计

### 4.1 `project_application_ext`：项目申请扩展表

用于承接申报流程中的稳定扩展字段：

```text
申报批次、是否限项、推荐得分、推荐排序、主管部门批复文号、主管部门批准金额、最终上报编号。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `application_id` | 申报 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
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

---

### 4.2 `project_application_detail`：项目申请详细内容表

如果第一版申请书完全以文件上传为主，该表可以暂缓。若要在线填写申请书详细字段，则建议新增。

用于承接：

```text
研究背景、研究目标、研究内容、创新点、技术路线、阶段计划、经费预算说明、预期成果。
```

核心字段：

| 字段 | 说明 |
|---|---|
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

---

### 4.3 `project_application_publicity`：项目申请公示记录表

用于承接：

```text
公示标题、公示范围、公示起止时间、推荐排名、是否有异议、异议处理结果、公示结果。
```

核心字段：

| 字段 | 说明 |
|---|---|
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

---

### 4.4 `project_contract_ext`：纵向合同扩展表

用于承接：

```text
甲方、乙方、主管部门审核结果、负责人签字时间、学校盖章时间、主管部门盖章时间、生效日期、归档编号、归档位置。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `contract_id` | 合同 ID |
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `contract_source` | 合同来源 |
| `party_a_name` | 甲方名称 |
| `party_a_contact` | 甲方联系人 |
| `party_a_phone` | 甲方联系电话 |
| `party_b_name` | 乙方名称 |
| `party_b_contact` | 乙方联系人 |
| `party_b_phone` | 乙方联系电话 |
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

---

### 4.5 `project_acceptance_ext`：项目结题扩展表

用于承接：

```text
是否校级结题、任务完成率、成果数量、主管部门审核结果、专家最终得分、专家最终结论、证书发放日期、结余经费退还状态。
```

核心字段：

| 字段 | 说明 |
|---|---|
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

---

### 4.6 `acceptance_financial_settlement`：结题经费决算表

用于承接：

```text
批准经费、到账经费、支出经费、结余经费、经费执行率、财务决算结果。
```

核心字段：

| 字段 | 说明 |
|---|---|
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

---

### 4.7 `project_achievement`：项目成果清单表

用于承接：

```text
论文、专利、软件著作权、奖励、标准、研究报告等成果清单。
```

核心字段：

| 字段 | 说明 |
|---|---|
| `project_id` | 项目 ID |
| `module_instance_id` | 模块实例 |
| `acceptance_id` | 结题 ID |
| `achievement_type` | 成果类型 |
| `achievement_title` | 成果名称 |
| `author_list` | 作者列表 |
| `achievement_level` | 成果级别 |
| `publish_or_grant_date` | 发表或授权日期 |
| `proof_material_version_id` | 佐证材料版本 |
| `remark` | 备注 |

---

### 4.8 `surplus_funds_return_record`：结余经费退还记录表

用于承接：

```text
结余金额、退还账户、退还状态、退还金额、退还时间。
```

核心字段：

| 字段 | 说明 |
|---|---|
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

---

## 5. 专家评审表设计

专家评审必须结构化，不建议使用 `payload_json`。

### 5.1 `expert_review_batch`

表示一次专家评审批次。

核心字段：

| 字段 | 说明 |
|---|---|
| `module_instance_id` | 模块实例 |
| `workflow_node_id` | 所属流程节点 |
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

`review_type` 示例：

```text
APPLICATION_DEPT_EXPERT
APPLICATION_SCIENCE_EXPERT
ACCEPTANCE_EXPERT
```

`rule_type` 示例：

```text
AVERAGE
REMOVE_HIGHEST_LOWEST_AVERAGE
MAJORITY_PASS
WEIGHTED_AVERAGE
```

---

### 5.2 `expert_review_assignment`

表示某位专家被分配到某个评审批次。

核心字段：

| 字段 | 说明 |
|---|---|
| `batch_id` | 评审批次 |
| `expert_user_id` | 专家用户 |
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

---

### 5.3 `expert_review_score`

表示某位专家对某个评分项的评分。

核心字段：

| 字段 | 说明 |
|---|---|
| `assignment_id` | 专家分配记录 |
| `score_item_code` | 评分项编码 |
| `score_item_name` | 评分项名称 |
| `weight` | 权重 |
| `max_score` | 最高分 |
| `score_value` | 实际得分 |
| `comment` | 该项说明 |

---

## 6. 节点字段替换映射

### 6.1 项目申请流程

| 节点 | V1 payload 字段 | V2 专门表 |
|---|---|---|
| 发布申报通知 | 通知标题、通知编号、通知类型、申报对象、限项数量、截止时间 | `notice_record` |
| 填报申请书 | 研究背景、目标、内容、创新点、预算说明、预期成果 | `project_application_detail` |
| 二级单位形式审核 | 审核检查项、是否通过 | `state_record_check_item` + `state_record_remark.result` |
| 二级单位专家评审 | 推荐排序、专家评分、最终得分 | `expert_review_*` + `project_application_ext` |
| 科技处初审 | 审核检查项、是否需要专家评审、是否通过 | `state_record_check_item` |
| 科技处专家评审 | 专家评分、最终结论 | `expert_review_*` + `project_application_ext` |
| 公示 | 公示标题、范围、起止时间、异议、处理结果 | `project_application_publicity` |
| 科技处上报 | 上报对象、上报方式、平台编号 | `submission_record` |
| 主管部门结果登记 | 文号、日期、结果、批准金额 | `external_result_record` + `project_application_ext` |
| 签字盖章 | 用印原因、份数、签字状态、盖章状态 | `seal_record` |
| 最终报送 | 上报对象、方式、回执编号 | `submission_record` |

---

### 6.2 纵向项目合同流程

| 节点 | V1 payload 字段 | V2 专门表 |
|---|---|---|
| 批准立项 | 批准文号、批准日期、批准经费 | `external_result_record` + `project` |
| 填写合同 | 甲方、乙方、联系方式 | `project_contract_ext` |
| 二级单位审核 | 审核检查项、是否通过 | `state_record_check_item` |
| 科技处审核 | 条款检查、知识产权风险、经费拨付条款 | `state_record_check_item` |
| 主管部门审核 | 外部审核时间、审核结果、修改意见 | `external_result_record` |
| 打印 PDF 合同 | 生成时间、文件哈希 | `material_version` + `module_state_record` |
| 项目负责人签字 | 签字时间 | `seal_record` + `project_contract_ext` |
| 学校盖章 | 用印类型、份数、盖章时间 | `seal_record` + `project_contract_ext` |
| 主管部门盖章 | 外部盖章主体、盖章时间、生效日期 | `external_result_record` + `seal_record` + `project_contract_ext` |
| 合同归档 | 归档份数、归档位置、归档编号 | `archive_record` + `project_contract_ext` |

---

### 6.3 项目结题流程

| 节点 | V1 payload 字段 | V2 专门表 |
|---|---|---|
| 发布结题通知 | 通知标题、编号、对象、截止时间 | `notice_record` |
| 通知项目负责人 | 通知方式、通知对象、通知时间 | `state_record_remark` + 可选 `notice_record` |
| 经费决算 | 到账经费、支出经费、结余经费、执行率 | `acceptance_financial_settlement` |
| 填报结题材料 | 任务完成率、成果数量、成果明细 | `project_acceptance_ext` + `project_achievement` |
| 二级单位审核 | 材料完整性、成果真实性、是否同意 | `state_record_check_item` |
| 科技处审核 | 材料完整性、流程合规性、是否需要专家验收 | `state_record_check_item` |
| 主管部门审核 | 外部日期、文号、结果 | `external_result_record` + `project_acceptance_ext` |
| 签字盖章 | 用印原因、份数、签字盖章状态 | `seal_record` |
| 报送主管部门 | 报送对象、方式、回执编号 | `submission_record` |
| 专家评审 | 专家评分、最终得分、最终结论 | `expert_review_*` + `project_acceptance_ext` |
| 发证 | 证书编号、发放日期 | `project_acceptance` + `project_acceptance_ext` + `process_document` |
| 不通过文件 | 文件编号、整改要求 | `external_result_record` 或 `state_record_remark` |
| 退还结余经费 | 结余金额、账户、退还状态 | `surplus_funds_return_record` |

---

## 7. `module_state_record.payload_json` 的 V2 使用边界

V2 中 `payload_json` 仍然可以保留，但必须约束用途。

### 7.1 允许保存

```text
1. 请求原始快照；
2. 前端临时状态；
3. 调试信息；
4. 非业务关键字段；
5. 尚未结构化的过渡字段；
6. 状态机本次执行时的临时变量。
```

### 7.2 不允许长期保存

```text
1. 审核检查项；
2. 是否通过；
3. 外部文号；
4. 外部结果；
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

### 7.3 状态迁移接口处理方式

状态迁移接口可以接收结构化请求，例如：

```json
{
  "eventType": "DEPT_REVIEW_FINISHED",
  "remark": "同意推荐",
  "checkItems": [
    {
      "itemCode": "APPLICATION_COMPLETE",
      "itemName": "申请书是否完整",
      "itemType": "BOOLEAN",
      "itemValue": "true",
      "passed": true
    }
  ],
  "materialVersionIds": [1001, 1002]
}
```

后端不要直接整体塞进 `payload_json`，而应拆分写入：

```text
state_record_check_item
state_record_remark
state_record_material
```

`payload_json` 只保留原始请求摘要或调试字段。

---

## 8. 新增表 SQL 汇总

详见配套 SQL 文件：

```text
process_node_forms_deliverables_v2_tables.sql
```

推荐第一批必须新增：

```text
state_record_check_item
external_result_record
seal_record
submission_record
expert_review_batch
expert_review_assignment
expert_review_score
```

推荐第二批新增：

```text
project_application_ext
project_contract_ext
project_acceptance_ext
acceptance_financial_settlement
project_achievement
```

推荐第三批新增：

```text
notice_record
project_application_publicity
surplus_funds_return_record
archive_record
project_application_detail
```

---

## 9. V2 实现建议

### 9.1 StateMachineRuntime 写入逻辑

状态迁移时，后端应将请求拆分为：

```text
1. 状态迁移事实 → module_state_record
2. 操作意见 → state_record_remark
3. 文件材料 → state_record_material
4. 审核检查项 → state_record_check_item
5. 外部结果 → external_result_record
6. 用印记录 → seal_record
7. 上报记录 → submission_record
8. 专家评审 → expert_review_*
9. 业务扩展字段 → project_*_ext
```

### 9.2 ViewModel 组装逻辑

ViewModel 不再从 `payload_json` 中解析业务字段，而是从结构化表查询：

```text
审核表 → state_record_check_item + state_record_remark
外部结果登记表 → external_result_record
用印记录 → seal_record
上报记录 → submission_record
公示表 → project_application_publicity
经费决算表 → acceptance_financial_settlement
成果清单 → project_achievement
专家评审表 → expert_review_*
```

### 9.3 `process_document.snapshot_json` 的定位

`process_document.snapshot_json` 仍然保留。

它的作用不是替代业务表，而是：

```text
在节点结束或流程结束时，把当时的结构化表数据快照固化下来。
```

这样可以避免后续业务数据变化导致历史审批单、归档单据漂移。

---

## 10. 最终结论

V2 设计从：

```text
大量使用 payload_json 的灵活模型
```

调整为：

```text
结构化业务表为主，payload_json 兜底为辅
```

核心变化是：

```text
审核检查项 → state_record_check_item
外部结果 → external_result_record
签字盖章 → seal_record
上报记录 → submission_record
通知发布 → notice_record
公示结果 → project_application_publicity
经费决算 → acceptance_financial_settlement
成果清单 → project_achievement
结余经费退还 → surplus_funds_return_record
专家评审 → expert_review_*
业务模块扩展字段 → project_*_ext
```

这样可以兼顾：

```text
1. 数据表可维护；
2. 字段含义清晰；
3. 支持查询统计；
4. 支持表单自动组装；
5. 支持流程审计；
6. 避免每个节点都单独建一张表导致表爆炸。
```
