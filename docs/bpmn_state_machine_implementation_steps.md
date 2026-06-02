# 基于 BPMN 状态机科研项目管理系统的推荐实现步骤

## 1. 总体结论

基于当前设计文档，系统实现可以采用“先基础设施、再权限、再流程定义、再业务数据、再状态机、最后 ViewModel 与 SSE”的方式逐步推进。

原始设想为：

```text
（1）后端实现对数据表的增删查改并通过测试
（2）实现用户权限相关内容，完成用户登录、鉴权与权限控制
（3）实现每个业务对数据表的增删查改
（4）接入状态机，完成状态转换
```

该顺序总体是可行的，但需要在第（3）和第（4）之间补充几个关键基础模块：

```text
BPMN 流程定义解析与发布
材料、材料版本、意见、待办、单据等通用运行时能力
ViewModel 组装能力
```

否则后续接入状态机时，会因为缺少流程定义、节点配置、材料要求、待办任务、意见记录、单据配置等基础能力而产生较多返工。

因此，推荐的整体实现顺序为：

```text
（1）数据库基础设施与通用 CRUD
（2）用户、角色、权限、登录与鉴权
（3）BPMN 流程定义解析与发布
（4）业务模块 CRUD，先按“草稿 / 业务事实”实现
（5）材料、意见、待办、单据等通用运行时表
（6）接入状态机，完成状态迁移
（7）ViewModel + SSE + 前端联动
（8）集成测试和端到端流程测试
```

---

## 2. 第 0 阶段：明确技术基线

在正式实现前，应先固定工程技术选型，避免后续频繁调整基础架构。

建议技术基线如下：

```text
后端：Spring Boot
数据库：MySQL 8.0
测试数据库：H2 或 Testcontainers MySQL
持久层：MyBatis / MyBatis-Plus / JPA 三选一
认证：Spring Security + JWT / Session
接口文档：OpenAPI / Swagger
```

如果当前项目已经采用 Spring Boot + MyBatis，则建议继续沿用 MyBatis 或 MyBatis-Plus。

原因是该系统包含较多状态机上下文查询、视图查询、历史记录查询和复杂联表查询，MyBatis 对 SQL 的可控性更强，适合当前设计。

---

## 3. 第 1 阶段：数据库表、实体、Mapper、基础 CRUD

本阶段对应原计划中的：

```text
（1）后端实现对数据表的增删查改并通过测试
```

### 3.1 实现目标

本阶段目标不是实现完整业务，而是先保证数据库结构和基础数据访问能力稳定。

需要完成：

```text
SQL 能成功执行；
所有表能正常插入、查询、更新、删除；
主外键关系正确；
唯一约束正确；
索引可用；
视图能正常查询；
H2 / MySQL 测试能跑通。
```

### 3.2 优先实现的数据表

#### 3.2.1 权限管理表

```text
department
app_user
role
permission
user_role
role_permission
```

#### 3.2.2 BPMN 状态机配置表

```text
workflow_definition
workflow_node
workflow_node_material_requirement
workflow_node_document_config
```

#### 3.2.3 项目与业务数据表

```text
project
project_member
project_application
project_contract
project_acceptance
```

#### 3.2.4 状态机运行时表

```text
project_module_instance
module_state_record
state_record_remark
state_record_material
task_instance
```

#### 3.2.5 材料与单据表

```text
material_type
material
material_version
process_document
process_document_file
```

### 3.3 需要创建的视图

```text
v_user_role_detail
v_workflow_node_config
v_project_module_current_state
v_module_runtime_context
v_state_record_context
v_material_context
```

其中最关键的是：

```text
v_project_module_current_state
v_module_runtime_context
```

因为后续状态机需要依赖当前状态视图判断模块实例当前所处节点。

### 3.4 推荐测试

建议优先编写以下测试：

```text
DepartmentMapperTest
UserRoleMapperTest
WorkflowDefinitionMapperTest
ProjectMapperTest
ProjectModuleInstanceMapperTest
ModuleStateRecordMapperTest
MaterialMapperTest
ViewQueryTest
```

其中 `module_state_record` 和当前状态视图的测试最重要。

测试示例：

```text
插入状态记录：

seq = 1, to_state = APPLICATION_DRAFT
seq = 2, to_state = APPLICATION_DEPT_REVIEWING
seq = 3, to_state = APPLICATION_SCIENCE_REVIEWING

然后查询当前状态视图，应返回：

APPLICATION_SCIENCE_REVIEWING
```

该测试必须稳定通过，因为后续状态机、ViewModel 和权限判断都依赖它。

---

## 4. 第 2 阶段：用户登录、RBAC、权限控制

本阶段对应原计划中的：

```text
（2）实现用户权限相关内容，完成用户登录，鉴权与权限控制
```

### 4.1 实现目标

本阶段要完成系统的基础安全边界，避免后续状态迁移接口直接暴露给无权限用户。

建议先实现以下接口：

```http
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
```

登录成功后，后端应能获取当前用户的完整身份上下文：

```text
userId
username
realName
deptId
roleCodes
permissionCodes
```

### 4.2 权限判断服务

建议实现统一的 `PermissionService`：

```java
public class PermissionService {

    boolean hasRole(Long userId, String roleCode);

    boolean hasPermission(Long userId, String permissionCode);

    boolean canAccessProject(Long userId, Long projectId);

    boolean canOperateModuleNode(Long userId, Long moduleInstanceId);

}
```

### 4.3 权限判断层次

不能只做菜单权限，还必须做项目访问权限和流程节点权限。

至少应包含四层判断：

```text
1. 用户是否登录；
2. 用户是否有接口权限；
3. 用户是否能访问当前项目；
4. 用户角色是否匹配当前 BPMN 节点 candidateRoleCode。
```

例如当前 BPMN 节点配置为：

```text
candidateRoleCode = DEPT_ADMIN
```

则只有拥有 `DEPT_ADMIN` 角色的用户可以处理。

如果当前节点是项目负责人节点，则还需要额外判断：

```text
当前用户是否等于 project.leader_user_id
```

### 4.4 本阶段完成标准

本阶段应完成：

```text
登录鉴权；
角色权限查询；
接口拦截；
项目访问控制；
当前节点候选角色判断；
权限单元测试；
接口权限集成测试。
```

---

## 5. 第 3 阶段：BPMN 流程定义解析与发布

本阶段建议放在业务 CRUD 之前或与业务 CRUD 并行推进，而不是等到最后才做。

### 5.1 为什么 BPMN 发布要提前

业务模块最终都要绑定以下流程配置：

```text
workflow_definition
workflow_node
state_code
candidate_role_code
materialRequirement
documentConfig
transition rule
```

如果不先实现 BPMN 解析发布，后续业务模块中的状态、按钮、材料、单据都缺少配置来源，后面接入状态机时容易返工。

### 5.2 推荐接口

建议实现以下流程定义管理接口：

```http
POST /api/workflow-definitions/upload
POST /api/workflow-definitions/{id}/validate
POST /api/workflow-definitions/{id}/publish
GET  /api/workflow-definitions
GET  /api/workflow-definitions/{id}
GET  /api/workflow-definitions/{id}/bpmn
GET  /api/workflow-definitions/{id}/nodes
GET  /api/workflow-definitions/{id}/transitions
```

### 5.3 BPMN 发布器职责

BPMN 发布器需要完成：

```text
读取 BPMN XML；
解析 rm:workflowNode；
生成 workflow_node；
解析 rm:materialRequirement；
生成 material_type 和 workflow_node_material_requirement；
解析 rm:documentConfig；
生成 workflow_node_document_config；
解析 rm:transition；
生成 state_machine_rules_json；
校验 stateCode 是否唯一；
校验 candidateRoleCode 是否存在；
校验 eventType 是否完整；
发布 workflow_definition 为 ACTIVE。
```

### 5.4 本阶段完成标准

本阶段完成后，创建模块实例时应可以根据模块类型自动绑定：

```text
module_type → 最新 ACTIVE workflow_definition
```

例如：

```text
APPLICATION → APPLICATION_PROCESS 最新 ACTIVE 版本
CONTRACT    → CONTRACT_PROCESS 最新 ACTIVE 版本
ACCEPTANCE  → ACCEPTANCE_PROCESS 最新 ACTIVE 版本
```

---

## 6. 第 4 阶段：项目与业务模块 CRUD

本阶段对应原计划中的：

```text
（3）实现每个业务对数据表的增删查改
```

但需要注意一个重要原则：

> 业务 CRUD 先只负责保存业务事实，不直接推进流程状态。

### 6.1 实现内容

需要实现以下业务表的基础增删查改：

```text
project
project_member
project_application
project_contract
project_acceptance
```

对应业务能力包括：

```text
项目基础信息维护；
项目成员维护；
项目申报表单保存；
纵向合同信息保存；
项目结题信息保存。
```

### 6.2 推荐接口

```http
POST /api/projects
GET  /api/projects
GET  /api/projects/{projectId}
PUT  /api/projects/{projectId}

POST /api/projects/{projectId}/modules
GET  /api/projects/{projectId}/modules
GET  /api/module-instances/{id}

GET  /api/module-instances/{id}/business-data
PUT  /api/module-instances/{id}/draft
```

### 6.3 草稿与状态机的关系

`PUT /api/module-instances/{id}/draft` 只保存业务数据，不写入 `module_state_record`。

推荐划分如下：

```text
保存草稿：只调用 BusinessService，不触发状态机；
正式提交：BusinessService 保存业务数据后，调用 StateMachineRuntime。
```

这样可以避免“保存草稿”和“正式提交”混在一起，降低状态管理复杂度。

---

## 7. 第 5 阶段：材料、意见、待办、单据基础能力

本阶段建议在状态机正式接入之前完成。

### 7.1 原因

状态机执行时会依赖以下运行时能力：

```text
material
material_version
state_record_remark
state_record_material
task_instance
process_document
process_document_file
```

如果这些能力没有提前准备好，状态机只能写状态记录，无法完整实现材料校验、意见记录、待办流转和单据生成。

### 7.2 材料接口

建议实现：

```http
POST /api/projects/{projectId}/materials
GET  /api/projects/{projectId}/materials
POST /api/materials/{materialId}/versions
GET  /api/materials/{materialId}/versions
GET  /api/material-versions/{versionId}/download
DELETE /api/material-versions/{versionId}
```

### 7.3 待办接口

可以先实现查询接口：

```http
GET /api/tasks/my
GET /api/tasks/candidate
GET /api/tasks/{taskId}
```

后续再扩展：

```http
POST /api/tasks/{taskId}/claim
POST /api/tasks/{taskId}/unclaim
POST /api/tasks/{taskId}/complete
```

### 7.4 单据接口

可以先实现查询接口：

```http
GET /api/module-instances/{id}/documents
GET /api/documents/{documentId}
GET /api/documents/{documentId}/files
```

### 7.5 状态机依赖的服务能力

本阶段结束时，应至少具备以下服务能力：

```java
remarkService.createOperatorRemark(...);

materialLinkService.linkMaterialsToRecord(...);

taskService.closeCurrentTask(...);

taskService.createNextTask(...);

documentService.generateIfNeeded(...);
```

---

## 8. 第 6 阶段：接入状态机，完成状态转换

本阶段对应原计划中的：

```text
（4）接入状态机，完成状态转换
```

状态机建议分阶段实现，而不是一开始就实现所有复杂能力。

### 8.1 第一阶段：最小状态机

先支持最基础的状态迁移：

```text
current_state + eventType → to_state
```

例如：

```text
APPLICATION_DRAFT
  -- USER_CONFIRMED_SUBMIT -->
APPLICATION_DEPT_REVIEWING
```

执行时至少写入：

```text
module_state_record
state_record_remark
task_instance
```

### 8.2 第二阶段：支持 Gateway 条件

再支持简单条件判断：

```text
conditionType = SIMPLE_BOOL
conditionKey
conditionValue
```

例如：

```text
deptApproved = true  → 科技处审核
deptApproved = false → 退回项目负责人
```

### 8.3 第三阶段：支持扩展 Handler

最后支持可插拔扩展点：

```text
Validator
ConditionHandler
ActionHandler
```

默认情况走通用逻辑，复杂情况再使用：

```text
validatorKey
conditionHandlerKey
actionKeys
```

### 8.4 推荐状态机核心接口

```java
@Transactional
public TransitionResult fire(
    Long moduleInstanceId,
    String eventType,
    Long currentUserId,
    String remark,
    Map<String, Object> payload,
    List<Long> materialVersionIds,
    Integer expectedSeq
)
```

建议加入 `expectedSeq`，用于防止重复提交和并发状态错乱。

### 8.5 状态机执行链路

完整状态迁移建议按照如下顺序执行：

```text
1. 锁定 project_module_instance；
2. 查询 v_module_runtime_context；
3. 校验 expectedSeq；
4. 校验当前用户是否可操作当前节点；
5. 根据 workflow_definition_id 加载规则；
6. 匹配 current_state + eventType；
7. 执行条件判断；
8. 执行材料校验；
9. 计算 next_seq、next_round_no；
10. 插入 module_state_record；
11. 插入 state_record_remark；
12. 插入 state_record_material；
13. 关闭当前 task_instance；
14. 创建下一节点 task_instance；
15. 执行 ActionHandler；
16. 生成 process_document；
17. 发布领域事件；
18. 事务提交后发送 SSE。
```

---

## 9. 第 7 阶段：ViewModel 组装器

状态机接入后，需要通过 ViewModel 驱动前端页面展示。

### 9.1 ViewModel 需要支撑的页面内容

前端需要动态展示：

```text
当前节点；
当前状态；
当前可执行按钮；
当前材料要求；
当前用户是否可操作；
BPMN 高亮；
时间线；
意见；
附件；
单据；
待办。
```

### 9.2 推荐接口

```http
GET /api/module-instances/{id}/view-model
GET /api/module-instances/{id}/bpmn
```

### 9.3 ViewModel 组装器查询来源

ViewModel 组装器内部应查询：

```text
v_module_runtime_context
workflow_node
state_machine_rules_json
workflow_node_material_requirement
workflow_node_document_config
task_instance
module_state_record
state_record_remark
state_record_material
material_version
process_document
```

### 9.4 实现原则

前端不应写死流程逻辑。

前端应根据后端返回的 ViewModel 渲染：

```text
按钮；
材料要求；
是否可操作；
当前节点高亮；
历史时间线；
单据；
附件。
```

状态判断和权限判断必须以后端结果为准。

---

## 10. 第 8 阶段：SSE 通知与前端刷新

SSE 不建议太早实现，因为它依赖状态机和 ViewModel 已经稳定。

### 10.1 推荐接口

```http
GET /api/sse/subscribe
```

### 10.2 SSE 事件格式

状态迁移事务提交后，后端发送轻量事件：

```json
{
  "type": "MODULE_STATE_CHANGED",
  "projectId": 1,
  "moduleInstanceId": 101,
  "moduleType": "APPLICATION",
  "fromState": "APPLICATION_DRAFT",
  "toState": "APPLICATION_DEPT_REVIEWING",
  "seq": 2,
  "eventType": "USER_CONFIRMED_SUBMIT",
  "occurredAt": "2026-06-02 21:30:00"
}
```

### 10.3 前端处理原则

前端收到 SSE 后，不应直接修改页面状态，而是重新请求：

```http
GET /api/module-instances/{id}/view-model
```

也就是说：

```text
SSE 只通知“发生了变化”；
ViewModel 才是页面最终状态来源。
```

这样可以保证页面状态始终以后端为准。

---

## 11. 第 9 阶段：端到端测试

最后阶段需要做完整流程测试。

### 11.1 主流程测试

至少测试三条主流程：

```text
项目申报 APPLICATION
纵向合同 CONTRACT
项目结题 ACCEPTANCE
```

每条流程至少覆盖：

```text
创建项目；
启动模块实例；
保存草稿；
项目负责人提交；
二级单位审核通过；
科技处审核通过；
外部结果代录；
流程结束；
生成单据；
历史记录正确；
当前状态正确；
待办正确关闭和生成；
权限控制正确；
SSE 事件正确发布。
```

### 11.2 退回路径测试

必须测试退回和重新提交路径：

```text
项目负责人提交；
二级单位退回；
项目负责人重新提交；
轮次 round_no + 1；
历史时间线正确；
材料版本正确关联。
```

### 11.3 并发测试

建议测试：

```text
同一节点重复提交；
两个用户同时审核；
expectedSeq 不一致；
重复点击按钮；
事务回滚后不发送 SSE；
状态迁移失败后不生成待办和单据。
```

---

## 12. 对原实现顺序的修正建议

### 12.1 原方案

```text
（1）后端实现对数据表的增删查改并通过测试
（2）实现用户权限相关内容，完成用户登录，鉴权与权限控制
（3）实现每个业务对数据表的增删查改
（4）接入状态机，完成状态转换
```

### 12.2 推荐修正版

```text
（1）实现基础数据库表、Mapper、Repository、通用 CRUD，并通过单元测试；
（2）实现用户、角色、权限、登录、鉴权、项目访问控制、节点候选角色判断；
（3）实现 BPMN 解析发布，将 BPMN 扩展标签落入 workflow_definition / workflow_node / material requirement / document config；
（4）实现项目、申报、合同、结题等业务数据 CRUD，但先只作为草稿和业务事实，不直接推进流程；
（5）实现材料、材料版本、意见、待办、单据等状态机依赖的通用能力；
（6）实现 StateMachineRuntime，完成状态转换、轮次、Gateway、材料校验、意见记录、待办切换、单据生成；
（7）实现 ViewModel 组装接口，让前端根据后端返回结果渲染页面；
（8）实现 SSE，状态变化后通知前端重新拉取 ViewModel；
（9）补充完整端到端测试。
```

---

## 13. 最小闭环优先级

如果希望尽快跑通第一条完整流程，建议先实现最小闭环。

### 13.1 最小闭环步骤

```text
1. 建表 + Mapper + 当前状态视图；
2. 登录 + RBAC；
3. BPMN 解析 APPLICATION_PROCESS；
4. project + project_application + project_module_instance；
5. 最小 StateMachineRuntime；
6. module_state_record + state_record_remark + task_instance；
7. ViewModel；
8. 前端提交 / 审核 / 退回。
```

### 13.2 暂时可以延后的内容

第一条流程跑通前，可以先不要急着实现：

```text
复杂材料版本；
正式单据模板；
专家多意见；
完整合同流程；
完整结题流程；
SSE；
复杂 ActionHandler；
复杂 ConditionHandler。
```

第一条完整链路跑通后，再复制扩展到：

```text
CONTRACT
ACCEPTANCE
```

这样风险最低。

---

## 14. 实现过程中的关键注意点

### 14.1 业务 CRUD 不要直接修改流程状态

业务表只保存业务事实，例如申报内容、合同内容、结题内容。

流程状态应由：

```text
module_state_record
v_project_module_current_state
v_module_runtime_context
```

共同表达。

### 14.2 权限不能只做菜单权限

必须同时实现：

```text
接口权限；
项目访问权限；
流程节点候选角色权限；
项目负责人身份判断；
待办任务处理人判断。
```

### 14.3 状态机接入前必须准备好运行时基础表

至少要准备：

```text
workflow_definition
workflow_node
project_module_instance
module_state_record
state_record_remark
task_instance
```

否则状态机无法完成完整闭环。

### 14.4 状态迁移必须使用事务

一次状态迁移中涉及：

```text
业务数据保存；
状态记录追加；
意见记录；
材料关联；
待办关闭与创建；
单据生成；
领域事件发布。
```

这些操作应在同一事务内完成。

SSE 应在事务提交后发送，不能在事务提交前发送。

### 14.5 前端最终状态以后端 ViewModel 为准

前端不要根据按钮、SSE 或本地状态自行推断当前状态。

正确方式是：

```text
用户操作后，接口返回最新 ViewModel；
其他页面收到 SSE 后，重新拉取 ViewModel；
页面根据 ViewModel 重新渲染。
```

---

## 15. 最终建议

整体实现路径建议总结为：

```text
先做表和基础 CRUD；
再做权限；
再做 BPMN 发布；
再做业务草稿 CRUD；
再做材料 / 待办 / 意见 / 单据；
再接状态机；
最后做 ViewModel、SSE 和端到端测试。
```

该路径的优点是：

```text
基础依赖清晰；
每一阶段都可以独立测试；
状态机不会过早和业务 CRUD 混在一起；
流程定义、权限、材料、待办、单据都有稳定支撑；
后续扩展 CONTRACT、ACCEPTANCE、PROCESS 等模块时复用性更好。
```

一句话概括：

> 先把“数据、权限、流程定义、运行时支撑”打牢，再让状态机推进流程，最后用 ViewModel 和 SSE 驱动前端刷新。
