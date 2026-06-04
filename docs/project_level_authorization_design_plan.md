# 项目级授权与权限管理业务设计计划

## 1. 目标

在当前“登录 + JWT + RBAC 基础判定”之上，补齐权限管理业务设计，重点回答三个问题：

1. 基于 `docs/bpmn_auxiliary_tags_augmented/`，哪些处理能力需要被建模为“项目级授权”；
2. 项目级授权能否设计为一种统一机制，由高级角色向低级角色授权；
3. 当前数据库与运行时设计是否已经具备实现该机制的基础，如果具备，后续应如何分阶段落地。

本计划只做设计与实施规划，不直接修改 BPMN 或后端运行时逻辑。

## 2. 当前实现与设计基础

### 2.1 已具备的基础

当前设计中，以下结构已经为“项目级授权”提供了实现基础：

1. 全局 RBAC 基础：
   `app_user`、`role`、`user_role`、`permission`、`role_permission` 已能表达“谁具备某类系统级角色”。
2. 项目上下文基础：
   `project.leader_user_id`、`project.dept_id`、`project_member` 已能表达“谁属于该项目”以及“谁是项目负责人”。
3. 节点处理约束基础：
   `workflow_node.candidate_role_code` 已能表达“该 BPMN 节点需要什么候选角色”。
4. 任务落人基础：
   `task_instance.assignee_user_id` 已能表达“任务明确指派给某个用户”；
   `task_instance.candidate_role_code` 已能表达“任务面向某个候选角色”。
5. 历史审计基础：
   `state_record_remark.participant_type` 已支持 `EXPERT / PROXY_OPERATOR / APPROVER / OPERATOR`，能记录专家评审和代录场景。
6. 外部主体代录基础：
   `workflow_node.represented_actor_code` 与 `represented_actor_name` 已能表达“系统用户代录外部主体结果”。

### 2.2 当前仍然缺失的部分

当前系统还没有以下能力：

1. “谁可以给某项目授权谁做什么”的数据结构。
2. 项目级授权的授予、撤销、失效、审计日志。
3. 运行时将“全局角色”与“项目级授权”合并判定的统一规则。
4. 管理员和科技处管理员的授权管理界面与接口。
5. 基于项目级授权自动生成或限制专家任务的运行时逻辑。

结论：当前基础**足以支持实现项目级授权机制**，但还缺一层独立的“授权关系模型”和“授权管理业务”。

## 3. 从 BPMN 推导出的项目级授权需求

### 3.1 当前 BPMN 中出现的候选处理角色

从三条 BPMN 流程看，实际出现的候选角色包括：

1. `PROJECT_LEADER`
2. `DEPT_ADMIN`
3. `SCIENCE_ADMIN`
4. `EXPERT`
5. `FINANCE_ADMIN`

其中前四类已经在合并设计文档的权限部分被明确提及；`FINANCE_ADMIN` 已出现在 BPMN 中，但尚未纳入当前权限设计与实现，是一个需要补齐的设计缺口。

### 3.2 哪些属于系统级角色，哪些属于项目级授权

建议区分为两层：

1. 系统级角色：
   `SYSTEM_ADMIN`、`SCIENCE_ADMIN`、`DEPT_ADMIN`、`FINANCE_ADMIN`、`EXPERT`
2. 项目级授权：
   某个用户在某个项目、某个模块、某个周期内，被授权以某种项目身份执行某类动作

这意味着：

1. `SCIENCE_ADMIN`、`DEPT_ADMIN`、`FINANCE_ADMIN` 更适合作为“系统级资格角色”。
2. `PROJECT_LEADER` 和“某项目评审专家”明显带项目上下文，应被视为项目级授权。
3. `EXPERT` 更适合作为“专家资格角色”，而“是否是这个项目的评审专家”应由项目级授权决定。

### 3.3 需要建模为项目级授权的场景

基于当前 BPMN，建议先纳入以下项目级授权：

| 项目级授权代码 | 说明 | 来源依据 | 是否当前 BPMN 必需 |
|---|---|---|---|
| `PROJECT_LEADER_BINDING` | 某用户是该项目负责人 | `project.leader_user_id` + 多个 `PROJECT_LEADER` 节点 | 是 |
| `PROJECT_MEMBER_BINDING` | 某用户属于该项目成员 | `project_member` | 是，影响项目访问与协作 |
| `PROJECT_EXPERT_ASSIGNMENT` | 某用户被指定为该项目的评审专家 | `APPLICATION_SCIENCE_EXPERT_REVIEWING`、`ACCEPTANCE_EXPERT_REVIEWING` | 是 |
| `PROJECT_MODULE_EXPERT_ASSIGNMENT` | 某用户被指定为某项目某模块的评审专家 | 申报专家评审与结题专家评审不一定同人 | 是，推荐作为实际落地粒度 |
| `PROJECT_PROXY_RECORDER_ASSIGNMENT` | 某用户被允许代录该项目外部主体结果 | 多个 `PROXY_INPUT` 节点 | 否，当前可先由 `SCIENCE_ADMIN` 全局处理 |
| `PROJECT_FINANCE_HANDLER_ASSIGNMENT` | 某用户被指定处理该项目财务节点 | `ACCEPTANCE_FINANCIAL_SETTLEMENT`、`ACCEPTANCE_SURPLUS_FUNDS_RETURNING` | 否，当前 BPMN 可先按全局 `FINANCE_ADMIN` 处理 |

### 3.4 本阶段推荐先实现的项目级授权范围

为了避免一次性设计过重，建议第一阶段只正式落地三类：

1. `PROJECT_LEADER_BINDING`
2. `PROJECT_MEMBER_BINDING`
3. `PROJECT_MODULE_EXPERT_ASSIGNMENT`

原因：

1. 这三类与当前 BPMN 主路径直接相关。
2. 其中 `PROJECT_MODULE_EXPERT_ASSIGNMENT` 正是“科技处管理员可分配某成员为某项目评审专家”的核心场景。
3. 其余代理录入、财务处理可以先由系统级角色承担，后续再扩展为项目级授权。

## 4. 推荐的权限模型分层

### 4.1 三层权限结构

建议后续权限模型固定为三层：

1. 系统级资格角色：
   用户在全系统范围内拥有什么资格。
   例如：`SCIENCE_ADMIN`、`DEPT_ADMIN`、`FINANCE_ADMIN`、`EXPERT`。
2. 项目级授权关系：
   某用户在某项目或某模块内被授予什么项目身份。
   例如：某人是该项目负责人、某人是该项目结题专家。
3. 节点运行时判定：
   BPMN 节点到达时，后端同时判断：
   全局角色是否匹配；
   项目级授权是否有效；
   当前任务是否明确指派给本人；
   部门、项目、轮次、模块等上下文是否匹配。

### 4.2 一个关键原则

`EXPERT` 不应再直接理解为“所有拥有专家角色的人都能处理所有专家评审节点”。

更合理的解释应为：

1. `EXPERT` 表示该用户具备专家资格，可以被纳入专家池。
2. `PROJECT_MODULE_EXPERT_ASSIGNMENT` 表示该用户被授权为某项目某模块当前轮次的实际评审专家。
3. 只有同时满足两者，或者任务被明确指派到该用户，才能真正处理专家评审节点。

这套规则与当前 `task_instance.assignee_user_id` 很契合。

## 5. 项目级授权机制是否可行

### 5.1 结论

可行，而且与当前设计兼容。

### 5.2 可行性的原因

当前模型已经具备三块关键基础：

1. “角色资格”已经有：
   `user_role` 可表达用户是否拥有 `SCIENCE_ADMIN`、`EXPERT` 等系统资格。
2. “项目上下文”已经有：
   `project` 与 `project_member` 可表达项目、负责人、成员。
3. “任务落人”已经有：
   `task_instance` 可表达候选角色与明确指派用户。

因此只需补上一层“项目级授权关系表”，就能完成：

```text
系统级资格角色
    +
项目级授权关系
    +
任务实例
    =
最终节点操作权限
```

### 5.3 为什么说“由高级角色向低级角色授权”也可行

这是可行的，因为该机制本质上不是“角色继承”，而是“授权关系”。

建议不要做成：

```text
SCIENCE_ADMIN > EXPERT
DEPT_ADMIN > PROJECT_MEMBER
```

而要做成：

```text
拥有某类上位管理角色的人
可以在其管理边界内
授予其他用户某类项目级身份
```

例如：

1. `SYSTEM_ADMIN` 可授予任意项目授权。
2. `SCIENCE_ADMIN` 可在其管辖范围内授予项目负责人、项目专家等项目级授权。
3. `DEPT_ADMIN` 可在本部门项目内维护项目成员或推荐候选专家，但不必拥有专家资格本身。

这是一种“管理权限”而不是“角色自动降级”。

## 6. 推荐的数据模型

### 6.1 不建议直接复用 `project_member` 承载全部授权

`project_member` 适合表达“项目成员关系”，但不适合完整承载授权治理，原因有三点：

1. 缺少授权人信息。
2. 缺少生效时间、失效时间、撤销状态。
3. 缺少模块范围、轮次范围、授权原因、审计记录。

因此建议：

1. `project_member` 保留为“业务上的项目成员关系”。
2. 新增独立的“项目级授权关系表”。

### 6.2 推荐新增表

#### 6.2.1 `project_role_grant`

用于记录项目级授权关系。

建议字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `project_role_grant_id` | BIGINT PK | 主键 |
| `project_id` | BIGINT FK | 项目 ID |
| `module_type` | VARCHAR NULL | 模块范围，可空；为空表示项目级 |
| `grant_role_code` | VARCHAR | 项目级授权代码 |
| `grantee_user_id` | BIGINT FK | 被授权用户 |
| `granted_by_user_id` | BIGINT FK | 授权人 |
| `grant_scope` | VARCHAR | `PROJECT / MODULE / ROUND / TASK` |
| `round_no` | INT NULL | 适用于专家按轮次授权 |
| `task_node_id` | VARCHAR NULL | 适用于节点级定向授权 |
| `status` | VARCHAR | `ACTIVE / REVOKED / EXPIRED` |
| `effective_from` | DATETIME | 生效时间 |
| `effective_to` | DATETIME NULL | 失效时间 |
| `grant_reason` | TEXT NULL | 授权原因 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

#### 6.2.2 `project_role_grant_log`

用于记录授权历史操作。

建议字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `grant_log_id` | BIGINT PK | 主键 |
| `project_role_grant_id` | BIGINT FK | 对应授权记录 |
| `action_type` | VARCHAR | `GRANT / REVOKE / EXPIRE / UPDATE_SCOPE` |
| `operator_user_id` | BIGINT FK | 操作人 |
| `before_snapshot_json` | JSON | 变更前快照 |
| `after_snapshot_json` | JSON | 变更后快照 |
| `remark` | TEXT | 备注 |
| `created_at` | DATETIME | 创建时间 |

### 6.3 哪些关系继续保留现状

1. `project.leader_user_id`：
   继续作为项目负责人主字段，避免双写。
2. `project_member`：
   继续作为项目协作成员关系。
3. `task_instance.assignee_user_id`：
   继续作为运行时最终落人机制。

换句话说：

1. 项目负责人是“结构化主数据”。
2. 项目成员是“业务协作关系”。
3. 项目级授权是“权限治理关系”。
4. `task_instance` 是“运行时执行关系”。

## 7. 运行时判定规则设计

### 7.1 节点权限判定总规则

后端运行时建议按以下顺序判断：

1. 用户是否拥有接口权限；
2. 用户是否能访问该项目；
3. 节点要求的是系统级角色、项目级授权，还是两者组合；
4. 如果任务已明确指派给某人，是否等于当前用户；
5. 如果节点属于项目级专家节点，是否存在有效的 `project_role_grant`；
6. 如果节点属于项目负责人节点，是否等于 `project.leader_user_id`；
7. 如果节点属于部门管理员节点，是否为本部门 `DEPT_ADMIN`；
8. 如果节点属于科技处管理员节点，是否拥有 `SCIENCE_ADMIN`。

### 7.2 对当前 BPMN 的建议解释

| 节点候选角色 | 建议运行时解释 |
|---|---|
| `PROJECT_LEADER` | 当前用户必须等于 `project.leader_user_id` |
| `DEPT_ADMIN` | 当前用户必须拥有 `DEPT_ADMIN`，且与项目部门匹配 |
| `SCIENCE_ADMIN` | 当前用户必须拥有 `SCIENCE_ADMIN` |
| `FINANCE_ADMIN` | 当前用户必须拥有 `FINANCE_ADMIN` |
| `EXPERT` | 当前用户必须拥有 `EXPERT`，且存在有效项目级专家授权或任务明确指派 |

### 7.3 与 `task_instance` 的结合方式

建议对专家类任务采用“双保险”：

1. BPMN 节点候选角色仍保留 `EXPERT`。
2. 创建任务时将 `assignee_user_id` 直接写入被分配的专家。

这样运行时既能通过候选角色表达“这是专家节点”，也能通过任务落人确保“只有被分配的专家真正收到待办”。

## 8. 推荐的授权边界

### 8.1 谁可以授予什么

建议先固定为以下边界：

| 授权人角色 | 可授予项目级授权 |
|---|---|
| `SYSTEM_ADMIN` | 全部项目级授权 |
| `SCIENCE_ADMIN` | `PROJECT_LEADER_BINDING`、`PROJECT_MODULE_EXPERT_ASSIGNMENT`、`PROJECT_MEMBER_BINDING` |
| `DEPT_ADMIN` | `PROJECT_MEMBER_BINDING`；可推荐专家，但不直接生效 |

### 8.2 为什么不建议让低级角色直接互授

因为当前业务需要的是“责任可追溯”，而不是“自由转授”。

如果允许项目负责人或专家继续转授，会带来三个问题：

1. 审批链条模糊。
2. 责任归属不清。
3. 专家评审公正性难审计。

因此第一版建议：

1. 只允许上位管理角色授予。
2. 被授权人不允许继续转授。
3. 所有授权操作写审计日志。

## 9. 对 BPMN 的影响建议

### 9.1 第一阶段不强制改 BPMN

当前 BPMN 已有 `candidateRoleCode`，第一阶段可以不修改 BPMN，只在运行时增加一层授权解释规则：

1. `PROJECT_LEADER` 解释为结构化项目负责人。
2. `EXPERT` 解释为“专家资格 + 项目级授权”。
3. `SCIENCE_ADMIN` / `DEPT_ADMIN` / `FINANCE_ADMIN` 解释为系统级角色。

### 9.2 第二阶段可选增强

后续如果希望把授权语义直接体现在 BPMN 中，可以给 `rm:workflowNode` 增加可选扩展属性：

| 字段 | 说明 |
|---|---|
| `authorizationScope` | `SYSTEM_ROLE / PROJECT_ROLE / HYBRID` |
| `grantRoleCode` | 对应项目级授权代码 |
| `assignmentStrategy` | `ROLE_ONLY / TASK_ASSIGNEE / PROJECT_GRANT_AND_TASK` |

这会让 BPMN 与运行时解释更显式，但不是第一阶段必需条件。

## 10. 实施计划

### 10.1 第一阶段：权限模型补全

目标：把“系统级角色”和“项目级授权”拆开。

工作项：

1. 在设计文档中补充 `FINANCE_ADMIN`。
2. 新增 `project_role_grant`、`project_role_grant_log` 表设计。
3. 定义项目级授权代码枚举。
4. 定义授权边界与授予规则。

交付物：

1. 数据库设计更新。
2. 权限判定规则文档更新。

### 10.2 第二阶段：后端授权服务

目标：形成统一的项目级授权查询与判定服务。

工作项：

1. 新增 `ProjectAuthorizationService`。
2. 提供：
   `hasProjectGrant(userId, projectId, grantRoleCode)`
   `hasModuleGrant(userId, projectId, moduleType, grantRoleCode, roundNo)`
3. 改造当前 `PermissionService.canOperateModuleNode(...)`，让其支持项目级授权判定。
4. 为专家节点增加“项目级授权 + task assignee”的组合判断。

交付物：

1. 服务代码。
2. 单元测试。
3. 集成测试。

### 10.3 第三阶段：授权管理接口

目标：让高级角色能实际授予或撤销项目级授权。

工作项：

1. 新增项目成员管理接口。
2. 新增项目负责人调整接口。
3. 新增项目专家分配接口。
4. 新增授权记录查询接口。

建议接口：

```http
GET  /api/projects/{projectId}/authorizations
POST /api/projects/{projectId}/authorizations
POST /api/projects/{projectId}/authorizations/{grantId}/revoke
POST /api/projects/{projectId}/leader/change
POST /api/projects/{projectId}/experts/assign
```

### 10.4 第四阶段：任务生成与运行时联动

目标：授权结果真正影响待办和节点操作。

工作项：

1. 对专家节点执行 `ASSIGN_EXPERT_TASKS` 时，按 `project_role_grant` 生成任务。
2. 对负责人节点从 `project.leader_user_id` 自动落人。
3. 对项目成员和专家撤销时，处理未完成待办的回收策略。

### 10.5 第五阶段：前端管理界面

目标：形成可操作的权限管理业务页。

建议页面：

1. 系统角色管理页。
2. 项目成员与负责人管理页。
3. 项目专家授权页。
4. 项目授权历史页。

## 11. 推荐落地顺序

建议优先级如下：

1. 先补设计与表结构。
2. 再补后端授权查询与授权管理接口。
3. 再补专家分配与任务生成联动。
4. 最后补前端管理页。

如果只优先打通一个高价值场景，建议先做：

```text
科技处管理员为某项目某模块分配评审专家
→ 生成专家任务
→ 只有被分配专家可以处理 EXPERT 节点
```

这条链路最能验证“项目级授权机制”是否成立。

## 12. 最终结论

结论如下：

1. 当前系统已经有足够基础实现项目级授权机制。
2. 当前 BPMN 中真正迫切需要项目级授权的核心场景是“项目负责人”和“项目评审专家”。
3. 最合理的模型不是把高级角色和低级角色做成继承关系，而是做成“上位管理角色授予项目级身份”的授权关系。
4. `EXPERT` 应被拆解为“专家资格角色”与“项目实际授权”两层。
5. 第一阶段应优先落地 `PROJECT_LEADER_BINDING`、`PROJECT_MEMBER_BINDING`、`PROJECT_MODULE_EXPERT_ASSIGNMENT` 三类项目级授权。

这套方案既兼容当前 RBAC，也兼容当前 BPMN 与状态机设计，不需要推翻现有模型，只需要在其上补齐“项目级授权关系层”。
