# project-service

项目主数据与项目授权服务，负责项目查询、负责人/成员/专家/财务等授权、部门归属、审计及项目申请 Saga。默认容器内端口为 **9002**，拥有 MySQL Schema **sic_project**。

## 包结构与职责

Java 根包为 `com.amatrix.sic.project`，源码位于 [src/main/java/com/amatrix/sic/project](src/main/java/com/amatrix/sic/project)。

| 包 | 主要类 | 职责 |
| --- | --- | --- |
| 根包 | `ProjectServiceApplication` | 启动服务，扫描组件、Feign 客户端和配置属性。 |
| `config`、`config.properties` | `ProjectSnapshotMessagingConfiguration`、`ServiceClientProperties` | 快照/生命周期/专家访问结束队列，内部令牌配置。 |
| `controller` | `ProjectController`、`ProjectAuthorizationController`、`ProjectDepartmentController` | 项目查询、申请启动、项目授权及部门分配接口。 |
| `controller` | `ProjectAdminController`、`ProjectAuditController` | 管理端授权查询、概览和审计。 |
| `controller.internal` | `InternalProjectController` | 项目访问判断、财务就绪及专家授权同步/撤销。 |
| `service` | `ProjectQueryService`、`ProjectAuthorizationService`、`ProjectDepartmentService` | 项目可见性查询、授权变更及历史空部门归属补齐。 |
| `service` | `ProjectAuditService`、`ProjectFinanceReadinessService` | 审计/概览查询及精确业务范围内的财务授权检查。 |
| `service.saga` | `ProjectApplicationSagaService` | 项目申请跨服务编排、幂等、补偿及待确认恢复。 |
| `service.directory` | `ProjectUserDirectory` | 本地身份快照读取、缺失时访问 IAM 和显式刷新。 |
| `policy` | `ProjectAccessPolicy` | 项目、模块、轮次、节点范围的访问与操作判断。 |
| `client`、`client.auth` | `IamDirectoryClient`、`WorkflowSagaClient`、`ServiceTokenClient`、`InternalTokenProvider` | IAM/Workflow 的 Feign 调用和内部 Bearer 令牌缓存。 |
| `messaging.consumer` | 三个 `Project*Consumer` | 更新身份快照、项目业务状态和专家访问授权。 |
| `dao`、`entity`、`dto` | 各业务 Mapper、`ProjectRecord`、`ApplicationSagaRow`、`ProjectDtos` 等 | 数据访问、持久化行模型及业务数据结构。 |
| `metrics` | `ProjectSagaMetrics` | 未完成/失败/补偿 Saga 数量及待确认年龄指标。 |

SQL 位于 [mapper/project](src/main/resources/mapper/project)。实体和 Mapper 以本服务数据库为边界；跨服务身份信息通过客户端与快照获取。

## 接口与主要流程

| 接口族 | 用途 |
| --- | --- |
| `GET /api/projects` | 按身份及授权过滤可见项目。 |
| `POST /api/project-applications/start` | 启动项目申请 Saga。 |
| `/api/projects/{projectId}/authorization` 及成员、负责人、grant 子路径 | 查询、授予、调整和撤销项目授权。 |
| `PUT /api/projects/{projectId}/department` | 为无归属部门的项目补齐部门。 |
| `/api/admin/projects/**`、`/api/admin/overview`、`/api/admin/audit-logs` | 管理查询与审计。 |
| `/internal/projects/**` | 供业务服务判断访问范围、财务就绪及同步专家 grant。 |

项目申请流程为 `ProjectController → ProjectApplicationSagaService → 本地项目/Saga → WorkflowSagaClient → Workflow 申请实例 → 确认本地项目`。本地提交与 Workflow 提交分开，依靠幂等标识、查询确认和补偿协调；定时恢复主要处理 `WORKFLOW_CREATED` 待确认状态，不是跨库原子事务。

授权变更在本地事务中更新关系、项目版本、审计和 Outbox，再发布 `project.authorization`。业务操作资格需匹配具体范围；拥有全局专家或财务角色不自动获得任意项目的业务操作权。

消费者分别处理 `iam.changed`、`workflow.project-lifecycle-changed`、`workflow.expert-access-ended`。`ProjectUserDirectory` 优先读本地快照，必要时访问 IAM，快照与页面缓存不能当成实时身份数据库。

## 配置与启动装配

- [本地 application.yaml](src/main/resources/application.yaml)：声明服务名，通过 `CONFIG_IMPORT` 导入配置中心。
- [project-service.yaml](../../platform/config-server/src/main/resources/config/project-service.yaml)：9002 端口，`PROJECT_DB_URL/USERNAME/PASSWORD`，内部令牌引导密钥和 `SAGA_RECOVERY_DELAY_MS`（默认 30000 ms）。
- [共享 application.yaml](../../platform/config-server/src/main/resources/config/application.yaml)：Eureka、RabbitMQ、JWT 公钥地址、MyBatis、连接池及 Feign 超时/断路器。

启动类通过 `@SpringBootApplication` 扫描组件，`@EnableFeignClients` 注册客户端，`@ConfigurationPropertiesScan` 绑定属性。`cloud-security` 提供 Servlet JWT、方法安全和身份上下文；`cloud-messaging` 提供 Outbox/Inbox、事件发布及定时任务支持。`config` 声明本服务的 RabbitMQ 队列与绑定。

主要依赖为 Config Server、Eureka、MySQL、RabbitMQ、IAM，以及申请编排使用的 Workflow。Project 的内部接口要求内部 scope；外部请求通过网关认证后，仍由本服务进行方法及项目范围授权。

## 构建、启动与检查

以下命令在 `SicProjectIS_cloud` 根目录执行：

```powershell
mvn -B -pl services/project-service -am test
mvn -B -pl services/project-service -am -DskipTests package
```

可执行产物为 `services/project-service/target/project-service-1.0.0-SNAPSHOT-exec.jar`。首次初始化、Docker 镜像及冻结 JAR 挂载步骤见 [工程 README](../../README.md)。

已有冻结 JAR 环境示例（使用实际环境对应的配置）：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600 project-service
```

容器检查 `/actuator/health/readiness`，外部从网关访问上述 `/api` 路径。数据库完整初始化与升级使用发行 SQL/版本补丁；资源中的 `db/migration` 不会自动触发 Flyway。更新冻结 JAR 需切换发布包和挂载源。

[测试目录](src/test/java/com/amatrix/sic/project) 覆盖访问策略、方法安全代理、查询、Saga 和专家授权结束；接口契约位于 [src/test/resources/contracts](src/test/resources/contracts)。真实跨服务申请、恢复和多副本并发需结合 [启动指南](../../docs/backend-startup-guide.md) 与 [多实例验收](../../docs/multi-instance-startup.md)。
