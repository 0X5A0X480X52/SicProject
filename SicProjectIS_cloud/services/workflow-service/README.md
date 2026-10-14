# workflow-service

流程与任务服务，负责 BPMN 定义发布、模块实例与节点流转、节点表单、申请/合同/验收业务数据、专家评审、公告及工作台查询。默认容器内端口为 **9003**，拥有 MySQL Schema **sic_workflow**。

## 包结构与职责

Java 根包为 `com.amatrix.sic.workflow`，源码位于 [src/main/java/com/amatrix/sic/workflow](src/main/java/com/amatrix/sic/workflow)。

| 包 | 主要类/内容 | 职责 |
| --- | --- | --- |
| 根包 | `WorkflowServiceApplication` | 组件、Feign 客户端和配置属性扫描。 |
| `bootstrap` | `WorkflowAssetInitializer` | 启动检查发行 BPMN 所需的已安装定义，按显式开关补齐缺失资产。 |
| `config`、`config.properties` | `WorkflowSnapshotMessagingConfiguration`、`WorkflowClientProperties` | 消息队列绑定及内部令牌配置。 |
| `controller` | 定义、运行时、工作台、表单、业务数据、评审和公告 Controller | 各业务域的外部 HTTP 入口。 |
| `controller.internal` | `InternalApplicationSagaController` | 项目申请 Saga 的创建、查询及补偿入口。 |
| `service.definition` | `WorkflowDefinitionService`、`WorkflowDefinitionModelStore` | 定义上传、校验、发布和节点/迁移模型持久化。 |
| `service.runtime` | `WorkflowRuntimeService`、`WorkflowTransitionActionService`、`WorkflowWorkbenchQueryService` | 实例/任务流转、节点动作、后继模块恢复和工作台过滤查询。 |
| `service.form` | `NodeFormService` | 节点表单目录、节点/轮次检查、保存及聚合读取。 |
| `service.business` | `StructuredBusinessService`、`NoticeService` | 申请/合同/验收等结构化正文及公告管理。 |
| `service.review` | `ExpertReviewService`、`ExpertPersonalReviewService` | 专家批次、指派、评分及本人评审历史。 |
| `service.saga` | `ApplicationSagaWorkflowService` | 幂等创建申请实例及补偿对应 Saga 的本地数据。 |
| `workflow.parser`、`workflow.model` | `FlowableBpmnDefinitionParser`、`WorkflowBpmnParseResult` | 用 Flowable BPMN 转换器解析 XML，生成业务运行模型。 |
| `workflow.definition` | `WorkflowAssetCatalog`、`WorkflowDefinitionCatalog`、`WorkflowDefinitionNavigator` | 发行资产读取、已发布模型读取和迁移路径导航。 |
| `policy` | `WorkflowOperationPolicy`、`WorkflowProjectAccessPolicy`、`ModuleSuccessorPolicy` | 当前任务操作权、项目读取范围和后继模块规则。 |
| `client`、`client.auth` | Project/IAM/Material Feign 客户端、`WorkflowInternalTokenProvider` | 项目授权、身份/专家摘要、材料预留及内部令牌。 |
| `messaging.consumer` | 身份、项目生命周期、项目授权及评审完成消费者 | 更新快照、负责人任务及推进专家评审完成后的流转。 |
| `dao`、`dao.review` | 各业务 Mapper、`ExpertReviewViewRepository` | 数据访问及专家评审视图查询。 |
| `entity`、`dto` | 模块、任务、定义、轮次、专家批次等模型 | 持久化行和业务数据结构。 |
| `tools.seed` | `WorkflowSeedExporter` | 离线生成发行 SQL 种子，复用运行时 BPMN 解析器。 |

SQL 位于 [mapper/workflow](src/main/resources/mapper/workflow)，内置 BPMN 位于 [workflows](src/main/resources/workflows)。当前业务状态由本服务的运行时模型和表维护，Flowable 转换器用于 BPMN 解析。

## 接口与主要流程

| 接口族 | 用途 |
| --- | --- |
| `/api/workflow-definitions/**` | 上传、校验、发布、停用及读取 BPMN/节点/迁移配置。 |
| `/api/module-instances/**`、`/api/legacy/**` | 模块实例、运行视图及兼容流转接口。 |
| `/api/node-forms/**` | 表单目录、数据读写及历史记录。 |
| `/api/projects/{projectId}/applications`、`contracts`、`acceptances` 等 | 项目阶段结构化数据、成果和财务结算。 |
| `/api/expert-reviews/**` | 评审批次、专家指派、评分及本人历史。 |
| `/api/workflow-workbench/**`、`/api/notices/**` | 工作台及公告。 |
| `/internal/application-sagas/**` | Project 调用的申请创建、状态查询和补偿。 |

定义流程为 `Controller → WorkflowDefinitionService → FlowableBpmnDefinitionParser → 模型校验 → WorkflowDefinitionModelStore/Mapper → 发布`。运行时使用对应已发布定义与导航器决定路径。

节点操作先检查当前任务、角色、指派、序号和项目授权；必要时向 Material 预留材料，再在本地事务中更新状态、任务、表单/引用及 Outbox，提交后由事件确认材料引用。节点动作可请求生成单据或发布项目生命周期事件。

专家评审创建/调整指派时通过 Project 同步范围授权。评分聚合完成后发布评审完成事件，由消费者推进节点；历史评审读取和当前节点操作使用各自的权限边界。

输入事件包括 `iam.changed`、`workflow.project-lifecycle-changed`、`project.authorization`、`workflow.expert-review-completed`。消息处理使用公共 Inbox 去重；跨库授权、材料预留和状态同步通过调用及事件协调。

## 配置与启动装配

- [本地 application.yaml](src/main/resources/application.yaml)：服务名及 `CONFIG_IMPORT`。
- [workflow-service.yaml](../../platform/config-server/src/main/resources/config/workflow-service.yaml)：9003 端口、`WORKFLOW_DB_URL/USERNAME/PASSWORD`、内部令牌引导密钥。
- [共享 application.yaml](../../platform/config-server/src/main/resources/config/application.yaml)：Eureka、JWT、RabbitMQ、MyBatis、连接池及 Feign 超时/断路器。
- `sic.workflow.assets.bootstrap-if-missing`：默认 `false`，启动依赖已安装的发行 SQL 定义，不用内置 BPMN 覆盖已有配置/发布版本。
- `sic.workflow.*recovery*`：运行时后继模块、负责人指派和专家访问恢复任务参数，以 `WorkflowRuntimeService` 中的 `@Scheduled` 为准。

启动入口扫描子包、配置属性与 Feign 客户端；公共 `cloud-security` 接入 JWT/方法安全，`cloud-messaging` 接入 Outbox/Inbox 和调度。`WorkflowAssetInitializer` 是启动 runner，会检查所需定义。运行依赖为 Config Server、Eureka、MySQL、RabbitMQ，以及 IAM、Project、Material。

## 构建、启动与检查

以下命令在 `SicProjectIS_cloud` 根目录执行：

```powershell
mvn -B -pl services/workflow-service -am test
mvn -B -pl services/workflow-service -am -DskipTests package
```

可执行产物为 `services/workflow-service/target/workflow-service-1.0.0-SNAPSHOT-exec.jar`。首次初始化及镜像/冻结 JAR 挂载方式见 [工程 README](../../README.md)。

已有冻结 JAR 环境示例（复用实际部署配置）：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600 workflow-service
```

容器检查 `/actuator/health/readiness`。缺少发行流程种子时应补齐对应 SQL 初始化/升级，避免通过重启覆盖自定义定义；`db/migration` 资源不表示启用了 Flyway。冻结 JAR 更新需切换发布包及挂载源。

[测试目录](src/test/java/com/amatrix/sic/workflow) 覆盖定义模型与权限、资产重启、表单与业务数据、操作策略、迁移动作、序号并发和 Saga 补偿；契约位于 [src/test/resources/contracts](src/test/resources/contracts)。真实节点链路、专家评审、材料确认及多实例恢复按 [启动指南](../../docs/backend-startup-guide.md) 和 [多实例验收](../../docs/multi-instance-startup.md) 验证。
