# 数据库迁移范围清单

调研日期：2026-10-10。由当前源码静态枚举生成，配套说明见 [SQL 初始化与 MyBatis 迁移调研](database-mybatis-migration-research.md)。

这里统计的是建表 SQL 定义的目标结构，不是已连接数据库后的实测结构。业务表按 Schema 计数；两个共享消息表在五个 Schema 中各建一份。Flyway 历史表和旧库迁移暂存表不计入。

## 1. 五个服务 Schema

| 服务 | Schema | 业务表 | 含消息表合计 | 直接引用 JdbcTemplate 的主源码文件 |
| --- | --- | ---: | ---: | ---: |
| iam-service | `sic_iam` | 7 | 9 | 4 |
| project-service | `sic_project` | 7 | 9 | 10 |
| workflow-service | `sic_workflow` | 20 | 22 | 15 |
| material-service | `sic_material` | 7 | 9 | 5 |
| notification-service | `sic_notification` | 1 | 3 | 3 |
| **合计** | **5 个 Schema** | **42** | **52** | **37** |

共 15 个不同的版本 SQL 文件：业务迁移 13 个，共享消息迁移 2 个。每个 Schema 应用共享消息迁移和自身业务迁移，按数值版本排序。

## 2. 所有建表来源与表名

| 所属 | 来源 | 创建的表 |
| --- | --- | --- |
| 每个服务的消息表 | [V1__outbox_and_inbox.sql](../platform/cloud-messaging/src/main/resources/db/common/V1__outbox_and_inbox.sql) | `outbox_event`, `consumed_event` |
| 每个服务的消息表 | [V100__outbox_claim_and_retry.sql](../platform/cloud-messaging/src/main/resources/db/common/V100__outbox_claim_and_retry.sql) | 无新表；ALTER / 索引补充 |
| iam | [V2__iam_domain.sql](../services/iam-service/src/main/resources/db/migration/V2__iam_domain.sql) | `department`, `app_user`, `app_role`, `app_permission`, `user_role`, `role_permission`, `expert_qualification` |
| project | [V2__project_domain.sql](../services/project-service/src/main/resources/db/migration/V2__project_domain.sql) | `project`, `project_member`, `project_access_grant`, `project_access_log`, `admin_operation_log`, `application_saga`, `user_snapshot` |
| workflow | [V2__workflow_domain.sql](../services/workflow-service/src/main/resources/db/migration/V2__workflow_domain.sql) | `workflow_definition`, `module_instance`, `state_record`, `workflow_task`, `node_form`, `project_application`, `contract_form`, `acceptance_form`, `expert_review`, `workflow_material_reference`, `user_snapshot`, `project_snapshot` |
| workflow | [V3__expert_review.sql](../services/workflow-service/src/main/resources/db/migration/V3__expert_review.sql) | `expert_review_batch`, `expert_review_assignment`, `expert_review_score` |
| workflow | [V4__workflow_definition_model.sql](../services/workflow-service/src/main/resources/db/migration/V4__workflow_definition_model.sql) | `workflow_node_config`, `workflow_transition_config` |
| workflow | [V5__workflow_definition_lifecycle.sql](../services/workflow-service/src/main/resources/db/migration/V5__workflow_definition_lifecycle.sql) | 无新表；ALTER / UPDATE / 索引补充 |
| workflow | [V6__structured_business_data.sql](../services/workflow-service/src/main/resources/db/migration/V6__structured_business_data.sql) | `project_achievement`, `acceptance_financial_settlement`, `notice_record` |
| workflow | [V101__unique_project_module.sql](../services/workflow-service/src/main/resources/db/migration/V101__unique_project_module.sql) | 无新表；ALTER / UPDATE / 索引补充 |
| workflow | [V102__project_authorization_snapshot.sql](../services/workflow-service/src/main/resources/db/migration/V102__project_authorization_snapshot.sql) | 无新表；ALTER / UPDATE / 索引补充 |
| material | [V2__material_domain.sql](../services/material-service/src/main/resources/db/migration/V2__material_domain.sql) | `material_type`, `material`, `material_version`, `generated_document`, `material_reservation`, `material_reservation_item` |
| material | [V3__durable_object_deletion.sql](../services/material-service/src/main/resources/db/migration/V3__durable_object_deletion.sql) | `object_deletion_job` |
| material | [V101__generated_document_idempotency.sql](../services/material-service/src/main/resources/db/migration/V101__generated_document_idempotency.sql) | 无新表；ALTER / UPDATE / 索引补充 |
| notification | [V2__notification_domain.sql](../services/notification-service/src/main/resources/db/migration/V2__notification_domain.sql) | `pending_notification` |

## 3. 直接 JDBC 调用的主源码迁移入口

以下 37 个服务文件直接引用 JdbcTemplate；cloud-messaging 另有 4 个装配/持久化/指标文件。引用不等于每个文件都包含业务 SQL，自动配置属于装配入口。

| 模块 | 文件 |
| --- | --- |
| iam-service | [ExpertQualificationService.java(../services/iam-service/src/main/java/com/amatrix/sic/iam/service/ExpertQualificationService.java) |
| iam-service | [IamAdminService.java(../services/iam-service/src/main/java/com/amatrix/sic/iam/service/IamAdminService.java) |
| iam-service | [IamRepository.java(../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/IamRepository.java) |
| iam-service | [InternalIdentityController.java(../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java) |
| project-service | [ProjectAccessPolicy.java(../services/project-service/src/main/java/com/amatrix/sic/project/policy/ProjectAccessPolicy.java) |
| project-service | [ProjectApplicationSagaService.java(../services/project-service/src/main/java/com/amatrix/sic/project/service/saga/ProjectApplicationSagaService.java) |
| project-service | [ProjectAuditService.java(../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectAuditService.java) |
| project-service | [ProjectAuthorizationService.java(../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectAuthorizationService.java) |
| project-service | [ProjectExpertAccessEndedConsumer.java(../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectExpertAccessEndedConsumer.java) |
| project-service | [ProjectQueryService.java(../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectQueryService.java) |
| project-service | [ProjectSagaMetrics.java(../services/project-service/src/main/java/com/amatrix/sic/project/metrics/ProjectSagaMetrics.java) |
| project-service | [ProjectUserDirectory.java(../services/project-service/src/main/java/com/amatrix/sic/project/service/directory/ProjectUserDirectory.java) |
| project-service | [ProjectUserSnapshotConsumer.java(../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectUserSnapshotConsumer.java) |
| project-service | [ProjectWorkflowLifecycleConsumer.java(../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectWorkflowLifecycleConsumer.java) |
| workflow-service | [ApplicationSagaWorkflowService.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/saga/ApplicationSagaWorkflowService.java) |
| workflow-service | [ExpertReviewService.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/review/ExpertReviewService.java) |
| workflow-service | [ExpertReviewViewRepository.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/review/ExpertReviewViewRepository.java) |
| workflow-service | [NodeFormService.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/form/NodeFormService.java) |
| workflow-service | [NoticeService.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/business/NoticeService.java) |
| workflow-service | [StructuredBusinessService.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/business/StructuredBusinessService.java) |
| workflow-service | [WorkflowAssetInitializer.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/bootstrap/WorkflowAssetInitializer.java) |
| workflow-service | [WorkflowDefinitionCatalog.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowDefinitionCatalog.java) |
| workflow-service | [WorkflowDefinitionService.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/definition/WorkflowDefinitionService.java) |
| workflow-service | [WorkflowOperationPolicy.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/WorkflowOperationPolicy.java) |
| workflow-service | [WorkflowProjectAccessPolicy.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/WorkflowProjectAccessPolicy.java) |
| workflow-service | [WorkflowProjectAuthorizationConsumer.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowProjectAuthorizationConsumer.java) |
| workflow-service | [WorkflowProjectLifecycleConsumer.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowProjectLifecycleConsumer.java) |
| workflow-service | [WorkflowRuntimeService.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowRuntimeService.java) |
| workflow-service | [WorkflowUserSnapshotConsumer.java(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowUserSnapshotConsumer.java) |
| material-service | [GeneratedDocumentService.java(../services/material-service/src/main/java/com/amatrix/sic/material/service/document/GeneratedDocumentService.java) |
| material-service | [MaterialApplicationService.java(../services/material-service/src/main/java/com/amatrix/sic/material/service/MaterialApplicationService.java) |
| material-service | [MaterialCleanupMetrics.java(../services/material-service/src/main/java/com/amatrix/sic/material/metrics/MaterialCleanupMetrics.java) |
| material-service | [MaterialReservationService.java(../services/material-service/src/main/java/com/amatrix/sic/material/service/reservation/MaterialReservationService.java) |
| material-service | [ObjectDeletionService.java(../services/material-service/src/main/java/com/amatrix/sic/material/service/cleanup/ObjectDeletionService.java) |
| notification-service | [NotificationEventConsumers.java(../services/notification-service/src/main/java/com/amatrix/sic/notification/messaging/consumer/NotificationEventConsumers.java) |
| notification-service | [NotificationMetrics.java(../services/notification-service/src/main/java/com/amatrix/sic/notification/metrics/NotificationMetrics.java) |
| notification-service | [PendingNotificationDeliveryService.java(../services/notification-service/src/main/java/com/amatrix/sic/notification/service/PendingNotificationDeliveryService.java) |
| cloud-messaging | [CloudMessagingAutoConfiguration.java](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/CloudMessagingAutoConfiguration.java) |
| cloud-messaging | [ConsumedEventGuard.java](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/ConsumedEventGuard.java) |
| cloud-messaging | [MessagingMetrics.java](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/MessagingMetrics.java) |
| cloud-messaging | [OutboxRepository.java](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRepository.java) |

migration-tool 的 LegacyFileMigrationTool 另用 DriverManager/PreparedStatement 操作旧库及迁移清单。它是一次性 CLI，不属于上述 37 个业务文件；迁移方案需明确保留 JDBC 工具例外还是一并引入 MyBatis。

## 4. 手写 H2 建表的测试入口

检索条件：测试 Java 文件同时出现 CREATE TABLE 和 jdbc:h2 或 DriverManagerDataSource。这些测试并没有证明 MySQL 完整 Schema 与 Mapper XML 正确；迁移时应改用 Mapper 测试装配，并增加 MySQL 8.4 验证。

| 测试文件 |
| --- |
| [ConsumedEventGuardTest.java](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/ConsumedEventGuardTest.java) |
| [MaterialDeletionTransactionTest.java](../services/material-service/src/test/java/com/amatrix/sic/material/MaterialDeletionTransactionTest.java) |
| [MaterialReservationServiceTest.java(../services/material-service/src/test/java/com/amatrix/sic/material/service/reservation/MaterialReservationServiceTest.java) |
| [ObjectDeletionServiceTest.java(../services/material-service/src/test/java/com/amatrix/sic/material/service/cleanup/ObjectDeletionServiceTest.java) |
| [PendingNotificationDeliveryServiceTest.java(../services/notification-service/src/test/java/com/amatrix/sic/notification/service/PendingNotificationDeliveryServiceTest.java) |
| [ProjectAccessPolicyTest.java(../services/project-service/src/test/java/com/amatrix/sic/project/policy/ProjectAccessPolicyTest.java) |
| [ProjectApplicationSagaServiceTest.java(../services/project-service/src/test/java/com/amatrix/sic/project/service/saga/ProjectApplicationSagaServiceTest.java) |
| [ProjectExpertAccessEndedConsumerTest.java(../services/project-service/src/test/java/com/amatrix/sic/project/messaging/consumer/ProjectExpertAccessEndedConsumerTest.java) |
| [ProjectQueryServiceTest.java(../services/project-service/src/test/java/com/amatrix/sic/project/service/ProjectQueryServiceTest.java) |
| [ApplicationSagaCompensationTest.java](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/ApplicationSagaCompensationTest.java) |
| [NodeFormBusinessDataTest.java](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/NodeFormBusinessDataTest.java) |
| [WorkflowExpectedSequenceConcurrencyTest.java](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowExpectedSequenceConcurrencyTest.java) |
| [WorkflowOperationPolicyTest.java(../services/workflow-service/src/test/java/com/amatrix/sic/workflow/policy/WorkflowOperationPolicyTest.java) |

按上述条件共有 13 个文件。InfrastructureIntegrationTest 另直接调用 Flyway，在实际实施时须改为完整 SQL 初始化与结构验证；该测试没有 Docker 时会跳过。

## 5. 范围核对结果

- 15 个版本 SQL、42 张业务表、52 张服务内物理表定义与 37 个业务 JDBC 入口已经枚举。
- 当前版本 SQL 中没有 FOREIGN KEY 定义；初次切换不应顺带增加外键或跨库约束。
- 后续合并脚本还必须逐项折入 ALTER、独立 CREATE INDEX 和数据初始化，不能仅以建表数量判断完整。
- 本清单不包含实际数据库的结构漂移；实施前仍需 SHOW CREATE TABLE / information_schema 对照。
