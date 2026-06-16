# 三类科研项目流程节点表单与交付物细化设计 V2：结构化数据源修正版

> 本文保留 V1 文档的章节结构、流程节点顺序和字段表格风格，重点修正“流程节点表单与交付物”中原先来源为 `payload` / `payload_json` 的字段，将其改为 V2 结构化表中的数据源。


## 1. 设计口径

本文基于当前三类 BPMN 流程：

```text
项目申请流程 APPLICATION
纵向项目合同流程 CONTRACT
项目结题流程 ACCEPTANCE
```

并基于当前数据库设计：

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

本文默认采用以下原则：

> 在保留 V1“虚拟表单 + 材料文件 + 正式单据快照”机制的基础上，尽可能将原先来源为 `payload` / `payload_json` 的稳定字段转化为 V2 中的结构化数据表。

也就是说，大部分节点结束后的表单或交付物仍然由多张表组合得到，但字段来源不再大量依赖 `payload_json`，而是优先使用结构化表：

| 来源 | 作用 |
|---|---|
| `project` | 项目基础信息 |
| `project_application` / `project_contract` / `project_acceptance` | 对应业务模块的核心业务事实 |
| `project_member` | 项目成员与分工 |
| `module_state_record` | 流程流转事实 |
| `state_record_remark` | 审核意见、操作说明、专家意见、代录说明 |
| `state_record_material` | 某次状态迁移关联了哪些材料版本 |
| `material` / `material_version` | 上传文件、签字盖章文件、佐证材料 |
| `process_document` | 固化后的正式单据快照 |
| `process_document_file` | 正式单据关联的文件版本 |
| `workflow_node_document_config.snapshot_schema_json` | 单据快照结构配置 |
| `workflow_node_material_requirement` | 节点材料要求 |
| `notice_record` | 申报通知、结题通知等通知发布信息 |
| `state_record_check_item` | 审核清单、检查项、布尔判断项 |
| `external_result_record` | 主管部门或第三方机构审核、批复、盖章、生效结果 |
| `seal_record` | 签字盖章、用印类型、用印份数、用印状态 |
| `submission_record` | 上报对象、上报方式、上报编号、回执信息 |
| `archive_record` | 归档编号、归档位置、归档份数、归档状态 |
| `project_application_ext` | 申报批次、推荐排序、主管部门批复等申报扩展信息 |
| `project_application_detail` | 在线申请书详细字段，如研究背景、目标、预算说明等 |
| `project_application_publicity` | 项目申报公示、异议处理、公示结果 |
| `project_contract_ext` | 合同甲乙方、签字盖章时间、归档信息等合同扩展信息 |
| `project_acceptance_ext` | 任务完成率、成果数量、专家结论、证书信息等结题扩展信息 |
| `acceptance_financial_settlement` | 经费到账、支出、结余、执行率等经费决算信息 |
| `project_achievement` | 论文、专利、软件著作权等成果清单 |
| `surplus_funds_return_record` | 结余经费退还账户、金额、状态、凭证等 |
| `expert_review_batch` / `expert_review_assignment` / `expert_review_score` | 多专家评审、评分项、汇总规则与整体结论 |

---

## 2. 表单与交付物的类型划分

本文将流程中的“表单 / 交付物”划分为四类。

### 2.1 虚拟表单

虚拟表单不单独建表，而是由多个表组合生成。

例如：

```text
项目申报审批单 =
  project
  + project_application
  + project_member
  + module_state_record
  + state_record_remark
  + material_version
  + process_document.snapshot_json
```

适合：

```text
审核意见表
材料清单
用印申请记录
上报材料包
归档清单
流程流转记录
```

### 2.2 材料型交付物

材料型交付物本质是文件。

例如：

```text
项目申请书 PDF
合同 PDF
签字盖章合同扫描件
结题报告
经费决算表
验收证书扫描件
```

建议通过：

```text
material
material_version
state_record_material
process_document_file
```

管理。

### 2.3 固化正式单据

正式单据需要在某个节点结束或流程结束时固化，避免后续业务数据变化导致历史单据漂移。

建议通过：

```text
process_document.snapshot_json
process_document_file
```

保存。

例如：

```text
项目申报审批单
纵向项目合同归档单
项目结题验收汇总单
结题证书
结题不通过文件
```

### 2.4 结构化业务表单

只有当表单存在以下需求时，才建议单独建表：

```text
需要多专家独立评分；
需要按评分项统计；
需要计算去最高最低后的平均分；
需要排名、择优推荐；
需要后续按字段检索、统计、分析；
需要长期保留结构化评审过程。
```

典型场景：

```text
专家评审
专家验收
```

因此本文建议新增专家评审相关表。同时，对于 V1 中原先由 `payload_json` 承载的稳定字段，V2 进一步引入通知、审核检查项、外部结果、用印、上报、归档、公示、经费决算和成果清单等结构化表。

---

## 3. 通用虚拟表单模块

后续每个节点的表单可以复用以下通用模块。若某个节点使用了通用模块，后文将直接引用模块编号。

---

### 3.1 M01 项目基础信息模块

用途：

```text
所有业务流程、所有审核表、所有归档表通用。
```

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目编号 | 系统生成或立项后登记 | `project.project_code` |
| 项目名称 | 项目名称 | `project.project_name` |
| 项目负责人 | 负责人姓名 | `project.leader_user_id -> app_user.real_name` |
| 所属部门 | 二级单位 / 学院 | `project.dept_id -> department.dept_name` |
| 项目类型 | 纵向 / 校级 / 限项等 | `project.project_type` |
| 项目级别 | 校级 / 省部级 / 国家级等 | `project.project_level` |
| 批准经费 | 已批准经费 | `project.approved_amount` |
| 起止日期 | 项目开始与结束日期 | `project.start_date`, `project.end_date` |
| 当前生命周期阶段 | 申报 / 合同 / 执行 / 结题 / 完成 | `project.lifecycle_stage` |
| 创建时间 | 项目创建时间 | `project.created_at` |

是否单独建表：

```text
不需要，使用 project 及关联查询。
```

---

### 3.2 M02 项目负责人信息模块

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 负责人姓名 | 项目负责人真实姓名 | `app_user.real_name` |
| 所属部门 | 负责人所在部门 | `department.dept_name` |
| 手机号 | 联系电话 | `app_user.phone` |
| 邮箱 | 邮箱 | `app_user.email` |
| 项目内角色 | 负责人 / 成员 / 财务联系人等 | `project_member.member_role` |
| 分工说明 | 负责内容 | `project_member.responsibility` |

是否单独建表：

```text
不需要，使用 app_user + department + project_member。
```

---

### 3.3 M03 材料清单模块

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 材料类型编码 | 如 PROJECT_APPLICATION_FORM | `material_type.material_type_code` |
| 材料名称 | 如 项目申请书 | `material_type.material_type_name` |
| 是否必填 | 节点配置要求 | `workflow_node_material_requirement.required` |
| 最小数量 | 至少上传几个文件 | `workflow_node_material_requirement.min_count` |
| 文件名 | 上传文件名称 | `material_version.file_name` |
| 文件版本号 | 第几版 | `material_version.version_no` |
| 上传人 | 上传人 | `material_version.uploaded_by` |
| 上传时间 | 上传时间 | `material_version.uploaded_at` |
| 文件哈希 | 文件完整性校验 | `material_version.file_hash` |
| 是否当前版本 | 当前有效版本 | `material_version.is_current` |
| 关联状态记录 | 本材料在哪次操作中提交 | `state_record_material.state_record_id` |

是否单独建表：

```text
不需要，使用 material_type + material + material_version + state_record_material。
```

---

### 3.4 M04 审核意见模块

适用节点：

```text
二级单位审核
科技处审核
主管部门结果登记
合同审核
结题审核
```

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 审核节点 | 当前 BPMN 节点名称 | `workflow_node.node_name` |
| 审核角色 | 处理角色 | `workflow_node.candidate_role_code` |
| 审核人 | 实际操作人 | `state_record_remark.participant_user_id` |
| 审核部门 | 审核人所在部门 | `app_user.dept_id` |
| 审核动作 | SUBMIT / APPROVE / RETURN / REGISTER_RESULT | `state_record_remark.action_type` |
| 审核结果 | APPROVED / RETURNED / REJECTED 等 | `state_record_remark.result` |
| 审核意见 | 文本意见 | `state_record_remark.remark_content` |
| 是否最终有效 | 对多轮退回时判断有效意见 | `state_record_remark.is_final` |
| 审核时间 | 创建时间 | `state_record_remark.created_at` |
| 关联附件 | 审核意见附件 | `state_record_material` + `material_version` |

是否单独建表：

```text
不需要，使用 state_record_remark。
```

---

### 3.5 M05 退回修改说明模块

适用场景：

```text
审核不通过并退回申请人 / 项目负责人修改。
```

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 退回节点 | 哪个节点退回 | `workflow_node.node_name` |
| 退回人 | 操作人 | `state_record_remark.participant_user_id` |
| 退回角色 | 操作角色 | `state_record_remark.participant_role_id` |
| 退回原因 | 文字说明 | `state_record_remark.remark_content` |
| 修改要求 | 退回修改要求，优先记录在文字意见中，结构化问题项记录在检查项表 | `state_record_remark.remark_content` + `state_record_check_item` |
| 退回时间 | 操作时间 | `module_state_record.created_at` |
| 原轮次 | 第几轮被退回 | `module_state_record.round_no` |
| 返回目标节点 | 通常返回草稿节点 | `module_state_record.to_node_id` |

是否单独建表：

```text
不需要，使用 module_state_record + state_record_remark。
```

---

### 3.6 M06 单个专家评审表模块

适用场景：

```text
院级专家评审
校级专家评审
结题专家验收
```

建议单独建表，因为专家评审需要结构化评分和汇总计算。

单个专家评审表字段建议：

| 字段 | 说明 |
|---|---|
| 评审批次 ID | 对应一次专家评审任务 |
| 专家用户 ID | 对应专家 |
| 专家姓名 | 展示字段 |
| 专家单位 | 可选 |
| 是否回避 | 是否存在利益冲突 |
| 评分明细 | 多个评分项 |
| 总分 | 加权或直接求和 |
| 专家结论 | 通过 / 不通过 / 推荐 / 不推荐 / 修改后通过 |
| 专家意见 | 文本意见 |
| 是否有效 | 是否计入汇总 |
| 提交时间 | 专家提交时间 |

是否单独建表：

```text
建议新增 expert_review_batch、expert_review_assignment、expert_review_score。
```

---

### 3.7 M07 专家评审汇总表模块

字段建议：

| 字段 | 说明 |
|---|---|
| 评审类型 | 院级申报评审 / 校级申报评审 / 结题验收评审 |
| 评审节点 | 对应 BPMN 节点 |
| 专家总数 | 应参与专家数 |
| 已提交专家数 | 实际提交数 |
| 有效专家数 | 排除回避、无效评分后的专家数 |
| 汇总规则 | 平均分 / 去最高最低 / 多数通过 / 加权平均 |
| 最高分 | 统计字段 |
| 最低分 | 统计字段 |
| 是否去最高最低 | 规则字段 |
| 最终得分 | 汇总计算结果 |
| 通过阈值 | 如 80 分 |
| 推荐阈值 | 如 85 分 |
| 最终结论 | 通过 / 不通过 / 推荐 / 不推荐 |
| 排名 | 同批项目排名时使用 |
| 汇总意见 | 管理员或系统生成 |
| 汇总时间 | 汇总生成时间 |

是否单独建表：

```text
建议新增 expert_review_batch 保存汇总结果。
```

---

### 3.8 M08 签字盖章 / 用印记录模块

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 用印材料名称 | 如申请书、合同、结题材料 | `material_type.material_type_name` |
| 用印原因 | 申请用印原因 | `seal_record.seal_reason`，补充说明可在 `state_record_remark.remark_content` |
| 申请人 | 项目负责人或科技处管理员 | `state_record_remark.participant_user_id` |
| 用印类型 | 学校章 / 部门章 / 合同章 / 负责人签字 | `seal_record.seal_type` |
| 用印份数 | 份数 | `seal_record.copy_count` |
| 用印日期 | 日期 | `seal_record.school_sealed_at` / `seal_record.leader_signed_at` / `seal_record.external_sealed_at` |
| 签字盖章文件 | 上传文件 | `material_version` |
| 审核状态 | 完成 / 退回 / 已用印 | `seal_record.seal_status` + `state_record_remark.result` |

是否单独建表：

```text
V2 建议单独结构化为 `seal_record`，文件仍使用 `material_version`。
```

---

### 3.9 M09 上报材料包模块

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 上报对象 | 主管部门 / 第三方机构 | `submission_record.target_actor_name`，默认可参考 `workflow_node.represented_actor_name` |
| 上报时间 | 操作时间 | `submission_record.submitted_at` |
| 上报人 | 操作人 | `submission_record.submitted_by` |
| 材料清单 | 上报了哪些材料 | M03 |
| 上报方式 | 系统上传 / 线下报送 / 邮件 / 平台提交 | `submission_record.submission_method` |
| 上报编号 | 外部系统回执号 | `submission_record.submission_no` / `submission_record.receipt_no` |
| 回执文件 | 上报回执 | `material_version` |
| 备注 | 说明 | `state_record_remark.remark_content` |

是否单独建表：

```text
V2 建议结构化为 `submission_record`，材料清单仍通过 M03 组合。
```

---

### 3.10 M10 归档清单模块

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 归档节点 | 合同归档 / 结题归档等 | workflow_node |
| 归档人 | 操作人 | state_record_remark |
| 归档时间 | 操作时间 | module_state_record.created_at |
| 归档材料列表 | 最终文件列表 | material_version / process_document_file |
| 是否主文件 | 主文件标记 | process_document_file.is_main_file |
| 档案编号 | 可选 | process_document.document_no |
| 归档状态 | 完成 / 缺失 / 补充中 | process_document.document_status |
| 备注 | 说明 | remark_content |

是否单独建表：

```text
不需要，使用 process_document + process_document_file。
```

---

### 3.11 M11 流程流转记录模块

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 序号 | 当前模块内递增序号 | `module_state_record.seq` |
| 轮次 | 第几轮提交/审核 | `module_state_record.round_no` |
| 原状态 | from_state | `module_state_record.from_state` |
| 新状态 | to_state | `module_state_record.to_state` |
| 事件类型 | event_type | `module_state_record.event_type` |
| 操作摘要 | summary | `module_state_record.summary` |
| 操作人 | operator remark | `state_record_remark.is_operator = true` |
| 操作意见 | remark_content | `state_record_remark.remark_content` |
| 操作时间 | created_at | `module_state_record.created_at` |

是否单独建表：

```text
不需要。
```

---

### 3.12 M12 经费决算模块

适用：

```text
项目结题流程中的经费决算节点。
```

字段建议：

| 字段 | 说明 | 来源 |
|---|---|---|
| 批准经费 | 项目批准经费 | `project.approved_amount` |
| 已到账经费 | 实际到账金额 | `acceptance_financial_settlement.received_amount` |
| 已支出经费 | 财务决算字段 | `acceptance_financial_settlement.spent_amount` |
| 结余经费 | 批准或到账经费 - 已支出 | `acceptance_financial_settlement.surplus_amount` |
| 报销清单文件 | 财务处打印文件 | material_version |
| 经费决算表 | 财务处确认文件 | material_version |
| 财务经办人 | 操作人 | state_record_remark |
| 财务审核意见 | 说明 | state_record_remark |
| 决算时间 | 操作时间 | module_state_record.created_at |

是否单独建表：

```text
V2 建议单独结构化为 `acceptance_financial_settlement`，相关文件仍使用 `material_version`。
```

可选新增表：

```sql
CREATE TABLE acceptance_financial_settlement (
    settlement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    approved_amount DECIMAL(14,2),
    received_amount DECIMAL(14,2),
    spent_amount DECIMAL(14,2),
    surplus_amount DECIMAL(14,2),
    settlement_result VARCHAR(64),
    finance_operator_id BIGINT,
    remark TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);
```

---

## 4. 专家评审结构化建表建议

专家评审不建议仅作为普通 `state_record_remark` 保存。原因是：

```text
1. 一个节点可能有多个专家；
2. 每个专家有多个评分项；
3. 需要汇总计算整体结果；
4. 需要支持去最高最低、平均分、多数通过等规则；
5. 需要后续按分数、排名、专家意见统计。
```

因此建议新增以下表。

---

### 4.1 expert_review_batch

表示一次专家评审批次。一个专家评审节点对应一个或多个批次。

```sql
CREATE TABLE expert_review_batch (
    batch_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    workflow_node_id BIGINT,
    state_record_id BIGINT,
    review_type VARCHAR(64) NOT NULL,
    review_title VARCHAR(255) NOT NULL,

    rule_type VARCHAR(64) NOT NULL,
    min_expert_count INT NOT NULL DEFAULT 3,
    pass_score DECIMAL(5,2),
    recommend_score DECIMAL(5,2),
    remove_highest_lowest BOOLEAN NOT NULL DEFAULT FALSE,

    expected_expert_count INT,
    submitted_expert_count INT DEFAULT 0,
    valid_expert_count INT DEFAULT 0,

    highest_score DECIMAL(5,2),
    lowest_score DECIMAL(5,2),
    final_score DECIMAL(5,2),
    final_result VARCHAR(64),

    summary_comment TEXT,
    status VARCHAR(32) NOT NULL,

    created_by BIGINT,
    created_at DATETIME NOT NULL,
    completed_at DATETIME,
    updated_at DATETIME
);
```

字段说明：

| 字段 | 说明 |
|---|---|
| `review_type` | `APPLICATION_DEPT_EXPERT` / `APPLICATION_SCIENCE_EXPERT` / `ACCEPTANCE_EXPERT` |
| `rule_type` | `AVERAGE` / `REMOVE_HIGHEST_LOWEST_AVERAGE` / `MAJORITY_PASS` / `WEIGHTED_AVERAGE` |
| `final_result` | `PASSED` / `REJECTED` / `RECOMMENDED` / `NOT_RECOMMENDED` |
| `status` | `DRAFT` / `IN_PROGRESS` / `COMPLETED` / `CANCELED` |

---

### 4.2 expert_review_assignment

表示某位专家被分配到某个评审批次。

```sql
CREATE TABLE expert_review_assignment (
    assignment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,

    expert_name VARCHAR(128),
    expert_org VARCHAR(255),
    expert_title VARCHAR(128),

    assigned_at DATETIME NOT NULL,
    submitted_at DATETIME,

    review_status VARCHAR(32) NOT NULL,
    conflict_of_interest BOOLEAN NOT NULL DEFAULT FALSE,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,

    total_score DECIMAL(5,2),
    review_result VARCHAR(64),
    review_comment TEXT,

    UNIQUE (batch_id, expert_user_id)
);
```

---

### 4.3 expert_review_score

表示某位专家对某个评分项的评分。

```sql
CREATE TABLE expert_review_score (
    score_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    score_item_code VARCHAR(64) NOT NULL,
    score_item_name VARCHAR(128) NOT NULL,
    weight DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    max_score DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    score_value DECIMAL(5,2) NOT NULL,
    comment TEXT
);
```

---

### 4.4 专家评审结果汇总规则

推荐第一版支持两种规则。

#### 4.4.1 去最高最低后平均分

适合专家数不少于 5 的情况。

```text
有效专家数 >= 5：
  去掉最高分
  去掉最低分
  对剩余分数求平均
```

例如：

```text
专家分数：92, 88, 85, 90, 78

去最高分 92
去最低分 78

最终分 = (88 + 85 + 90) / 3 = 87.67
```

#### 4.4.2 普通平均分

适合专家数为 3 或 4 的情况。

```text
有效专家数 < 5：
  直接取平均分
```

#### 4.4.3 推荐阈值

项目申报专家评审建议：

```text
final_score >= 85：推荐
70 <= final_score < 85：可推荐 / 排队推荐
final_score < 70：不推荐
```

项目结题专家评审建议：

```text
final_score >= 80：通过
final_score < 80：不通过
```

最终阈值可配置到：

```text
expert_review_batch.pass_score
expert_review_batch.recommend_score
```

---

## 5. 项目申请流程节点表单与交付物

---

### 5.1 发布申报通知

节点：

```text
PublishNoticeTask
APPLICATION_NOTICE_PUBLISHING
```

节点结束后产生：

```text
项目申报通知表
```

表单编码：

```text
APPLICATION_NOTICE_FORM
```

表单类型：

```text
虚拟表单 + 材料型交付物
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 通知信息 | 通知标题 | 申报通知标题 | `notice_record` + `process_document.snapshot_json` |
| 通知信息 | 通知编号 | 通知文号或系统编号 | `notice_record.notice_no` |
| 通知信息 | 通知类型 | 限项 / 非限项 / 常规申报 | `notice_record.notice_type` + `notice_record.is_limited_project` |
| 通知信息 | 发布单位 | 科技处 | workflow_node.responsible_actor_name |
| 通知信息 | 发布人 | 科技处管理员 | state_record_remark |
| 通知信息 | 发布时间 | 节点完成时间 | module_state_record.created_at |
| 申报要求 | 项目类别 | 校级 / 省部级 / 国家级 | `notice_record.project_category` |
| 申报要求 | 申报对象 | 面向哪些学院、教师 | `notice_record.target_dept_scope` / `notice_record.target_user_scope` |
| 申报要求 | 限项数量 | 如每学院推荐 N 项 | `notice_record.limit_count` |
| 申报要求 | 申报截止时间 | 提交截止时间 | `notice_record.deadline_time` |
| 申报要求 | 材料要求 | 申请书、预算表、附件等 | workflow_node_material_requirement |
| 附件 | 通知正文文件 | PDF / DOC | material_version |
| 附件 | 模板文件 | 申请书模板、预算模板 | material_version |

是否单独建表：

```text
V2 建议使用 `notice_record` 保存通知结构化字段；通知正文、模板文件仍通过 `material_version` 管理；正式通知单可通过 `process_document.snapshot_json` 固化。
```

---

### 5.2 填报并提交项目申请书

节点：

```text
SubmitApplicationTask
APPLICATION_DRAFT
```

节点结束后产生：

```text
项目申请书
申报佐证材料清单
项目成员信息表
```

表单编码：

```text
PROJECT_APPLICATION_FORM
```

表单类型：

```text
业务表 + 虚拟表单 + 材料型交付物
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 项目基础 | 项目名称 | 申报项目名称 | project.project_name / project_application.application_title |
| 项目基础 | 项目类型 | 项目类别 | project.project_type |
| 项目基础 | 是否限项项目 | 网关条件 | project_application.is_limited_project |
| 项目基础 | 申报摘要 | 项目摘要 | project_application.application_summary |
| 负责人 | 负责人姓名 | 项目负责人 | M02 |
| 负责人 | 所属二级单位 | 学院 / 部门 | M02 |
| 团队 | 成员姓名 | 项目成员 | project_member |
| 团队 | 成员分工 | 分工说明 | project_member.responsibility |
| 研究内容 | 研究背景 | 文本字段 | `project_application_detail.research_background`，也可由申请书文件补充 |
| 研究内容 | 研究目标 | 文本字段 | `project_application_detail.research_objective` / `process_document.snapshot_json` |
| 研究内容 | 主要内容 | 文本字段 | `project_application_detail.research_content` / `process_document.snapshot_json` |
| 研究内容 | 创新点 | 文本字段 | `project_application_detail.innovation_points` / `process_document.snapshot_json` |
| 计划安排 | 阶段计划 | 起止时间与任务 | `project_application_detail.schedule_plan` / `process_document.snapshot_json` |
| 经费预算 | 预算总额 | 经费预算 | `project_application_ext.expected_budget` / 预算材料 `material_version` |
| 经费预算 | 经费用途 | 设备、材料、差旅等 | `project_application_detail.budget_description` / 预算材料 `material_version` |
| 成果 | 预期成果 | 论文、专利、软件著作权等 | `project_application_detail.expected_outcomes` / 申请书材料 `material_version` |
| 承诺 | 真实性承诺 | 负责人确认 | state_record_remark |
| 附件 | 项目申请书 | 必填 | material_version |
| 附件 | 申报佐证材料 | 可选 | material_version |

是否单独建表：

```text
V2 建议：核心字段进入 `project_application`，申报批次、预算、推荐结果等进入 `project_application_ext`；若支持在线填写申请书详细内容，则进入 `project_application_detail`；申请书文件仍通过 `material_version` 管理，正式单据通过 `process_document.snapshot_json` 固化。
```

---

### 5.3 二级单位形式审核

节点：

```text
DeptReviewTask
APPLICATION_DEPT_REVIEWING
```

节点结束后产生：

```text
二级单位形式审核表
```

表单编码：

```text
DEPT_REVIEW_OPINION_FORM
```

表单类型：

```text
虚拟表单
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 项目信息 | 项目基础信息 | 引用 M01 | M01 |
| 审核信息 | 审核人 | 二级单位管理员 | state_record_remark |
| 审核信息 | 审核时间 | 审核完成时间 | module_state_record.created_at |
| 审核清单 | 是否属于本单位项目 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 负责人资格是否符合 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 申请书是否完整 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 附件是否齐全 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 经费预算是否合理 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 是否存在重复申报 | 是 / 否 | `state_record_check_item` |
| 审核结果 | deptApproved | true / false | `state_record_remark.result`，必要时由 `state_record_check_item` 汇总得到 |
| 审核结果 | 审核结论 | 通过 / 退回 | state_record_remark.result |
| 审核意见 | 文字意见 | 审核说明 | state_record_remark.remark_content |
| 附件 | 二级单位审核意见附件 | 可选 | material_version |

是否单独建表：

```text
V2 建议使用 `state_record_check_item` 保存审核检查项；审核意见与结论仍使用 `state_record_remark`。
```

---

### 5.4 二级单位专家评审并择优推荐

节点：

```text
DeptExpertReviewTask
APPLICATION_DEPT_EXPERT_REVIEWING
```

节点结束后产生：

```text
二级单位专家个人评审表
二级单位专家评审汇总表
二级单位择优推荐意见表
```

表单编码：

```text
DEPT_EXPERT_REVIEW_FORM
DEPT_EXPERT_REVIEW_SUMMARY_FORM
DEPT_RECOMMENDATION_FORM
```

表单类型：

```text
结构化专家评审表 + 虚拟表单
```

单个专家评分项建议：

| 评分项 | 最高分 | 说明 |
|---|---:|---|
| 选题意义 | 20 | 是否符合申报方向，是否有现实意义 |
| 创新性 | 20 | 理论、方法、应用创新 |
| 可行性 | 20 | 技术路线、研究计划是否可行 |
| 研究基础 | 15 | 前期成果、团队基础 |
| 团队条件 | 10 | 成员结构、分工是否合理 |
| 预期成果 | 15 | 成果目标是否明确、可评价 |

汇总字段：

| 字段 | 说明 | 来源 |
|---|---|---|
| 专家人数 | 应参与专家数量 | expert_review_batch.expected_expert_count |
| 已提交人数 | 已提交评审人数 | expert_review_batch.submitted_expert_count |
| 有效人数 | 有效评分人数 | expert_review_batch.valid_expert_count |
| 汇总规则 | 平均 / 去最高最低 | expert_review_batch.rule_type |
| 最终得分 | 计算结果 | expert_review_batch.final_score |
| 最终结论 | 推荐 / 不推荐 / 退回修改 | expert_review_batch.final_result |
| 推荐排序 | 院内排序 | `expert_review_batch.rank_no` / `project_application_ext.dept_recommend_rank` |
| 汇总意见 | 二级单位汇总意见 | state_record_remark |

整体结果建议：

```text
final_score >= 85：推荐
70 <= final_score < 85：备选推荐
final_score < 70：不推荐
```

是否单独建表：

```text
建议新增 expert_review_batch、expert_review_assignment、expert_review_score。
```

---

### 5.5 科技处初审

节点：

```text
ScienceOfficeInitialReviewTask
APPLICATION_SCIENCE_INITIAL_REVIEWING
```

节点结束后产生：

```text
科技处初审意见表
```

表单编码：

```text
SCIENCE_INITIAL_REVIEW_OPINION_FORM
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 项目信息 | 项目基础信息 | 引用 M01 | M01 |
| 审核清单 | 申报类别是否正确 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 是否符合通知要求 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 材料是否完整 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 二级单位推荐是否有效 | 是 / 否 | `state_record_check_item` |
| 审核清单 | 是否需要校级专家评审 | 是 / 否 | `state_record_check_item` |
| 审核结果 | scienceInitialApproved | true / false | `state_record_remark.result`，必要时由 `state_record_check_item` 汇总得到 |
| 审核意见 | 科技处初审意见 | 文本 | state_record_remark |
| 附件 | 初审意见附件 | 可选 | material_version |

是否单独建表：

```text
不需要。
```

---

### 5.6 科技处组织专家评审

节点：

```text
ScienceExpertReviewTask
APPLICATION_SCIENCE_EXPERT_REVIEWING
```

节点结束后产生：

```text
校级专家个人评审表
校级专家评审汇总表
校级专家推荐结论表
```

表单编码：

```text
SCIENCE_EXPERT_REVIEW_FORM
SCIENCE_EXPERT_REVIEW_SUMMARY_FORM
```

单个专家评分项建议：

| 评分项 | 最高分 | 说明 |
|---|---:|---|
| 项目必要性 | 15 | 是否有明确价值 |
| 创新性 | 20 | 是否具备创新点 |
| 技术路线可行性 | 20 | 路线是否合理 |
| 研究基础 | 15 | 团队和前期成果 |
| 经费预算合理性 | 10 | 预算是否匹配任务 |
| 预期成果质量 | 20 | 成果是否可评价、可验收 |

整体结果：

```text
专家数 >= 5：建议去最高最低后平均；
专家数为 3 或 4：直接平均；
final_score >= 85：推荐；
final_score < 85：不推荐或备选。
```

是否单独建表：

```text
建议复用 expert_review_* 表，review_type = APPLICATION_SCIENCE_EXPERT。
```

---

### 5.7 科技处择优推荐并公示

节点：

```text
ScienceOfficePublicityTask
APPLICATION_PUBLICITY
```

节点结束后产生：

```text
推荐名单公示表
公示异议处理表
公示结果确认表
```

表单编码：

```text
PUBLICITY_NOTICE_FORM
PUBLICITY_RESULT_FORM
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 公示信息 | 公示标题 | 推荐名单公示标题 | `project_application_publicity.publicity_title` |
| 公示信息 | 公示范围 | 校内 / 院内 / 网站 | `project_application_publicity.publicity_scope` |
| 公示信息 | 公示开始时间 | 日期 | `project_application_publicity.publicity_start_date` |
| 公示信息 | 公示结束时间 | 日期 | `project_application_publicity.publicity_end_date` |
| 推荐信息 | 推荐项目名单 | 项目列表 | `process_document.snapshot_json`，结构化字段优先来自对应 V2 表 |
| 推荐信息 | 推荐排序 | 排名 | `project_application_publicity.recommended_rank` / `project_application_ext.science_recommend_rank` |
| 推荐信息 | 推荐理由 | 科技处说明 | state_record_remark |
| 异议处理 | 是否有异议 | true / false | `project_application_publicity.has_objection` |
| 异议处理 | 异议内容 | 文本 | `project_application_publicity.objection_content` |
| 异议处理 | 处理结果 | 维持 / 调整 / 取消 | `project_application_publicity.objection_handling_result` |
| 公示结果 | publicityPassed | true / false | `project_application_publicity.publicity_result` |
| 附件 | 公示截图 / 公示文件 | 可选 | material_version |

是否单独建表：

```text
V2 建议使用 `project_application_publicity` 保存公示结构化字段；公示文件或截图仍通过 `material_version` 管理。
```

---

### 5.8 科技处审核上报

节点：

```text
ScienceOfficeSubmitTask
APPLICATION_SCIENCE_SUBMITTING
```

节点结束后产生：

```text
主管部门上报材料清单
科技处推荐意见表
```

表单编码：

```text
AUTHORITY_SUBMISSION_PACKAGE_FORM
SCIENCE_RECOMMENDATION_FORM
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 上报对象 | 主管部门 / 第三方机构 | `submission_record.target_actor_name` |
| 上报项目名称 | 项目名称 | M01 |
| 推荐结论 | 推荐 / 不推荐 | state_record_remark.result |
| 推荐理由 | 科技处意见 | state_record_remark.remark_content |
| 上报材料列表 | 申请书、专家评审、公示材料等 | M03 |
| 上报时间 | 节点完成时间 | module_state_record.created_at |
| 上报人 | 科技处管理员 | state_record_remark |
| 外部平台编号 | 如有 | `submission_record.external_system_no` |
| 上报附件 | 材料包 ZIP / PDF | material_version |

是否单独建表：

```text
不需要。
```

---

### 5.9 主管部门或第三方机构审核结果登记

节点：

```text
AuthorityReviewTask
APPLICATION_AUTHORITY_REVIEWING
```

节点结束后产生：

```text
主管部门审核结果登记表
主管部门批复文件
```

表单编码：

```text
AUTHORITY_REVIEW_RESULT_FORM
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 审核主体 | 主管部门 / 第三方机构 | workflow_node.represented_actor_name |
| 代录人 | 科技处管理员 | state_record_remark |
| 外部审核时间 | 外部结果时间 | `external_result_record.external_result_date` |
| 外部文号 | 批复文号 | `external_result_record.external_file_no` |
| 外部结果 | 通过 / 不通过 / 补充材料 | `external_result_record.external_result` |
| authorityApproved | 网关条件 | `external_result_record.external_result` + `project_application_ext.authority_result` |
| 批准经费 | 如有 | `external_result_record.approved_amount`，必要时同步更新 `project.approved_amount` / `project_application_ext.authority_approved_amount` |
| 批复意见 | 外部意见摘要 | state_record_remark.remark_content |
| 批复文件 | 必填 | material_version |

是否单独建表：

```text
不需要。
V2 建议统一使用 `external_result_record` 保存外部结果；申报维度的最终批复字段可同步写入 `project_application_ext`。
```

---

### 5.10 打印申请书、签字并申请用章

节点：

```text
SignAndSealTask
APPLICATION_SIGN_SEALING
```

节点结束后产生：

```text
签字盖章后的项目申请书
用印申请记录
```

表单编码：

```text
SIGNED_APPLICATION_FORM
SEAL_APPLICATION_RECORD_FORM
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 用印材料 | 项目申请书 | material_type |
| 用印原因 | 申报主管部门需要 | `seal_record.seal_reason` |
| 用印份数 | 份数 | `seal_record.copy_count`_json |
| 负责人签字状态 | 已签 / 未签 | `seal_record.leader_signed` |
| 学校盖章状态 | 已盖章 / 未盖章 | `seal_record.school_sealed` |
| 签字盖章文件 | 必填 | material_version |
| 操作人 | 项目负责人 | state_record_remark |
| 完成时间 | 状态迁移时间 | module_state_record.created_at |

是否单独建表：

```text
第一版不需要。
```

---

### 5.11 报送主管部门或第三方机构

节点：

```text
SubmitFinalMaterialsTask
APPLICATION_FINAL_MATERIAL_SUBMITTING
```

节点结束后产生：

```text
最终报送材料清单
项目申报审批单
正式报送记录
```

表单编码：

```text
FINAL_APPLICATION_MATERIALS_FORM
APPLICATION_APPROVAL_FORM
APPLICATION_SUBMISSION_RECORD_FORM
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目基础信息 | 引用 M01 | M01 |
| 最终申请书 | 签字盖章版 | material_version |
| 批复文件 | 主管部门批复 | material_version |
| 专家评审报告 | 如有 | process_document / material_version |
| 公示材料 | 如有 | material_version |
| 上报对象 | 外部主体 | `submission_record.target_actor_name` |
| 上报方式 | 平台 / 邮件 / 线下 | `submission_record.submission_method` |
| 回执编号 | 外部回执号 | `submission_record.receipt_no` |
| 报送人 | 科技处管理员 | state_record_remark |
| 报送时间 | 节点完成时间 | module_state_record.created_at |
| 审批单快照 | 流程最终汇总 | process_document.snapshot_json |

是否单独建表：

```text
不需要。项目申报审批单通过 process_document 固化。
```

---

## 6. 纵向项目合同流程节点表单与交付物

---

### 6.1 批准立项

节点：

```text
AuthorityApproveProjectTask
CONTRACT_PROJECT_APPROVED
```

节点结束后产生：

```text
项目立项批复登记表
项目立项批复文件
```

表单编码：

```text
PROJECT_APPROVAL_REGISTER_FORM
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目名称 | 项目基础信息 | M01 |
| 批准主体 | 主管部门 / 专业机构 | workflow_node |
| 批准文号 | 立项批复文号 | `external_result_record.external_file_no` |
| 批准日期 | 外部批准时间 | `external_result_record.external_result_date` |
| 批准经费 | 批准金额 | `external_result_record.approved_amount`，必要时同步更新 `project.approved_amount` |
| 项目起止时间 | 批复中的起止时间 | project.start_date / project.end_date |
| 登记人 | 科技处管理员 | state_record_remark |
| 立项批复文件 | 必填 | material_version |

是否单独建表：

```text
V2 建议使用 `external_result_record` 保存立项批复文号、日期、金额等外部结果；项目层面的批准经费和起止时间可同步更新到 `project`。
```

---

### 6.2 填写项目合同

节点：

```text
FillContractTask
CONTRACT_DRAFT
```

节点结束后产生：

```text
项目合同草稿
```

表单编码：

```text
CONTRACT_DRAFT_FORM
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 合同基础 | 合同编号 | 可草稿为空 | project_contract.contract_code |
| 合同基础 | 合同名称 | 合同名称 | project_contract.contract_name |
| 合同基础 | 合同金额 | 金额 | project_contract.contract_amount |
| 合同基础 | 合同开始日期 | 日期 | project_contract.contract_start_date |
| 合同基础 | 合同结束日期 | 日期 | project_contract.contract_end_date |
| 合同主体 | 甲方 | 主管部门 / 委托方 | `project_contract_ext.party_a_name` / 合同文件 `material_version` |
| 合同主体 | 乙方 | 学校 / 项目承担单位 | `project_contract_ext.party_b_name` / 合同文件 `material_version` |
| 合同主体 | 项目负责人 | 负责人 | M02 |
| 合同内容 | 研究任务 | 合同正文 | material_version |
| 合同内容 | 进度安排 | 合同正文 | material_version |
| 合同内容 | 经费拨付计划 | 合同正文 | material_version |
| 合同内容 | 成果要求 | 合同正文 | material_version |
| 合同状态 | 盖章状态 | 草稿阶段通常未盖章 | project_contract.seal_status |
| 附件 | 合同草稿 | 必填 | material_version |

是否单独建表：

```text
V2 建议合同核心字段进入 `project_contract`，甲乙方、联系方式、外部审核、签章、归档等稳定扩展字段进入 `project_contract_ext`；合同正文仍作为 `material_version` 管理。
```

---

### 6.3 二级单位审核

节点：

```text
DeptReviewTask
CONTRACT_DEPT_REVIEWING
```

产生：

```text
二级单位合同审核意见表
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目信息 | 引用 M01 | M01 |
| 合同信息 | 合同金额、起止日期等 | project_contract |
| 审核清单 | 合同内容是否与立项一致 | `state_record_check_item` |
| 审核清单 | 经费与任务是否匹配 | `state_record_check_item` |
| 审核清单 | 学院是否同意承担 | `state_record_check_item` |
| 审核结果 | deptContractApproved | `state_record_check_item` |
| 审核意见 | 二级单位意见 | state_record_remark |
| 附件 | 审核意见附件 | material_version |

是否单独建表：

```text
不需要。
```

---

### 6.4 科技处审核

节点：

```text
ScienceOfficeReviewTask
CONTRACT_SCIENCE_REVIEWING
```

产生：

```text
科技处合同审核意见表
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 合同与立项一致性 | 是否一致 | `state_record_check_item` |
| 合同金额合理性 | 是否合理 | `state_record_check_item` |
| 合同周期合理性 | 是否合理 | `state_record_check_item` |
| 知识产权条款检查 | 是否符合学校要求 | `state_record_check_item` |
| 经费拨付条款检查 | 是否明确 | `state_record_check_item` |
| 违约责任条款检查 | 是否存在风险 | `state_record_check_item` |
| 审核结果 | scienceContractApproved | `state_record_check_item` |
| 审核意见 | 科技处意见 | state_record_remark |
| 附件 | 科技处审核意见附件 | material_version |

是否单独建表：

```text
不需要。
```

---

### 6.5 主管部门或专业机构审核

节点：

```text
AuthorityReviewTask
CONTRACT_AUTHORITY_REVIEWING
```

产生：

```text
主管部门合同审核结果登记表
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 审核主体 | 主管部门 / 专业机构 | workflow_node |
| 外部审核时间 | 时间 | `external_result_record.external_result_date` |
| 外部审核结果 | 通过 / 退回 | `external_result_record.external_result` |
| authorityContractApproved | 网关条件 | `external_result_record.external_result` + `project_contract_ext.authority_review_result` |
| 修改意见 | 外部意见 | state_record_remark |
| 审核文件 | 外部审核文件 | material_version |
| 代录人 | 科技处管理员 | state_record_remark |

是否单独建表：

```text
不需要。
```

---

### 6.6 打印 PDF 合同书

节点：

```text
PrintPdfContractTask
CONTRACT_PDF_PRINTING
```

产生：

```text
PDF 合同书
PDF 合同生成确认单
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 合同编号 | 合同编号 | project_contract.contract_code |
| 合同名称 | 合同名称 | project_contract.contract_name |
| PDF 生成时间 | 生成时间 | module_state_record.created_at |
| 生成操作人 | 项目负责人 | state_record_remark |
| PDF 文件 | 必填 | material_version |
| 文件哈希 | 防篡改 | material_version.file_hash |

是否单独建表：

```text
不需要。
```

---

### 6.7 项目负责人签字

节点：

```text
LeaderSignTask
CONTRACT_LEADER_SIGNING
```

产生：

```text
项目负责人签字合同
签字确认记录
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 签字人 | 项目负责人 | project.leader_user_id |
| 签字时间 | 操作时间 | module_state_record.created_at |
| 签字文件 | 必填 | material_version |
| 签字确认意见 | 说明 | state_record_remark |
| 合同版本 | 对应 PDF 版本 | material_version.version_no |

是否单独建表：

```text
不需要。
```

---

### 6.8 学校盖章

节点：

```text
SchoolSealTask
CONTRACT_SCHOOL_SEALING
```

产生：

```text
学校盖章合同
学校用印记录
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 用印材料 | 合同书 | `material_type` + `seal_record.seal_subject` |
| 用印类型 | 学校公章 / 合同章 | `seal_record.seal_type` |
| 用印份数 | 份数 | `seal_record.copy_count`_json |
| 用印时间 | 操作时间 | module_state_record.created_at |
| 经办人 | 科技处管理员 | state_record_remark |
| 学校盖章文件 | 必填 | material_version |
| 用印备注 | 说明 | state_record_remark |

是否单独建表：

```text
第一版不需要。
```

---

### 6.9 主管部门或专业机构盖章

节点：

```text
AuthoritySealTask
CONTRACT_AUTHORITY_SEALING
```

产生：

```text
主管部门盖章合同
最终生效合同
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 外部盖章主体 | 主管部门 / 专业机构 | workflow_node |
| 外部盖章时间 | 时间 | `seal_record.external_sealed_at` / `external_result_record.external_result_date` |
| 外部经办人 | 可选 | `external_result_record.summary` 或外部材料说明 |
| 最终合同文件 | 必填 | material_version |
| 合同生效日期 | 可更新 | project_contract.signed_at |
| 备注 | 说明 | state_record_remark |

是否单独建表：

```text
不需要。
```

---

### 6.10 返回科技处、项目负责人存档

节点：

```text
ArchiveContractTask
CONTRACT_ARCHIVING
```

产生：

```text
纵向项目合同归档单
正式签署合同文件
```

表单编码：

```text
CONTRACT_ARCHIVE_FORM
SIGNED_CONTRACT_DOCUMENT
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目基础信息 | 引用 M01 | M01 |
| 合同基础信息 | 合同编号、金额、起止日期 | project_contract |
| 合同最终文件 | 主管部门盖章合同 | material_version |
| 归档份数 | 纸质 / 电子份数 | `archive_record.paper_copy_count` / `archive_record.electronic_copy_count` |
| 归档位置 | 档案位置 | `archive_record.archive_location` / `project_contract_ext.archive_location` |
| 归档人 | 科技处管理员 | state_record_remark |
| 归档时间 | 节点完成时间 | module_state_record.created_at |
| 归档单快照 | 固化正式单据 | process_document.snapshot_json |

是否单独建表：

```text
V2 建议归档结构化字段进入 `archive_record`，合同扩展归档字段可同步写入 `project_contract_ext`；归档单据仍由 `process_document` 固化。
```

---

## 7. 项目结题流程节点表单与交付物

---

### 7.1 发布结题验收通知

节点：

```text
PublishAcceptanceNoticeTask
ACCEPTANCE_NOTICE_PUBLISHING
```

产生：

```text
结题验收通知表
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 通知标题 | 结题通知标题 | `notice_record.notice_title` |
| 通知编号 | 通知文号 | `notice_record.notice_no` |
| 通知对象 | 需要结题的项目范围 | `notice_record.notice_scope` / `notice_record.target_dept_scope` / `notice_record.target_user_scope` |
| 结题截止时间 | 截止日期 | `notice_record.deadline_time` |
| 材料要求 | 结题申请表、结题报告、成果附件等 | workflow_node_material_requirement |
| 发布人 | 科技处管理员 | state_record_remark |
| 发布时间 | 节点完成时间 | module_state_record.created_at |
| 通知文件 | 可选 | material_version |

是否单独建表：

```text
不需要。
```

---

### 7.2 通知项目负责人

节点：

```text
DeptNotifyLeaderTask
ACCEPTANCE_DEPT_NOTIFYING
```

产生：

```text
二级单位通知确认记录
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 被通知项目 | 项目基础信息 | M01 |
| 通知人 | 二级单位管理员 | state_record_remark |
| 通知对象 | 项目负责人 | project.leader_user_id |
| 通知方式 | 系统消息 / 电话 / 邮件 | `state_record_remark.remark_content`；如需要结构化通知批次，可复用 `notice_record` |
| 通知时间 | 操作时间 | module_state_record.created_at |
| 备注 | 通知说明 | state_record_remark |

是否单独建表：

```text
不需要。
```

---

### 7.3 办理经费决算并打印报销清单

节点：

```text
FinancialSettlementTask
ACCEPTANCE_FINANCIAL_SETTLEMENT
```

产生：

```text
经费决算表
报销清单
经费决算确认记录
```

表单编码：

```text
FINANCIAL_SETTLEMENT_FORM
REIMBURSEMENT_LIST_FORM
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 项目信息 | 项目基础信息 | 引用 M01 | M01 |
| 经费信息 | 批准经费 | 项目批准金额 | project.approved_amount |
| 经费信息 | 已到账经费 | 实际到账 | `acceptance_financial_settlement.received_amount` |
| 经费信息 | 已支出经费 | 实际支出 | `acceptance_financial_settlement.spent_amount` |
| 经费信息 | 结余经费 | 到账 - 支出 | `acceptance_financial_settlement.surplus_amount` |
| 经费信息 | 经费执行率 | 已支出 / 到账 | `acceptance_financial_settlement.execution_rate` |
| 财务确认 | 经办人 | 财务人员 | state_record_remark |
| 财务确认 | 决算结果 | 已完成 / 存在问题 | state_record_remark.result |
| 财务确认 | 决算意见 | 财务说明 | state_record_remark |
| 附件 | 经费决算表 | 必填 | material_version |
| 附件 | 报销清单 | 必填 | material_version |

是否单独建表：

```text
V2 建议使用 `acceptance_financial_settlement` 结构化保存经费决算字段；经费决算表和报销清单文件仍通过 `material_version` 管理。
```

---

### 7.4 填报结题验收材料

节点：

```text
SubmitAcceptanceMaterialsTask
ACCEPTANCE_MATERIAL_DRAFT
```

产生：

```text
结题验收申请表
结题报告
成果清单
结题佐证材料清单
```

表单编码：

```text
ACCEPTANCE_APPLICATION_FORM
ACCEPTANCE_REPORT_FORM
ACHIEVEMENT_LIST_FORM
```

字段细节：

| 分组 | 字段 | 说明 | 来源 |
|---|---|---|---|
| 项目信息 | 项目基础信息 | 引用 M01 | M01 |
| 结题信息 | 结题申请标题 | 结题申请名称 | project_acceptance |
| 结题信息 | 提交时间 | 提交时间 | project_acceptance.submitted_at |
| 任务完成 | 研究任务完成情况 | 文本 | 材料 / snapshot_json |
| 任务完成 | 计划目标完成度 | 百分比或说明 | `project_acceptance_ext.task_completion_rate` |
| 成果信息 | 论文数量 | 成果统计 | `project_acceptance_ext.paper_count`，明细见 `project_achievement` |
| 成果信息 | 专利数量 | 成果统计 | `project_acceptance_ext.patent_count`，明细见 `project_achievement` |
| 成果信息 | 软件著作权数量 | 成果统计 | `project_acceptance_ext.software_copyright_count`，明细见 `project_achievement` |
| 成果信息 | 其他成果 | 文本 | `project_acceptance_ext.other_achievement_count`，明细见 `project_achievement` |
| 经费信息 | 经费决算情况 | 引用 M12 | M12 |
| 自评结论 | 负责人自评 | 文本 | state_record_remark |
| 附件 | 结题验收申请表 | 必填 | material_version |
| 附件 | 结题报告 | 必填 | material_version |
| 附件 | 成果佐证材料 | 可选 | material_version |

是否单独建表：

```text
V2 建议使用 `project_acceptance_ext` 保存成果数量汇总，使用 `project_achievement` 保存成果明细；结题报告和成果佐证材料仍通过 `material_version` 管理。
```

可选新增成果表：

```sql
CREATE TABLE project_achievement (
    achievement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT,
    achievement_type VARCHAR(64) NOT NULL,
    achievement_title VARCHAR(255) NOT NULL,
    author_list TEXT,
    publish_or_grant_date DATE,
    proof_material_version_id BIGINT,
    remark TEXT,
    created_at DATETIME NOT NULL
);
```

---

### 7.5 二级单位审核

节点：

```text
DeptReviewTask
ACCEPTANCE_DEPT_REVIEWING
```

产生：

```text
二级单位结题审核意见表
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目基础信息 | 引用 M01 | M01 |
| 结题材料完整性 | 是否完整 | `state_record_check_item` |
| 任务完成情况 | 是否认可 | `state_record_check_item` |
| 成果真实性 | 是否确认 | `state_record_check_item` |
| 经费决算情况 | 是否完成 | `state_record_check_item` |
| 是否同意结题 | deptAcceptanceApproved | `state_record_check_item` |
| 审核意见 | 二级单位意见 | state_record_remark |
| 附件 | 二级单位结题审核意见 | material_version |

是否单独建表：

```text
不需要。
```

---

### 7.6 科技处审核

节点：

```text
ScienceOfficeReviewTask
ACCEPTANCE_SCIENCE_REVIEWING
```

产生：

```text
科技处结题审核意见表
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 材料完整性 | 是否完整 | `state_record_check_item` |
| 流程合规性 | 是否符合结题要求 | `state_record_check_item` |
| 经费决算是否完成 | 是 / 否 | `state_record_check_item` |
| 是否需要专家验收 | 由项目类型决定 | `state_record_check_item` |
| 是否同意进入后续环节 | scienceAcceptanceApproved | `state_record_check_item` |
| 审核意见 | 科技处意见 | state_record_remark |
| 附件 | 科技处审核意见 | material_version |

是否单独建表：

```text
不需要。
```

---

### 7.7 主管部门或第三方机构审核

节点：

```text
AuthorityReviewTask
ACCEPTANCE_AUTHORITY_REVIEWING
```

产生：

```text
主管部门结题审核结果登记表
主管部门结题审核文件
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 审核主体 | 主管部门 / 第三方机构 | workflow_node |
| 外部审核日期 | 日期 | `external_result_record.external_result_date` |
| 外部审核结果 | 通过 / 退回 | `external_result_record.external_result` |
| authorityAcceptanceApproved | 网关条件 | `external_result_record.external_result` + `project_acceptance_ext.authority_review_result` |
| 外部意见 | 审核意见 | state_record_remark |
| 外部文号 | 文号 | `external_result_record.external_file_no` / `project_acceptance_ext.authority_file_no` |
| 审核文件 | 主管部门结题审核文件 | material_version |
| 代录人 | 科技处管理员 | state_record_remark |

是否单独建表：

```text
不需要。
```

---

### 7.8 打印结题材料签字并申请用章

节点：

```text
SignSealAcceptanceTask
ACCEPTANCE_SIGN_SEALING
```

产生：

```text
签字盖章后的结题材料
结题材料用印记录
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 用印材料 | 结题验收申请表 / 结题报告 | `material_type` + `seal_record.seal_subject` |
| 用印原因 | 结题报送 | `seal_record.seal_reason` |
| 用印份数 | 份数 | `seal_record.copy_count`_json |
| 负责人签字状态 | 已签 / 未签 | `seal_record.leader_signed` |
| 学校盖章状态 | 已盖章 / 未盖章 | `seal_record.school_sealed` |
| 签字盖章文件 | 必填 | material_version |
| 操作人 | 项目负责人 | state_record_remark |

是否单独建表：

```text
不需要。
```

---

### 7.9 报送主管部门或第三方机构

节点：

```text
SubmitFinalMaterialsTask
ACCEPTANCE_FINAL_MATERIAL_SUBMITTING
```

产生：

```text
最终结题材料报送清单
结题材料报送记录
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 报送对象 | 主管部门 / 第三方机构 | `state_record_check_item` |
| 报送方式 | 平台 / 线下 / 邮件 | `submission_record.submission_method` |
| 报送材料列表 | 结题报告、申请表、经费决算等 | M03 |
| 报送时间 | 时间 | module_state_record.created_at |
| 报送人 | 科技处管理员 | state_record_remark |
| 外部回执编号 | 如有 | `submission_record.receipt_no` |
| 回执文件 | 如有 | material_version |

是否单独建表：

```text
不需要。
```

---

### 7.10 专家评审

节点：

```text
ExpertReviewTask
ACCEPTANCE_EXPERT_REVIEWING
```

产生：

```text
专家结题个人评审表
专家结题验收汇总表
专家验收结论表
```

表单编码：

```text
EXPERT_ACCEPTANCE_REVIEW_FORM
EXPERT_ACCEPTANCE_REVIEW_SUMMARY_FORM
```

单个专家评分项建议：

| 评分项 | 最高分 | 说明 |
|---|---:|---|
| 任务完成度 | 25 | 是否完成合同/任务书约定目标 |
| 成果质量 | 20 | 成果水平、创新性、应用价值 |
| 经费执行情况 | 15 | 经费使用是否规范、合理 |
| 材料完整性 | 10 | 结题材料是否完整 |
| 组织实施情况 | 10 | 项目过程管理是否规范 |
| 推广应用价值 | 10 | 是否具备推广应用前景 |
| 总体评价 | 10 | 综合印象与结论 |

整体规则建议：

```text
有效专家数 >= 5：
  去最高最低后平均；
有效专家数为 3 或 4：
  直接平均；
final_score >= 80：
  结题通过；
final_score < 80：
  结题不通过。
```

是否单独建表：

```text
建议复用 expert_review_* 表，review_type = ACCEPTANCE_EXPERT。
```

---

### 7.11 发放验收证书或下达结题通过文件

节点：

```text
IssueCertificateTask
ACCEPTANCE_CERTIFICATE_ISSUING
```

产生：

```text
结题验收证书
结题通过文件
项目结题验收汇总单
```

表单编码：

```text
ACCEPTANCE_CERTIFICATE_DOCUMENT
ACCEPTANCE_SUMMARY_FORM
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目基础信息 | 引用 M01 | M01 |
| 验收结论 | 通过 | project_acceptance.conclusion |
| 证书编号 | 证书编号 | `project_acceptance.certificate_no` / `project_acceptance_ext.certificate_no` |
| 证书发放日期 | 日期 | `project_acceptance_ext.certificate_issue_date` / `project_acceptance.completed_at` |
| 专家验收结果 | 专家汇总结果 | expert_review_batch |
| 科技处经办人 | 经办人 | state_record_remark |
| 证书文件 | 必填 | material_version |
| 汇总单快照 | 流程总结 | process_document.snapshot_json |

是否单独建表：

```text
不需要。证书编号和结论进入 project_acceptance，正式证书用 process_document + material_version。
```

---

### 7.12 下达结题不通过文件

节点：

```text
IssueFailFileTask
ACCEPTANCE_FAIL_FILE_ISSUING
```

产生：

```text
结题不通过文件
结题不通过原因说明
```

表单编码：

```text
ACCEPTANCE_FAIL_DOCUMENT
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目基础信息 | 引用 M01 | M01 |
| 不通过原因 | 专家或科技处结论 | expert_review_batch / state_record_remark |
| 整改要求 | 是否允许整改 | `state_record_remark.remark_content`，如需结构化可扩展 `state_record_check_item` |
| 文件编号 | 不通过文件文号 | `process_document.document_no` / `external_result_record.external_file_no` |
| 下达日期 | 时间 | module_state_record.created_at |
| 经办人 | 科技处管理员 | state_record_remark |
| 不通过文件 | 必填 | material_version |

是否单独建表：

```text
不需要。
```

---

### 7.13 退还结余经费

节点：

```text
ReturnSurplusFundsTask
ACCEPTANCE_SURPLUS_FUNDS_RETURNING
```

产生：

```text
结余经费退还确认单
结余经费退还凭证
```

表单编码：

```text
SURPLUS_FUNDS_RETURN_FORM
```

字段细节：

| 字段 | 说明 | 来源 |
|---|---|---|
| 项目基础信息 | 引用 M01 | M01 |
| 结余金额 | 应退金额 | `surplus_funds_return_record.surplus_amount` / `acceptance_financial_settlement.surplus_amount` |
| 退还账户 | 学校 / 主管部门账户 | `surplus_funds_return_record.return_account_name` / `return_account_no` / `return_bank_name` |
| 退还时间 | 时间 | `surplus_funds_return_record.returned_at` |
| 财务经办人 | 财务处人员 | state_record_remark |
| 退还结果 | 已退还 / 无需退还 | state_record_remark.result |
| 退还凭证 | 可选 | material_version |

是否单独建表：

```text
第一版可不建表。
V2 建议使用 `surplus_funds_return_record` 保存结余经费退还信息，并与 `acceptance_financial_settlement` 联动。
```

---

## 8. 是否需要新增通用表单表

V2 默认仍不新增通用动态表单表。

原因：

```text
1. 当前已有 project_* 表保存核心业务事实；
2. 材料文件由 material_version 保存；
3. 审核意见由 state_record_remark 保存；
4. 正式单据由 process_document.snapshot_json 固化；
5. 节点材料和单据结构可由 workflow_node_* 配置描述；
6. V1 中大量 payload 字段已由 V2 的 notice_record、state_record_check_item、external_result_record、seal_record、submission_record 等结构化表承接。
```

但如果后续需要：

```text
在线编辑所有表单字段；
每个字段都要保存；
字段可配置；
表单版本可追踪；
表单字段要参与查询统计；
前端根据配置自动渲染表单；
```

则建议新增通用表单实例表。

### 8.1 可选：form_instance

```sql
CREATE TABLE form_instance (
    form_instance_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT,
    node_id VARCHAR(128),
    form_type_code VARCHAR(128) NOT NULL,
    form_title VARCHAR(255) NOT NULL,
    form_status VARCHAR(32) NOT NULL,
    data_json JSON NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    submitted_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);
```

### 8.2 可选：form_template

```sql
CREATE TABLE form_template (
    form_template_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    form_type_code VARCHAR(128) NOT NULL,
    form_title VARCHAR(255) NOT NULL,
    module_type VARCHAR(64),
    schema_json JSON NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    UNIQUE (form_type_code, version_no)
);
```

V2 是否建议新增：

```text
仍不建议一开始新增。
当前 V2 已通过专门结构化表承接主要字段，只有当前端确实要做“动态表单设计器”或“大量表单字段在线配置化”时再新增。
```

---

## 9. 最终建议

V2 落地建议：

```text
1. 表单仍以“虚拟表单组合”为主；
2. 文件型交付物继续走 material / material_version；
3. 审核意见继续走 state_record_remark；
4. 节点材料关联继续走 state_record_material；
5. 正式归档单据继续走 process_document；
6. 原 payload 中的审核检查项改为 state_record_check_item；
7. 原 payload 中的外部审核/批复/盖章结果改为 external_result_record；
8. 原 payload 中的用印字段改为 seal_record；
9. 原 payload 中的上报字段改为 submission_record；
10. 原 payload 中的通知字段改为 notice_record；
11. 原 payload 中的公示字段改为 project_application_publicity；
12. 原 payload 中的经费决算字段改为 acceptance_financial_settlement；
13. 原 payload 中的成果字段改为 project_achievement + project_acceptance_ext；
14. 原 payload 中的结余经费退还字段改为 surplus_funds_return_record；
15. 专家评审继续使用 expert_review_batch / expert_review_assignment / expert_review_score。
```

V2 中 `payload_json` 仅保留为：

```text
原始请求摘要；
调试信息；
临时上下文；
尚未稳定的过渡字段。
```

---

## 10. V2 数据源修正总表

| 原 V1 来源 | V2 修正来源 |
|---|---|
| `payload_json` 中的通知标题、通知编号、通知对象、截止时间 | `notice_record` |
| `payload_json` 中的审核检查项、是否完整、是否合理、是否合规 | `state_record_check_item` |
| `payload_json` 中的审核结论布尔值，如 deptApproved、scienceApproved | `state_record_remark.result`，必要时结合 `state_record_check_item` |
| `payload_json` 中的主管部门文号、外部日期、外部结果、批准金额 | `external_result_record`，部分最终字段同步到 `project_application_ext` / `project_contract_ext` / `project_acceptance_ext` |
| `payload_json` 中的用印原因、用印类型、用印份数、签字盖章状态 | `seal_record` |
| `payload_json` 中的上报对象、上报方式、上报编号、外部回执 | `submission_record` |
| `payload_json` 中的归档位置、归档份数、档案编号 | `archive_record` |
| `payload_json` 中的公示标题、公示范围、异议处理、公示结果 | `project_application_publicity` |
| `payload_json` 中的经费到账、支出、结余、执行率 | `acceptance_financial_settlement` |
| `payload_json` 中的论文数量、专利数量、软著数量、成果明细 | 汇总字段进入 `project_acceptance_ext`，明细进入 `project_achievement` |
| `payload_json` 中的结余经费退还账户、金额、状态 | `surplus_funds_return_record` |
| `payload_json` 中的专家评分、专家结论、最终得分 | `expert_review_batch` / `expert_review_assignment` / `expert_review_score` |
| `payload_json` 中的申请书详细在线字段 | `project_application_detail` |
| `payload_json` 中的合同甲乙方、外部审核结果、签章时间 | `project_contract_ext` |
| `payload_json` 中的结题证书日期、专家最终结果、结题扩展字段 | `project_acceptance_ext` |


最终形成的项目档案包可以由以下内容组成：

```text
项目基础信息
项目申请书
二级单位审核意见
专家评审个人表
专家评审汇总表
科技处审核意见
公示材料
主管部门批复
签字盖章文件
正式报送记录
立项批复
合同草稿
正式生效合同
合同归档单
结题通知
经费决算表
结题报告
成果清单
专家验收报告
验收证书或不通过文件
流程流转记录
全部材料版本
全部正式单据快照
```
