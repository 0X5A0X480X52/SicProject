# 后端项目结构设计与当前结构差异分析

## 1. 当前项目结构概况

当前后端项目包路径为：

```text
com.amatrix.sicprojectis_backend
```

当前目录结构如下：

```text
com/amatrix/sicprojectis_backend
├── SicProjectIsBackendApplication.java
│
├── controller
│
├── dao
│   ├── AppUserDao.java
│   ├── DepartmentDao.java
│   ├── MaterialContextViewDao.java
│   ├── MaterialDao.java
│   ├── MaterialTypeDao.java
│   ├── MaterialVersionDao.java
│   ├── ModuleRuntimeContextViewDao.java
│   ├── ModuleStateRecordDao.java
│   ├── PermissionDao.java
│   ├── ProcessDocumentDao.java
│   ├── ProcessDocumentFileDao.java
│   ├── ProjectAcceptanceDao.java
│   ├── ProjectApplicationDao.java
│   ├── ProjectContractDao.java
│   ├── ProjectDao.java
│   ├── ProjectMemberDao.java
│   ├── ProjectModuleInstanceDao.java
│   ├── RoleDao.java
│   ├── RolePermissionDao.java
│   ├── StateRecordContextViewDao.java
│   ├── StateRecordMaterialDao.java
│   ├── StateRecordRemarkDao.java
│   ├── TaskInstanceDao.java
│   ├── UserRoleDao.java
│   ├── UserRoleDetailViewDao.java
│   ├── WorkflowDefinitionDao.java
│   ├── WorkflowNodeConfigViewDao.java
│   ├── WorkflowNodeDao.java
│   ├── WorkflowNodeDocumentConfigDao.java
│   └── WorkflowNodeMaterialRequirementDao.java
│
├── entity
│   ├── AppUser.java
│   ├── Department.java
│   ├── Material.java
│   ├── MaterialContextView.java
│   ├── MaterialType.java
│   ├── MaterialVersion.java
│   ├── ModuleRuntimeContextView.java
│   ├── ModuleStateRecord.java
│   ├── Permission.java
│   ├── ProcessDocument.java
│   ├── ProcessDocumentFile.java
│   ├── Project.java
│   ├── ProjectAcceptance.java
│   ├── ProjectApplication.java
│   ├── ProjectContract.java
│   ├── ProjectMember.java
│   ├── ProjectModuleInstance.java
│   ├── Role.java
│   ├── RolePermission.java
│   ├── StateRecordContextView.java
│   ├── StateRecordMaterial.java
│   ├── StateRecordRemark.java
│   ├── TaskInstance.java
│   ├── UserRole.java
│   ├── UserRoleDetailView.java
│   ├── WorkflowDefinition.java
│   ├── WorkflowNode.java
│   ├── WorkflowNodeConfigView.java
│   ├── WorkflowNodeDocumentConfig.java
│   └── WorkflowNodeMaterialRequirement.java
│
└── service
```

当前结构已经完成了大量数据库表和视图对应的 `Dao`、`Entity` 定义，说明数据库基础层已经初步搭建完成。

---

## 2. 当前结构的特点

当前项目结构属于典型的“按技术层横向分包”方式：

```text
controller
dao
entity
service
```

也就是说：

```text
所有 Controller 放在 controller 包；
所有 Dao 放在 dao 包；
所有 Entity 放在 entity 包；
所有 Service 放在 service 包。
```

这种结构在项目早期有一定优点：

```text
1. 简单直接；
2. 生成代码方便；
3. 表结构和 Entity / Dao 对应关系清晰；
4. 适合快速验证数据库 CRUD；
5. 对于小型项目，上手成本低。
```

当前阶段如果主要目标是：

```text
建表；
生成实体；
生成 Mapper / Dao；
验证基础 CRUD；
验证视图查询；
```

那么当前结构是可以接受的。

---

## 3. 当前结构存在的问题

随着系统继续实现 BPMN 状态机、权限、材料、待办、单据、ViewModel 和 SSE，当前结构会逐渐暴露一些问题。

### 3.1 Dao 和 Entity 过于集中

当前所有 Dao 都放在同一个 `dao` 包中，例如：

```text
AppUserDao
WorkflowDefinitionDao
ProjectApplicationDao
ModuleStateRecordDao
TaskInstanceDao
ProcessDocumentDao
MaterialVersionDao
```

这些类分别属于不同业务域：

```text
权限管理；
BPMN 流程定义；
项目业务数据；
状态机运行时；
材料管理；
待办任务；
正式单据。
```

但目前它们全部混在一个包中，后续类数量继续增加后，维护成本会变高。

### 3.2 Entity 也缺少领域边界

当前所有 Entity 都放在同一个 `entity` 包中。

例如：

```text
AppUser.java
WorkflowNode.java
ProjectContract.java
ModuleStateRecord.java
TaskInstance.java
MaterialVersion.java
ProcessDocument.java
```

从领域角度看，这些实体属于不同模块，但当前目录无法体现这种差异。

后续开发者需要通过类名判断实体归属，包结构本身无法表达业务边界。

### 3.3 Service 层尚未体现系统核心职责

当前只有一个空的或待实现的 `service` 包。

而该系统未来至少需要以下核心服务：

```text
用户权限服务；
BPMN 发布服务；
BPMN 解析服务；
项目业务服务；
状态机运行时服务；
材料服务；
待办服务；
单据服务；
ViewModel 组装服务；
SSE 通知服务；
AOP 日志与幂等服务。
```

如果这些 Service 也全部放在一个 `service` 包中，会很快变得混乱。

### 3.4 不利于状态机核心逻辑隔离

根据设计文档，状态机运行时是系统核心，至少包含：

```text
StateMachineRuntime；
TransitionMatcher；
ConditionEvaluator；
Validator；
ActionHandler；
ModuleStateRecord；
StateRecordRemark；
StateRecordMaterial；
TaskInstance；
ProcessDocument；
```

如果仍然采用当前横向结构，状态机逻辑会分散在：

```text
service
dao
entity
controller
```

这会导致状态机主流程不够集中，不利于测试和维护。

### 3.5 不利于 AOP 横切能力落地

当前项目还没有独立的：

```text
common
config
security
aop
```

包结构。

如果要实现面向切面的功能，例如：

```text
权限切面；
操作日志切面；
幂等切面；
数据权限切面；
耗时统计切面；
```

最好将这些横切能力统一放在 `common.aop` 或类似包中，而不是混入业务 Service。

---

## 4. 推荐的第一版相对简洁结构

第一版不建议过度复杂化，也不建议拆成多模块 Maven 项目。

建议仍然保持单体 Spring Boot 项目，但从“按技术层分包”调整为“按领域能力分包”。

推荐结构如下：

```text
com.amatrix.sicprojectis_backend
├── SicProjectIsBackendApplication.java
│
├── common
│   ├── response
│   ├── exception
│   ├── enums
│   ├── util
│   └── aop
│       ├── annotation
│       ├── aspect
│       └── support
│
├── config
│
├── security
│
├── system
│   ├── controller
│   ├── service
│   ├── dao
│   ├── entity
│   └── dto
│
├── workflow
│   ├── controller
│   ├── service
│   ├── parser
│   ├── dao
│   ├── entity
│   └── dto
│
├── project
│   ├── controller
│   ├── service
│   ├── dao
│   ├── entity
│   └── dto
│
├── runtime
│   ├── controller
│   ├── service
│   ├── condition
│   ├── validator
│   ├── action
│   ├── dao
│   ├── entity
│   └── dto
│
├── task
│   ├── controller
│   ├── service
│   ├── dao
│   └── entity
│
├── material
│   ├── controller
│   ├── service
│   ├── dao
│   └── entity
│
├── document
│   ├── controller
│   ├── service
│   ├── dao
│   └── entity
│
├── viewmodel
│   ├── controller
│   ├── service
│   └── dto
│
└── notification
    ├── controller
    ├── service
    └── listener
```

该结构的目标是：

```text
保持第一版简洁；
不拆多模块；
但通过包结构体现业务边界；
让状态机、BPMN、权限、业务数据、材料、待办、单据、ViewModel、SSE 各自独立。
```

---

## 5. 推荐结构与当前结构的核心差异

| 维度 | 当前结构 | 推荐结构 |
|---|---|---|
| 分包方式 | 按技术层分包 | 按领域能力分包 |
| Dao 位置 | 全部集中在 `dao` | 分散到各领域包下 |
| Entity 位置 | 全部集中在 `entity` | 分散到各领域包下 |
| Service 位置 | 全部集中在 `service` | 各领域独立 service |
| 状态机 | 未单独体现 | 独立 `runtime` 包 |
| BPMN 发布 | 未单独体现 | 独立 `workflow` 包 |
| 权限认证 | 未单独体现 | `security` + `system` |
| AOP 横切 | 未单独体现 | `common.aop` |
| ViewModel | 未单独体现 | 独立 `viewmodel` 包 |
| SSE 通知 | 未单独体现 | 独立 `notification` 包 |
| 可维护性 | 初期简单，后期混乱 | 初期稍复杂，后期清晰 |

---

## 6. 当前类的推荐迁移归属

### 6.1 system：用户、角色、权限、部门

以下类建议迁移到 `system` 包：

```text
AppUser
Department
Role
Permission
UserRole
RolePermission
UserRoleDetailView
```

对应 Dao：

```text
AppUserDao
DepartmentDao
RoleDao
PermissionDao
UserRoleDao
RolePermissionDao
UserRoleDetailViewDao
```

推荐结构：

```text
system
├── dao
│   ├── AppUserDao.java
│   ├── DepartmentDao.java
│   ├── RoleDao.java
│   ├── PermissionDao.java
│   ├── UserRoleDao.java
│   ├── RolePermissionDao.java
│   └── UserRoleDetailViewDao.java
└── entity
    ├── AppUser.java
    ├── Department.java
    ├── Role.java
    ├── Permission.java
    ├── UserRole.java
    ├── RolePermission.java
    └── UserRoleDetailView.java
```

---

### 6.2 workflow：BPMN 流程定义与节点配置

以下类建议迁移到 `workflow` 包：

```text
WorkflowDefinition
WorkflowNode
WorkflowNodeConfigView
WorkflowNodeDocumentConfig
WorkflowNodeMaterialRequirement
```

对应 Dao：

```text
WorkflowDefinitionDao
WorkflowNodeDao
WorkflowNodeConfigViewDao
WorkflowNodeDocumentConfigDao
WorkflowNodeMaterialRequirementDao
```

推荐结构：

```text
workflow
├── dao
│   ├── WorkflowDefinitionDao.java
│   ├── WorkflowNodeDao.java
│   ├── WorkflowNodeConfigViewDao.java
│   ├── WorkflowNodeDocumentConfigDao.java
│   └── WorkflowNodeMaterialRequirementDao.java
└── entity
    ├── WorkflowDefinition.java
    ├── WorkflowNode.java
    ├── WorkflowNodeConfigView.java
    ├── WorkflowNodeDocumentConfig.java
    └── WorkflowNodeMaterialRequirement.java
```

后续 BPMN 解析相关类建议放入：

```text
workflow.parser
```

例如：

```text
BpmnParser
BpmnNodeParser
BpmnTransitionParser
MaterialRequirementParser
DocumentConfigParser
```

---

### 6.3 project：项目与业务数据

以下类建议迁移到 `project` 包：

```text
Project
ProjectMember
ProjectApplication
ProjectContract
ProjectAcceptance
```

对应 Dao：

```text
ProjectDao
ProjectMemberDao
ProjectApplicationDao
ProjectContractDao
ProjectAcceptanceDao
```

推荐结构：

```text
project
├── dao
│   ├── ProjectDao.java
│   ├── ProjectMemberDao.java
│   ├── ProjectApplicationDao.java
│   ├── ProjectContractDao.java
│   └── ProjectAcceptanceDao.java
└── entity
    ├── Project.java
    ├── ProjectMember.java
    ├── ProjectApplication.java
    ├── ProjectContract.java
    └── ProjectAcceptance.java
```

该包只负责业务事实数据，不直接承担流程状态判断。

---

### 6.4 runtime：状态机运行时

以下类建议迁移到 `runtime` 包：

```text
ProjectModuleInstance
ModuleStateRecord
StateRecordRemark
StateRecordMaterial
ModuleRuntimeContextView
StateRecordContextView
```

对应 Dao：

```text
ProjectModuleInstanceDao
ModuleStateRecordDao
StateRecordRemarkDao
StateRecordMaterialDao
ModuleRuntimeContextViewDao
StateRecordContextViewDao
```

推荐结构：

```text
runtime
├── dao
│   ├── ProjectModuleInstanceDao.java
│   ├── ModuleStateRecordDao.java
│   ├── StateRecordRemarkDao.java
│   ├── StateRecordMaterialDao.java
│   ├── ModuleRuntimeContextViewDao.java
│   └── StateRecordContextViewDao.java
└── entity
    ├── ProjectModuleInstance.java
    ├── ModuleStateRecord.java
    ├── StateRecordRemark.java
    ├── StateRecordMaterial.java
    ├── ModuleRuntimeContextView.java
    └── StateRecordContextView.java
```

后续状态机核心类也放在该包下：

```text
runtime.service.StateMachineRuntime
runtime.service.TransitionMatcher
runtime.condition.ConditionEvaluator
runtime.validator.NodeMaterialValidator
runtime.action.TransitionActionHandler
```

---

### 6.5 material：材料与材料版本

以下类建议迁移到 `material` 包：

```text
MaterialType
Material
MaterialVersion
MaterialContextView
```

对应 Dao：

```text
MaterialTypeDao
MaterialDao
MaterialVersionDao
MaterialContextViewDao
```

推荐结构：

```text
material
├── dao
│   ├── MaterialTypeDao.java
│   ├── MaterialDao.java
│   ├── MaterialVersionDao.java
│   └── MaterialContextViewDao.java
└── entity
    ├── MaterialType.java
    ├── Material.java
    ├── MaterialVersion.java
    └── MaterialContextView.java
```

---

### 6.6 task：待办任务

以下类建议迁移到 `task` 包：

```text
TaskInstance
```

对应 Dao：

```text
TaskInstanceDao
```

推荐结构：

```text
task
├── dao
│   └── TaskInstanceDao.java
└── entity
    └── TaskInstance.java
```

---

### 6.7 document：流程单据

以下类建议迁移到 `document` 包：

```text
ProcessDocument
ProcessDocumentFile
```

对应 Dao：

```text
ProcessDocumentDao
ProcessDocumentFileDao
```

推荐结构：

```text
document
├── dao
│   ├── ProcessDocumentDao.java
│   └── ProcessDocumentFileDao.java
└── entity
    ├── ProcessDocument.java
    └── ProcessDocumentFile.java
```

---

## 7. AOP 相关结构设计

考虑后续要使用面向切面实现通用能力，建议新增：

```text
common.aop
```

推荐结构：

```text
common
└── aop
    ├── annotation
    │   ├── RequirePermission.java
    │   ├── RequireRole.java
    │   ├── OperationLog.java
    │   ├── Idempotent.java
    │   ├── DataScope.java
    │   └── CostTime.java
    ├── aspect
    │   ├── PermissionAspect.java
    │   ├── RoleAspect.java
    │   ├── OperationLogAspect.java
    │   ├── IdempotentAspect.java
    │   ├── DataScopeAspect.java
    │   └── CostTimeAspect.java
    └── support
        ├── SpelExpressionResolver.java
        ├── RequestFingerprintGenerator.java
        └── AspectOrderConstants.java
```

第一版建议优先实现以下切面：

```text
@RequirePermission    接口权限校验
@RequireRole          角色校验
@OperationLog         操作日志
@Idempotent           防重复提交
@CostTime             方法耗时统计
```

可以暂缓实现：

```text
@DataScope            复杂数据范围过滤
```

因为数据范围和项目访问控制更适合先在 Service 中显式实现，等规则稳定后再抽象为切面。

---

## 8. 设计边界说明

### 8.1 AOP 负责什么

AOP 适合处理横切关注点，例如：

```text
接口权限校验；
角色校验；
操作日志；
防重复提交；
方法耗时统计；
部分数据范围控制。
```

### 8.2 AOP 不负责什么

以下内容不建议放入 AOP：

```text
状态机核心迁移逻辑；
BPMN transition 匹配；
Gateway 条件判断；
材料校验主流程；
待办关闭与创建；
单据生成；
业务表保存逻辑。
```

这些逻辑应该显式放在：

```text
StateMachineRuntime；
ConditionEvaluator；
NodeMaterialValidator；
TransitionActionHandler；
BusinessService；
TaskService；
ProcessDocumentService。
```

否则流程执行逻辑会隐藏在切面中，调试和维护都会变困难。

---

## 9. 推荐的第一版迁移策略

不建议一次性大规模重构全部文件，可以分阶段迁移。

### 9.1 第一阶段：保留现有结构，补齐基础能力

当前阶段可以继续保留：

```text
dao
entity
service
controller
```

先完成：

```text
基础 CRUD；
Mapper 测试；
视图查询测试；
数据库结构稳定。
```

### 9.2 第二阶段：新增 common、config、security

新增：

```text
common
config
security
```

先把通用返回、异常处理、登录鉴权、当前用户上下文、AOP 注解和切面放进去。

### 9.3 第三阶段：迁移 system 和 workflow

优先迁移：

```text
system
workflow
```

原因：

```text
system 是权限基础；
workflow 是 BPMN 发布基础；
这两个模块对后续状态机影响最大。
```

### 9.4 第四阶段：迁移 runtime

将状态机运行时相关类迁移到：

```text
runtime
```

并实现：

```text
StateMachineRuntime；
TransitionMatcher；
ConditionEvaluator；
NodeMaterialValidator；
TransitionActionHandler；
RuntimeQueryService。
```

### 9.5 第五阶段：迁移 project、material、task、document

最后再迁移：

```text
project
material
task
document
```

这样可以避免一次性移动太多类导致 import、Mapper 扫描、测试全部大面积失效。

---

## 10. 推荐的最终第一版结构

最终第一版建议形成如下结构：

```text
com.amatrix.sicprojectis_backend
├── SicProjectIsBackendApplication.java
│
├── common
│   ├── response
│   ├── exception
│   ├── enums
│   ├── util
│   └── aop
│       ├── annotation
│       ├── aspect
│       └── support
│
├── config
│
├── security
│
├── system
│   ├── controller
│   ├── service
│   ├── dao
│   ├── entity
│   └── dto
│
├── workflow
│   ├── controller
│   ├── service
│   ├── parser
│   ├── dao
│   ├── entity
│   └── dto
│
├── project
│   ├── controller
│   ├── service
│   ├── dao
│   ├── entity
│   └── dto
│
├── runtime
│   ├── controller
│   ├── service
│   ├── condition
│   ├── validator
│   ├── action
│   ├── dao
│   ├── entity
│   └── dto
│
├── material
│   ├── controller
│   ├── service
│   ├── dao
│   └── entity
│
├── task
│   ├── controller
│   ├── service
│   ├── dao
│   └── entity
│
├── document
│   ├── controller
│   ├── service
│   ├── dao
│   └── entity
│
├── viewmodel
│   ├── controller
│   ├── service
│   └── dto
│
└── notification
    ├── controller
    ├── service
    └── listener
```

---

## 11. 资源目录建议

如果使用 MyBatis XML，建议将 `resources/mapper` 也按领域拆分：

```text
src/main/resources
├── mapper
│   ├── system
│   ├── workflow
│   ├── project
│   ├── runtime
│   ├── material
│   ├── task
│   └── document
│
├── db
│   ├── schema.sql
│   ├── data.sql
│   └── migration
│
└── bpmn
    ├── application.bpmn
    ├── contract.bpmn
    └── acceptance.bpmn
```

例如：

```text
mapper/system/AppUserMapper.xml
mapper/workflow/WorkflowDefinitionMapper.xml
mapper/runtime/ModuleStateRecordMapper.xml
mapper/project/ProjectApplicationMapper.xml
```

这样 Java 包和 Mapper XML 路径可以保持一致。

---

## 12. 当前结构与推荐结构的映射表

| 当前类 / 包 | 推荐迁移位置 |
|---|---|
| `controller` | 按接口归属迁移到各领域 `controller` |
| `service` | 按业务能力迁移到各领域 `service` |
| `dao/AppUserDao` | `system/dao/AppUserDao` |
| `dao/DepartmentDao` | `system/dao/DepartmentDao` |
| `dao/RoleDao` | `system/dao/RoleDao` |
| `dao/PermissionDao` | `system/dao/PermissionDao` |
| `dao/UserRoleDao` | `system/dao/UserRoleDao` |
| `dao/RolePermissionDao` | `system/dao/RolePermissionDao` |
| `dao/UserRoleDetailViewDao` | `system/dao/UserRoleDetailViewDao` |
| `dao/WorkflowDefinitionDao` | `workflow/dao/WorkflowDefinitionDao` |
| `dao/WorkflowNodeDao` | `workflow/dao/WorkflowNodeDao` |
| `dao/WorkflowNodeConfigViewDao` | `workflow/dao/WorkflowNodeConfigViewDao` |
| `dao/WorkflowNodeDocumentConfigDao` | `workflow/dao/WorkflowNodeDocumentConfigDao` |
| `dao/WorkflowNodeMaterialRequirementDao` | `workflow/dao/WorkflowNodeMaterialRequirementDao` |
| `dao/ProjectDao` | `project/dao/ProjectDao` |
| `dao/ProjectMemberDao` | `project/dao/ProjectMemberDao` |
| `dao/ProjectApplicationDao` | `project/dao/ProjectApplicationDao` |
| `dao/ProjectContractDao` | `project/dao/ProjectContractDao` |
| `dao/ProjectAcceptanceDao` | `project/dao/ProjectAcceptanceDao` |
| `dao/ProjectModuleInstanceDao` | `runtime/dao/ProjectModuleInstanceDao` |
| `dao/ModuleStateRecordDao` | `runtime/dao/ModuleStateRecordDao` |
| `dao/StateRecordRemarkDao` | `runtime/dao/StateRecordRemarkDao` |
| `dao/StateRecordMaterialDao` | `runtime/dao/StateRecordMaterialDao` |
| `dao/ModuleRuntimeContextViewDao` | `runtime/dao/ModuleRuntimeContextViewDao` |
| `dao/StateRecordContextViewDao` | `runtime/dao/StateRecordContextViewDao` |
| `dao/MaterialTypeDao` | `material/dao/MaterialTypeDao` |
| `dao/MaterialDao` | `material/dao/MaterialDao` |
| `dao/MaterialVersionDao` | `material/dao/MaterialVersionDao` |
| `dao/MaterialContextViewDao` | `material/dao/MaterialContextViewDao` |
| `dao/TaskInstanceDao` | `task/dao/TaskInstanceDao` |
| `dao/ProcessDocumentDao` | `document/dao/ProcessDocumentDao` |
| `dao/ProcessDocumentFileDao` | `document/dao/ProcessDocumentFileDao` |

Entity 类也按照同样规则迁移到对应领域包的 `entity` 目录。

---

## 13. 最终建议

当前结构适合项目早期生成代码和验证数据库 CRUD，但随着系统进入权限、BPMN 发布、状态机、ViewModel、SSE 阶段，建议逐步迁移为领域分包结构。

推荐原则：

```text
不要一次性大重构；
先补 common / config / security；
再迁移 system / workflow；
再迁移 runtime；
最后迁移 project / material / task / document；
AOP 放入 common.aop；
状态机核心逻辑仍显式放在 runtime，不放入切面。
```

一句话概括：

> 当前结构是“表驱动的技术分层结构”，适合基础 CRUD；推荐结构是“领域驱动的单体结构”，更适合后续实现 BPMN 状态机、权限、材料、待办、单据、ViewModel 和 SSE。
