# 核心代码注释覆盖清单

本清单与实际源码逐文件对应，配合 [统一规范](code-commenting-guide.md) 阅读。已覆盖表示该文件有职责或重要契约说明，关键方法按业务需要补充，不表示每个 getter、构造器和普通 CRUD 都有独立注释。

Controller 层已补充全部 24 个 Controller、114 个映射方法的详细 Javadoc，具体路径和作用见 [Controller API 索引](controller-api-index.md)。这 24 个文件均属于下列已覆盖 Java 文件，未扩大或改变既有豁免范围。

后端调用层已补充全部 8 个 OpenFeign 客户端、20 个映射方法，以及主要请求 DTO、跨服务 Command/Target、服务 token 缓存和 Trace Header 转发说明，见 [后端请求契约](backend-request-contracts.md)。这些文件仍计入原 Java 覆盖总数。

| 类型 | 已覆盖 | 按需豁免 | 核对总数 |
| --- | ---: | ---: | ---: |
| Java 主源码 | 187 | 55 | 242 |
| TypeScript / Vue | 56 | 6 | 62 |
| Mapper XML | 29 | 0 | 29 |

## 后端核心文件

### platform/cloud-contracts

| 文件 | 说明重点 |
| --- | --- |
| [ActorContext](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ActorContext.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [ApplicationSagaCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApplicationSagaCommand.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [ApplicationSagaResult](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApplicationSagaResult.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [DepartmentSummary](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/DepartmentSummary.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [DocumentGenerationCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/DocumentGenerationCommand.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [EventEnvelope](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/EventEnvelope.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [ExpertProjectGrantCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ExpertProjectGrantCommand.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [MaterialReservationCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/MaterialReservationCommand.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [MaterialReservationResult](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/MaterialReservationResult.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [ProjectAccessBatchRequest](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ProjectAccessBatchRequest.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [ProjectAccessResult](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ProjectAccessResult.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [ProjectOperationTarget](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ProjectOperationTarget.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [StateRecordContract](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/StateRecordContract.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [StateTransitionContract](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/StateTransitionContract.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [UserSummary](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/UserSummary.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |

### platform/cloud-messaging

| 文件 | 说明重点 |
| --- | --- |
| [CloudMessagingAutoConfiguration](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/CloudMessagingAutoConfiguration.java) | 职责说明 |
| [ConsumedEventGuard](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/ConsumedEventGuard.java) | 职责说明；`begin` |
| [JdbcLatencyObserver](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/JdbcLatencyObserver.java) | 职责说明 |
| [MessagingMetrics](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/MessagingMetrics.java) | 职责说明 |
| [OutboxProperties](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxProperties.java) | 职责说明 |
| [OutboxPublisher](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxPublisher.java) | 职责说明；`publishPending` |
| [OutboxRepository](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRepository.java) | 职责说明；`claimPending` |
| [OutboxService](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxService.java) | 职责说明 |
| [MessagingMapper](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/dao/MessagingMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |

### platform/cloud-security

| 文件 | 说明重点 |
| --- | --- |
| [ActorContextFactory](../platform/cloud-security/src/main/java/com/amatrix/sic/security/ActorContextFactory.java) | 职责说明 |
| [ActorContextResolver](../platform/cloud-security/src/main/java/com/amatrix/sic/security/ActorContextResolver.java) | 职责说明 |
| [ApiExceptionHandler](../platform/cloud-security/src/main/java/com/amatrix/sic/security/ApiExceptionHandler.java) | 职责说明 |
| [AudienceValidator](../platform/cloud-security/src/main/java/com/amatrix/sic/security/AudienceValidator.java) | 职责说明 |
| [CanAccessModule](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanAccessModule.java) | 职责说明 |
| [CanAccessProject](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanAccessProject.java) | 职责说明 |
| [CanModifyProject](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanModifyProject.java) | 职责说明 |
| [CanOperateModule](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanOperateModule.java) | 职责说明 |
| [CanReadNodeForm](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanReadNodeForm.java) | 职责说明 |
| [CloudResourceServerAutoConfiguration](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CloudResourceServerAutoConfiguration.java) | 职责说明 |
| [CloudSecurityProperties](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CloudSecurityProperties.java) | 职责说明 |
| [FeignTracePropagationAutoConfiguration](../platform/cloud-security/src/main/java/com/amatrix/sic/security/FeignTracePropagationAutoConfiguration.java) | 职责说明 |
| [JsonSecurityHandlers](../platform/cloud-security/src/main/java/com/amatrix/sic/security/JsonSecurityHandlers.java) | 职责说明 |
| [JwtAuthoritiesConverter](../platform/cloud-security/src/main/java/com/amatrix/sic/security/JwtAuthoritiesConverter.java) | 职责说明 |
| [RequestLoggingContextFilter](../platform/cloud-security/src/main/java/com/amatrix/sic/security/RequestLoggingContextFilter.java) | 职责说明 |

### services/gateway-service

| 文件 | 说明重点 |
| --- | --- |
| [GatewayErrorWriter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/error/GatewayErrorWriter.java) | 职责说明 |
| [GatewayJsonErrorHandler](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/error/GatewayJsonErrorHandler.java) | 职责说明 |
| [GatewayRateLimitProperties](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/config/properties/GatewayRateLimitProperties.java) | 职责说明 |
| [GatewayRateLimitWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/GatewayRateLimitWebFilter.java) | 职责说明 |
| [GatewayRequestLoggingFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/GatewayRequestLoggingFilter.java) | 职责说明 |
| [GatewaySecurityConfig](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/config/GatewaySecurityConfig.java) | 职责说明 |
| [GatewaySecurityProperties](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/config/properties/GatewaySecurityProperties.java) | 职责说明 |
| [SessionValidityAuthorizationManager](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/security/SessionValidityAuthorizationManager.java) | 职责说明；`authorize` |
| [SseTokenRelayWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/SseTokenRelayWebFilter.java) | 职责说明 |
| [TraceIdWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/TraceIdWebFilter.java) | 职责说明 |

### services/iam-service

| 文件 | 说明重点 |
| --- | --- |
| [BootstrapAdminInitializer](../services/iam-service/src/main/java/com/amatrix/sic/iam/bootstrap/BootstrapAdminInitializer.java) | 职责说明 |
| [IamSecurityConfig](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/IamSecurityConfig.java) | 职责说明 |
| [BootstrapAdminProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/properties/BootstrapAdminProperties.java) | 职责说明 |
| [IamJwtProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/properties/IamJwtProperties.java) | 职责说明 |
| [ServiceTokenProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/properties/ServiceTokenProperties.java) | 职责说明 |
| [AuthController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/AuthController.java) | 职责说明 |
| [ExpertQualificationController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/ExpertQualificationController.java) | 职责说明 |
| [IamAdminController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java) | 职责说明 |
| [JwkController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/JwkController.java) | 职责说明 |
| [InternalIdentityController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java) | 职责说明 |
| [AdminMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/AdminMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [IamRepository](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/IamRepository.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [IdentityMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/IdentityMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [OverviewMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/OverviewMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [QualificationMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/QualificationMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [SessionMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/SessionMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [JwtTokenIssuer](../services/iam-service/src/main/java/com/amatrix/sic/iam/security/JwtTokenIssuer.java) | 职责说明 |
| [RsaKeyProvider](../services/iam-service/src/main/java/com/amatrix/sic/iam/security/RsaKeyProvider.java) | 职责说明 |
| [AuthApplicationService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/AuthApplicationService.java) | 职责说明；`login`、`register`、`logout` |
| [ExpertQualificationService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/ExpertQualificationService.java) | 职责说明 |
| [IamAdminService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/IamAdminService.java) | 职责说明 |
| [IamOverviewService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/IamOverviewService.java) | 职责说明 |
| [SessionVersionService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/SessionVersionService.java) | 职责说明；`publishLogin`、`publishAfterCommit`、`recover` |

### services/material-service

| 文件 | 说明重点 |
| --- | --- |
| [MaterialProjectAccessClient](../services/material-service/src/main/java/com/amatrix/sic/material/client/MaterialProjectAccessClient.java) | 职责说明 |
| [MaterialTokenClient](../services/material-service/src/main/java/com/amatrix/sic/material/client/MaterialTokenClient.java) | 职责说明 |
| [MaterialTokenProvider](../services/material-service/src/main/java/com/amatrix/sic/material/client/auth/MaterialTokenProvider.java) | 职责说明 |
| [MaterialMessagingConfiguration](../services/material-service/src/main/java/com/amatrix/sic/material/config/MaterialMessagingConfiguration.java) | 职责说明 |
| [MinioConfiguration](../services/material-service/src/main/java/com/amatrix/sic/material/config/MinioConfiguration.java) | 职责说明 |
| [MaterialClientProperties](../services/material-service/src/main/java/com/amatrix/sic/material/config/properties/MaterialClientProperties.java) | 职责说明 |
| [MaterialProperties](../services/material-service/src/main/java/com/amatrix/sic/material/config/properties/MaterialProperties.java) | 职责说明 |
| [MaterialController](../services/material-service/src/main/java/com/amatrix/sic/material/controller/MaterialController.java) | 职责说明 |
| [InternalMaterialReservationController](../services/material-service/src/main/java/com/amatrix/sic/material/controller/internal/InternalMaterialReservationController.java) | 职责说明 |
| [GeneratedDocumentMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/GeneratedDocumentMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [MaterialMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/MaterialMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [MaterialReservationMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/MaterialReservationMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ObjectDeletionMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/ObjectDeletionMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [GeneratedDocumentEventConsumer](../services/material-service/src/main/java/com/amatrix/sic/material/messaging/consumer/GeneratedDocumentEventConsumer.java) | 职责说明 |
| [MaterialReservationEventConsumer](../services/material-service/src/main/java/com/amatrix/sic/material/messaging/consumer/MaterialReservationEventConsumer.java) | 职责说明 |
| [MaterialCleanupMetrics](../services/material-service/src/main/java/com/amatrix/sic/material/metrics/MaterialCleanupMetrics.java) | 职责说明 |
| [MaterialAccessPolicy](../services/material-service/src/main/java/com/amatrix/sic/material/policy/MaterialAccessPolicy.java) | 职责说明 |
| [MaterialApplicationService](../services/material-service/src/main/java/com/amatrix/sic/material/service/MaterialApplicationService.java) | 职责说明；`upload`、`deleteLocked` |
| [ObjectDeletionService](../services/material-service/src/main/java/com/amatrix/sic/material/service/cleanup/ObjectDeletionService.java) | 职责说明；`deletePendingObjects` |
| [GeneratedDocumentRenderer](../services/material-service/src/main/java/com/amatrix/sic/material/service/document/GeneratedDocumentRenderer.java) | 职责说明 |
| [GeneratedDocumentService](../services/material-service/src/main/java/com/amatrix/sic/material/service/document/GeneratedDocumentService.java) | 职责说明；`generate` |
| [MaterialReservationService](../services/material-service/src/main/java/com/amatrix/sic/material/service/reservation/MaterialReservationService.java) | 职责说明；`reserve`、`confirm`、`release`、`cleanup` |
| [MinioObjectStorage](../services/material-service/src/main/java/com/amatrix/sic/material/storage/MinioObjectStorage.java) | 职责说明 |

### services/notification-service

| 文件 | 说明重点 |
| --- | --- |
| [NotificationMessagingConfiguration](../services/notification-service/src/main/java/com/amatrix/sic/notification/config/NotificationMessagingConfiguration.java) | 职责说明 |
| [SseController](../services/notification-service/src/main/java/com/amatrix/sic/notification/controller/SseController.java) | 职责说明 |
| [NotificationMapper](../services/notification-service/src/main/java/com/amatrix/sic/notification/dao/NotificationMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [NotificationEventConsumers](../services/notification-service/src/main/java/com/amatrix/sic/notification/messaging/consumer/NotificationEventConsumers.java) | 职责说明 |
| [NotificationMetrics](../services/notification-service/src/main/java/com/amatrix/sic/notification/metrics/NotificationMetrics.java) | 职责说明 |
| [PendingNotificationDeliveryService](../services/notification-service/src/main/java/com/amatrix/sic/notification/service/PendingNotificationDeliveryService.java) | 职责说明；`replayTo`、`retryQueued` |
| [SseConnectionRegistry](../services/notification-service/src/main/java/com/amatrix/sic/notification/sse/SseConnectionRegistry.java) | 职责说明；`subscribe` |

### services/project-service

| 文件 | 说明重点 |
| --- | --- |
| [IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/IamDirectoryClient.java) | 职责说明 |
| [ServiceTokenClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/ServiceTokenClient.java) | 职责说明 |
| [WorkflowSagaClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/WorkflowSagaClient.java) | 职责说明 |
| [InternalTokenProvider](../services/project-service/src/main/java/com/amatrix/sic/project/client/auth/InternalTokenProvider.java) | 职责说明 |
| [ProjectSnapshotMessagingConfiguration](../services/project-service/src/main/java/com/amatrix/sic/project/config/ProjectSnapshotMessagingConfiguration.java) | 职责说明 |
| [ServiceClientProperties](../services/project-service/src/main/java/com/amatrix/sic/project/config/properties/ServiceClientProperties.java) | 职责说明 |
| [ProjectAdminController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAdminController.java) | 职责说明 |
| [ProjectAuditController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuditController.java) | 职责说明 |
| [ProjectAuthorizationController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java) | 职责说明 |
| [ProjectController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectController.java) | 职责说明 |
| [ProjectDepartmentController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectDepartmentController.java) | 职责说明 |
| [InternalProjectController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/internal/InternalProjectController.java) | 职责说明 |
| [ApplicationSagaMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ApplicationSagaMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ProjectAuditMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectAuditMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ProjectAuthorizationMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectAuthorizationMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ProjectQueryMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectQueryMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ProjectScopeMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectScopeMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [UserSnapshotMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/UserSnapshotMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ProjectExpertAccessEndedConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectExpertAccessEndedConsumer.java) | 职责说明 |
| [ProjectUserSnapshotConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectUserSnapshotConsumer.java) | 职责说明 |
| [ProjectWorkflowLifecycleConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectWorkflowLifecycleConsumer.java) | 职责说明 |
| [ProjectSagaMetrics](../services/project-service/src/main/java/com/amatrix/sic/project/metrics/ProjectSagaMetrics.java) | 职责说明 |
| [ProjectAccessPolicy](../services/project-service/src/main/java/com/amatrix/sic/project/policy/ProjectAccessPolicy.java) | 职责说明；`decideBatch`、`evaluate` |
| [ProjectAuditService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectAuditService.java) | 职责说明 |
| [ProjectAuthorizationService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectAuthorizationService.java) | 职责说明；`detail`、`upsertMembers`、`assignGrants`、`revokeBatch`、`syncExpertGrant` |
| [ProjectDepartmentService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectDepartmentService.java) | 职责说明；`requireEnabled`、`assignMissing` |
| [ProjectFinanceReadinessService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectFinanceReadinessService.java) | 职责说明；`readiness` |
| [ProjectQueryService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectQueryService.java) | 职责说明 |
| [ProjectUserDirectory](../services/project-service/src/main/java/com/amatrix/sic/project/service/directory/ProjectUserDirectory.java) | 职责说明；`requireAll`、`fetchAll`、`allEnabled`、`upsert` |
| [ProjectApplicationSagaService](../services/project-service/src/main/java/com/amatrix/sic/project/service/saga/ProjectApplicationSagaService.java) | 职责说明；`start`、`recoverIncompleteConfirmations`、`confirm`、`compensate` |

### services/workflow-service

| 文件 | 说明重点 |
| --- | --- |
| [WorkflowAssetInitializer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/bootstrap/WorkflowAssetInitializer.java) | 职责说明；`run` |
| [MaterialReservationClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/MaterialReservationClient.java) | 职责说明 |
| [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/ProjectAccessClient.java) | 职责说明 |
| [WorkflowServiceTokenClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/WorkflowServiceTokenClient.java) | 职责说明 |
| [WorkflowInternalTokenProvider](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/auth/WorkflowInternalTokenProvider.java) | 职责说明 |
| [WorkflowSnapshotMessagingConfiguration](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/config/WorkflowSnapshotMessagingConfiguration.java) | 职责说明 |
| [WorkflowClientProperties](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/config/properties/WorkflowClientProperties.java) | 职责说明 |
| [ExpertPersonalReviewController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertPersonalReviewController.java) | 职责说明 |
| [ExpertReviewController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java) | 职责说明 |
| [ModuleBusinessDataController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ModuleBusinessDataController.java) | 职责说明 |
| [NodeFormController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java) | 职责说明 |
| [NoticeController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NoticeController.java) | 职责说明 |
| [StructuredBusinessController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java) | 职责说明 |
| [WorkflowDefinitionController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java) | 职责说明 |
| [WorkflowRuntimeController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowRuntimeController.java) | 职责说明 |
| [WorkflowWorkbenchController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowWorkbenchController.java) | 职责说明 |
| [InternalApplicationSagaController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/internal/InternalApplicationSagaController.java) | 职责说明 |
| [ExpertReviewMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/ExpertReviewMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ExpertReviewViewMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/ExpertReviewViewMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [NodeFormMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/NodeFormMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [NoticeMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/NoticeMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ProjectSnapshotMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/ProjectSnapshotMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [StructuredBusinessMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/StructuredBusinessMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [WorkflowCatalogMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowCatalogMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [WorkflowDefinitionMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowDefinitionMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [WorkflowRuntimeMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowRuntimeMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [WorkflowSagaMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowSagaMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [WorkflowScopeMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowScopeMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [WorkflowUserSnapshotMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowUserSnapshotMapper.java) | 职责说明；对应 Mapper SQL 的用途与边界 |
| [ExpertReviewViewRepository](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/review/ExpertReviewViewRepository.java) | 职责说明；对应 Mapper SQL 的用途与边界；`personalBatchDetail`、`moduleReviews` |
| [ExpertReviewDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/ExpertReviewDtos.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [WorkflowDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/WorkflowDtos.java) | 职责说明；跨服务或请求契约，重要 ID / 版本按需说明 |
| [ExpertReviewCompletedConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/ExpertReviewCompletedConsumer.java) | 职责说明 |
| [WorkflowProjectAuthorizationConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowProjectAuthorizationConsumer.java) | 职责说明 |
| [WorkflowProjectLifecycleConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowProjectLifecycleConsumer.java) | 职责说明 |
| [WorkflowUserSnapshotConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowUserSnapshotConsumer.java) | 职责说明 |
| [ModuleSuccessorPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/ModuleSuccessorPolicy.java) | 职责说明 |
| [WorkflowOperationPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/WorkflowOperationPolicy.java) | 职责说明；`operableModuleIds`、`runtimeAccess` |
| [WorkflowProjectAccessPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/WorkflowProjectAccessPolicy.java) | 职责说明 |
| [NoticeService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/business/NoticeService.java) | 职责说明 |
| [StructuredBusinessService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/business/StructuredBusinessService.java) | 职责说明 |
| [WorkflowDefinitionModelStore](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/definition/WorkflowDefinitionModelStore.java) | 职责说明 |
| [WorkflowDefinitionService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/definition/WorkflowDefinitionService.java) | 职责说明 |
| [NodeFormService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/form/NodeFormService.java) | 职责说明；`persistTransition`、`save`、`normalizeStored` |
| [ExpertPersonalReviewService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/review/ExpertPersonalReviewService.java) | 职责说明；`list`、`detail` |
| [ExpertReviewService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/review/ExpertReviewService.java) | 职责说明；`assign`、`assignLocked`、`submit`、`refreshAggregate`、`requireOperator` |
| [WorkflowRuntimeService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowRuntimeService.java) | 职责说明；`transition`、`runtimeView`、`lockedTransition`、`recoverEndedExpertAccess`、`endExpertAccessBatch`、`ensureSuccessor` |
| [WorkflowTransitionActionService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowTransitionActionService.java) | 职责说明 |
| [WorkflowWorkbenchQueryService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowWorkbenchQueryService.java) | 职责说明；`query` |
| [ApplicationSagaWorkflowService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/saga/ApplicationSagaWorkflowService.java) | 职责说明；`create`、`compensate` |
| [WorkflowAssetCatalog](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowAssetCatalog.java) | 职责说明 |
| [WorkflowDefinitionCatalog](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowDefinitionCatalog.java) | 职责说明 |
| [WorkflowDefinitionNavigator](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowDefinitionNavigator.java) | 职责说明 |
| [WorkflowBpmnParseResult](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/model/WorkflowBpmnParseResult.java) | 职责说明 |
| [FlowableBpmnDefinitionParser](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/parser/FlowableBpmnDefinitionParser.java) | 职责说明 |

## 前端核心文件

| 文件 | 说明重点 |
| --- | --- |
| [api/admin.ts](../../SicProjectIS_fronted/src/api/admin.ts) | 封装用户角色、启停、权限矩阵、概览和审计接口；这里描述请求契约，实际管理权限由后端判定。 |
| [api/auth.ts](../../SicProjectIS_fronted/src/api/auth.ts) | 封装注册、登录、当前身份和注销接口；登录返回的身份交由 auth store 管理，不在请求层自行推导业务权限。 |
| [api/client.ts](../../SicProjectIS_fronted/src/api/client.ts) | 统一请求 Gateway，附带当前 token 并解包 success/data/message。仅 401 清理会话，其他失败保留身份并交调用页面处理。 关键函数：`apiRequest`。 |
| [api/departmentMembers.ts](../../SicProjectIS_fronted/src/api/departmentMembers.ts) | 封装部门与成员管理，部门筛选和可编辑角色范围由后端返回；分配部门时省略 roleCodes 与显式替换角色含义不同。 |
| [api/expertQualification.ts](../../SicProjectIS_fronted/src/api/expertQualification.ts) | 封装专家资格申请、本人记录和部门/科研审批；取得专家角色依靠审批结果，提交申请本身不授予资格。 |
| [api/expertReviews.ts](../../SicProjectIS_fronted/src/api/expertReviews.ts) | 封装评审批次、专家指派、评分以及个人历史。个人记录接口与通用批次接口权限不同，评分完成后应读取本人记录。 关键函数：`getMyReview`、`submitExpertScore`、`removeExpertAssignment`。 |
| [api/materials.ts](../../SicProjectIS_fronted/src/api/materials.ts) | 按具体材料版本上传、下载或删除文件；文件请求附带认证，主材料 ID 与 materialVersionId 不能混用。 关键函数：`uploadMaterialVersion`、`downloadMaterialVersion`。 |
| [api/nodeForms.ts](../../SicProjectIS_fronted/src/api/nodeForms.ts) | 按 formCode 及运行时上下文读写节点表单；草稿保存、记录维护与正式流程提交是不同操作。 |
| [api/projects.ts](../../SicProjectIS_fronted/src/api/projects.ts) | 封装项目列表、成员和范围授权，以及申请 Saga 入口；批量接口保持同步整体提交，不在客户端拆成逐条写入。 关键函数：`assignMissingProjectDepartment`、`startProjectApplication`。 |
| [api/stateMachine.ts](../../SicProjectIS_fronted/src/api/stateMachine.ts) | 封装模块运行时、历史、任务和迁移接口；提交携带最新 expectedSeq，冲突后重新读取而不是盲目重试写请求。 |
| [api/workflowWorkbench.ts](../../SicProjectIS_fronted/src/api/workflowWorkbench.ts) | 封装工作台查询，区分旧待办接口与新的可见/办理历史分页；hasMore 由后端授权筛选后的结果决定。 |
| [components/ProjectDepartmentRepair.vue](../../SicProjectIS_fronted/src/components/ProjectDepartmentRepair.vue) | 为历史空部门项目提供一次性归属补充入口，只向全局管理员展示操作；选择启用部门并填写原因后由后端加锁校验。 关键函数：`save`。 |
| [components/expert-qualification/ExpertQualificationReviewModule.vue](../../SicProjectIS_fronted/src/components/expert-qualification/ExpertQualificationReviewModule.vue) | 展示资格申请并收集审批动作，部门和科研审批阶段由后端状态与权限决定；组件通过事件通知父页面刷新。 |
| [components/workflow/BpmnViewerPanel.vue](../../SicProjectIS_fronted/src/components/workflow/BpmnViewerPanel.vue) | 展示项目运行实例使用的 BPMN 并标记当前节点；流程图中的节点代码与业务状态、数据库节点 ID 分别处理。 |
| [components/workflow/ExpertReviewPanel.vue](../../SicProjectIS_fronted/src/components/workflow/ExpertReviewPanel.vue) | 通用流程页中的指派与评分入口，任务范围取自当前模块和轮次；本人评分提交后转到专用历史页，避免继续请求已失去权限的项目详情。 |
| [components/workflow/MaterialRequirementPanel.vue](../../SicProjectIS_fronted/src/components/workflow/MaterialRequirementPanel.vue) | 展示当前节点必需材料并维护选中的版本 ID；上传成功不代表流程已引用，最终引用和确认由后端提交链路处理。 |
| [components/workflow/NodeFormPanel.vue](../../SicProjectIS_fronted/src/components/workflow/NodeFormPanel.vue) | 按服务端表单定义展示节点数据并提交对应契约，区分只读、草稿及记录维护；实际可写范围由后端再次校验。 |
| [components/workflow/ProcessSteps.vue](../../SicProjectIS_fronted/src/components/workflow/ProcessSteps.vue) | 按节点与状态历史构建进度展示；展示顺序用于阅读流程，不承担后继模块创建或状态推进。 |
| [components/workflow/RecordTimeline.vue](../../SicProjectIS_fronted/src/components/workflow/RecordTimeline.vue) | 展示已提交状态记录的时间线，seq、roundNo 和 stateRecordId 分别代表迁移序号、业务轮次和记录主键。 |
| [components/workflow/RuntimeTaskPanel.vue](../../SicProjectIS_fronted/src/components/workflow/RuntimeTaskPanel.vue) | 展示当前任务与办理信息，以运行时返回的可操作状态控制按钮；候选角色不直接代替项目授权判定。 |
| [components/workflow/TransitionActionDialog.vue](../../SicProjectIS_fronted/src/components/workflow/TransitionActionDialog.vue) | 收集迁移动作、意见及材料版本等提交信息，通过组件事件交由父页面调用运行时接口。 |
| [components/workflow/WorkflowNodeWorkPanel.vue](../../SicProjectIS_fronted/src/components/workflow/WorkflowNodeWorkPanel.vue) | 从当前节点表单定义组织可写字段、材料及办理动作；系统字段不作为用户输入，专家评审和文档节点使用专门展示。 关键函数：`buildRequest`。 |
| [components/workflow/WorkflowTimeline.vue](../../SicProjectIS_fronted/src/components/workflow/WorkflowTimeline.vue) | 组合节点与历史的流程时间线，供浏览已发生的业务动作；历史可见性由父页面读取权限决定。 |
| [composables/useNodeFormHelpers.ts](../../SicProjectIS_fronted/src/composables/useNodeFormHelpers.ts) | 把扁平表单转换为后端按 dataKind 分组的业务请求，并生成节点快捷办理配置；系统关联 ID 取自运行时上下文。 关键函数：`buildRequestFromFlatForm`、`detectExpertSubType`、`quickActionConfig`、`useNodeFormHelpers`。 |
| [composables/useWorkflowEvents.ts](../../SicProjectIS_fronted/src/composables/useWorkflowEvents.ts) | 管理页面实时刷新通道：SSE 重连、token 切换、焦点与网络恢复、可见页定时刷新及卸载清理。事件仅提示重查，不能替代业务接口鉴权。 关键函数：`useWorkflowEvents`、`refresh`、`open`、`disconnect`。 |
| [features/node-forms/common/NodeFormsDebugView.vue](../../SicProjectIS_fronted/src/features/node-forms/common/NodeFormsDebugView.vue) | 用于节点表单联调的通用界面，复用公共字段与请求转换逻辑；所有保存与维护操作仍经过现有业务接口校验。 |
| [layouts/AppShell.vue](../../SicProjectIS_fronted/src/layouts/AppShell.vue) | 提供业务页面导航及当前身份菜单。角色与权限决定前端入口提示，页面存在不等于当前账号具有后台操作资格。 |
| [layouts/PermissionWorkbenchLayout.vue](../../SicProjectIS_fronted/src/layouts/PermissionWorkbenchLayout.vue) | 组织权限管理工作台的布局与导航，具体角色、成员和授权变更由对应页面及后端接口处理。 |
| [main.ts](../../SicProjectIS_fronted/src/main.ts) | 装配 Vue、Pinia、Router 与 Element Plus 并挂载应用；业务请求经公共客户端访问 Gateway。 |
| [router/index.ts](../../SicProjectIS_fronted/src/router/index.ts) | 定义前端页面路由与登录守卫；守卫只控制未登录入口，不承担项目范围或当前节点的服务端授权判断。 |
| [stores/auth.ts](../../SicProjectIS_fronted/src/stores/auth.ts) | 管理当前 token 和身份的持久化、登录更新及退出清理。身份用于页面展示与入口提示，最终业务权限仍由服务端校验。 关键函数：`applyLoginResponse`、`logout`、`clearSession`。 |
| [types/admin.ts](../../SicProjectIS_fronted/src/types/admin.ts) | 描述管理概览、角色权限和审计响应；前端能力字段用于展示，不替代请求时的后端权限检查。 |
| [types/auth.ts](../../SicProjectIS_fronted/src/types/auth.ts) | 描述注册登录、当前身份和权限码结构；部门可为空，角色变更后需重新登录取得新的 token 与显示身份。 |
| [types/departmentMembers.ts](../../SicProjectIS_fronted/src/types/departmentMembers.ts) | 描述可管理部门、成员及可编辑角色范围；管理能力随响应返回，不以同部门关系推导任意管理权限。 |
| [types/expertQualification.ts](../../SicProjectIS_fronted/src/types/expertQualification.ts) | 描述资格申请及分阶段审批结果，资格状态与已分配的 EXPERT 角色是相关但不同的数据。 |
| [types/nodeForms.ts](../../SicProjectIS_fronted/src/types/nodeForms.ts) | 声明运行时、表单定义、业务数据、任务和材料版本契约；多种 ID 和序号不能互换，写入模式与 canOperate 共同约束界面操作。 |
| [types/project.ts](../../SicProjectIS_fronted/src/types/project.ts) | 声明项目主数据、申请和成员授权请求；项目归属部门不随账号部门调整自动改变，授权范围限定模块、轮次和节点。 |
| [types/workflow.ts](../../SicProjectIS_fronted/src/types/workflow.ts) | 声明工作台与实时刷新事件；模块 finished 与项目 lifecycleStage 分开解释，事件提示后仍需重新读取当前权限和状态。 |
| [utils/displayLabels.ts](../../SicProjectIS_fronted/src/utils/displayLabels.ts) | 把后台状态、角色和业务代码映射为展示文本；保留未知代码回退，显示文本不参加权限或状态机判断。 |
| [utils/nodeFormFields.ts](../../SicProjectIS_fronted/src/utils/nodeFormFields.ts) | 集中维护 dataKind 的字段、默认值和必填提示，过滤系统字段；客户端提示与转换不替代后端校验。 关键函数：`fieldsForKind`、`fieldType`、`filteredFieldsForKind`、`defaultsForKind`、`isTextareaField`。 |
| [views/AdminExpertQualificationView.vue](../../SicProjectIS_fronted/src/views/AdminExpertQualificationView.vue) | 科研或系统管理员的专家资格审批页，按当前审批阶段提交结果并刷新记录；审批成功后新身份需重新登录获取。 |
| [views/AdminOverviewView.vue](../../SicProjectIS_fronted/src/views/AdminOverviewView.vue) | 展示管理统计及可访问管理入口，统计信息是只读概览，不据此决定单个项目的办理资格。 |
| [views/AdminProjectAuthorizationsView.vue](../../SicProjectIS_fronted/src/views/AdminProjectAuthorizationsView.vue) | 全局项目授权管理入口，维护成员与范围授权并补充历史空部门；批量变更使用后端整体提交接口。 |
| [views/AuditLogsView.vue](../../SicProjectIS_fronted/src/views/AuditLogsView.vue) | 按过滤条件读取身份和项目授权审计；展示服务端已有记录，不在前端重建或补写审计事实。 |
| [views/DashboardView.vue](../../SicProjectIS_fronted/src/views/DashboardView.vue) | 组合当前身份和项目等概览信息，提供业务导航；概览计数不代替分页工作台的完整记录。 |
| [views/DepartmentMembersView.vue](../../SicProjectIS_fronted/src/views/DepartmentMembersView.vue) | 管理后端返回的可管理部门与成员，区分仅分配部门和明确替换可编辑角色，防止部门调整意外丢失身份角色。 |
| [views/ExpertQualificationView.vue](../../SicProjectIS_fronted/src/views/ExpertQualificationView.vue) | 展示本人资格申请与审批进度；申请提交成功后等待审批，不自行添加专家角色或跳过部门审批。 |
| [views/ExpertReviewWorkbenchView.vue](../../SicProjectIS_fronted/src/views/ExpertReviewWorkbenchView.vue) | 专家专用任务与本人历史界面。提交后等待批次完成，保留个人记录；材料仅在当前 canReview 时加载，刷新时可保留尚未提交草稿。 关键函数：`loadList`、`selectReview`、`submit`。 |
| [views/LoginView.vue](../../SicProjectIS_fronted/src/views/LoginView.vue) | 收集登录凭据并更新 auth store，再进入目标页面；错误提示使用请求结果，不将基础设施失败自动当成密码错误。 |
| [views/ProjectAuthorizationView.vue](../../SicProjectIS_fronted/src/views/ProjectAuthorizationView.vue) | 管理单个可访问项目的成员、专家和财务范围授权，并为管理员提供空部门补充；后端能力控制可见操作。 |
| [views/ProjectsView.vue](../../SicProjectIS_fronted/src/views/ProjectsView.vue) | 读取当前可访问项目并按生命周期展示；项目可读不意味着可以操作其所有流程节点。 |
| [views/RegisterView.vue](../../SicProjectIS_fronted/src/views/RegisterView.vue) | 提交注册信息并建立初始登录状态；部门、业务角色与专家资格在后续管理和审批流程中取得。 |
| [views/RolePermissionMatrixView.vue](../../SicProjectIS_fronted/src/views/RolePermissionMatrixView.vue) | 编辑角色对应权限并提交后台校验；权限变更影响授权版本，界面不能沿用旧 token 假定身份持续有效。 |
| [views/UserRoleManagementView.vue](../../SicProjectIS_fronted/src/views/UserRoleManagementView.vue) | 管理用户角色及启停状态，提交后刷新身份详情；改变用户身份与给用户授权某个项目是两个步骤。 |
| [views/WorkflowDetailView.vue](../../SicProjectIS_fronted/src/views/WorkflowDetailView.vue) | 组合模块运行时、节点表单、材料与可执行动作；正式提交使用当前序号和选中版本，提交成功后重新读取状态，纯专家使用专用评审入口。 关键函数：`loadDetail`、`submitCurrentDraft`。 |
| [views/WorkflowWorkbenchView.vue](../../SicProjectIS_fronted/src/views/WorkflowWorkbenchView.vue) | 按当前权限分页展示可见流程、待办、办理历史和已结束模块。取消旧请求防止过期响应覆盖，新申请要求所属部门，SSE 只触发重查。 关键函数：`loadItems`、`submitApplicationStart`。 |

## Mapper XML

注释位于 SQL 元素外。全部文件说明 DAO 用途，复杂锁查询、条件更新和幂等语句另附局部说明；普通 CRUD 不机械重复。

| 文件 | 明确说明的重点语句 |
| --- | --- |
| [MessagingMapper](../platform/cloud-messaging/src/main/resources/mapper/messaging/MessagingMapper.xml) | `insertOutbox`、`claim`、`published`、`failed`、`consume` |
| [AdminMapper](../services/iam-service/src/main/resources/mapper/iam/AdminMapper.xml) | `lockAllRoles`、`lockedVersions`、`incrementVersions`、`moveUser` |
| [IdentityMapper](../services/iam-service/src/main/resources/mapper/iam/IdentityMapper.xml) | `summaries`、`rolesForUsers` |
| [OverviewMapper](../services/iam-service/src/main/resources/mapper/iam/OverviewMapper.xml) | DAO 用途；存在行锁时说明调用方事务前提 |
| [QualificationMapper](../services/iam-service/src/main/resources/mapper/iam/QualificationMapper.xml) | DAO 用途；存在行锁时说明调用方事务前提 |
| [SessionMapper](../services/iam-service/src/main/resources/mapper/iam/SessionMapper.xml) | `lockedVersion` |
| [GeneratedDocumentMapper](../services/material-service/src/main/resources/mapper/material/GeneratedDocumentMapper.xml) | `insert` |
| [MaterialMapper](../services/material-service/src/main/resources/mapper/material/MaterialMapper.xml) | `reserved`、`scheduleDeletion` |
| [MaterialReservationMapper](../services/material-service/src/main/resources/mapper/material/MaterialReservationMapper.xml) | `available`、`confirm`、`release`、`expire` |
| [ObjectDeletionMapper](../services/material-service/src/main/resources/mapper/material/ObjectDeletionMapper.xml) | DAO 用途；存在行锁时说明调用方事务前提 |
| [NotificationMapper](../services/notification-service/src/main/resources/mapper/notification/NotificationMapper.xml) | `queued` |
| [ApplicationSagaMapper](../services/project-service/src/main/resources/mapper/project/ApplicationSagaMapper.xml) | `recent`、`recoverable` |
| [ProjectAuditMapper](../services/project-service/src/main/resources/mapper/project/ProjectAuditMapper.xml) | DAO 用途；存在行锁时说明调用方事务前提 |
| [ProjectAuthorizationMapper](../services/project-service/src/main/resources/mapper/project/ProjectAuthorizationMapper.xml) | `lockProject`、`financeCandidates`、`assignDepartment`、`duplicateGrant`、`endExpertAccess` |
| [ProjectQueryMapper](../services/project-service/src/main/resources/mapper/project/ProjectQueryMapper.xml) | `accessible` |
| [ProjectScopeMapper](../services/project-service/src/main/resources/mapper/project/ProjectScopeMapper.xml) | `grants` |
| [UserSnapshotMapper](../services/project-service/src/main/resources/mapper/project/UserSnapshotMapper.xml) | `upsert` |
| [ExpertReviewMapper](../services/workflow-service/src/main/resources/mapper/workflow/ExpertReviewMapper.xml) | `lockModule`、`batch`、`lockAssignment`、`scores` |
| [ExpertReviewViewMapper](../services/workflow-service/src/main/resources/mapper/workflow/ExpertReviewViewMapper.xml) | `mine` |
| [NodeFormMapper](../services/workflow-service/src/main/resources/mapper/workflow/NodeFormMapper.xml) | `store` |
| [NoticeMapper](../services/workflow-service/src/main/resources/mapper/workflow/NoticeMapper.xml) | DAO 用途；存在行锁时说明调用方事务前提 |
| [ProjectSnapshotMapper](../services/workflow-service/src/main/resources/mapper/workflow/ProjectSnapshotMapper.xml) | `owner` |
| [StructuredBusinessMapper](../services/workflow-service/src/main/resources/mapper/workflow/StructuredBusinessMapper.xml) | DAO 用途；存在行锁时说明调用方事务前提 |
| [WorkflowCatalogMapper](../services/workflow-service/src/main/resources/mapper/workflow/WorkflowCatalogMapper.xml) | DAO 用途；存在行锁时说明调用方事务前提 |
| [WorkflowDefinitionMapper](../services/workflow-service/src/main/resources/mapper/workflow/WorkflowDefinitionMapper.xml) | `replaceEmpty` |
| [WorkflowRuntimeMapper](../services/workflow-service/src/main/resources/mapper/workflow/WorkflowRuntimeMapper.xml) | `module`、`workbenchCandidates`、`advanceModule`、`reference` |
| [WorkflowSagaMapper](../services/workflow-service/src/main/resources/mapper/workflow/WorkflowSagaMapper.xml) | `find` |
| [WorkflowScopeMapper](../services/workflow-service/src/main/resources/mapper/workflow/WorkflowScopeMapper.xml) | `openTasks`、`expertModules` |
| [WorkflowUserSnapshotMapper](../services/workflow-service/src/main/resources/mapper/workflow/WorkflowUserSnapshotMapper.xml) | `upsert` |

## 豁免文件

以下文件仍参与注释外内容一致性核对。豁免只表示没有独立复杂业务规则，并不取消以后的按需字段说明。

### Java

| 文件 | 豁免理由 |
| --- | --- |
| [platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/AccessDecision.java](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/AccessDecision.java) | 简单响应或统计数据载体，无额外业务边界 |
| [platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApiResponse.java](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApiResponse.java) | 简单响应或统计数据载体，无额外业务边界 |
| [platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/IamOverviewStats.java](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/IamOverviewStats.java) | 简单响应或统计数据载体，无额外业务边界 |
| [platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/InternalError.java](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/InternalError.java) | 简单响应或统计数据载体，无额外业务边界 |
| [platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRecord.java](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRecord.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/entity/OutboxEntry.java](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/entity/OutboxEntry.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [platform/config-server/src/main/java/com/amatrix/sic/config/ConfigServerApplication.java](../platform/config-server/src/main/java/com/amatrix/sic/config/ConfigServerApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [platform/discovery-server/src/main/java/com/amatrix/sic/discovery/DiscoveryServerApplication.java](../platform/discovery-server/src/main/java/com/amatrix/sic/discovery/DiscoveryServerApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DeadLetterReplayProperties.java](../platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DeadLetterReplayProperties.java) | 离线迁移、运维或资产导出工具，本轮业务核心范围外 |
| [platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DeadLetterReplayService.java](../platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DeadLetterReplayService.java) | 离线迁移、运维或资产导出工具，本轮业务核心范围外 |
| [platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DlqRecoveryToolApplication.java](../platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DlqRecoveryToolApplication.java) | 离线迁移、运维或资产导出工具，本轮业务核心范围外 |
| [platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java](../platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java) | 离线迁移、运维或资产导出工具，本轮业务核心范围外 |
| [services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayServiceApplication.java](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayServiceApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/IamServiceApplication.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamServiceApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/entity/AdminUserRow.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/AdminUserRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/entity/IamCounters.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/IamCounters.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/entity/IdentityRow.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/IdentityRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/entity/SummaryRow.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/SummaryRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/entity/UserRoleRow.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/UserRoleRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/iam-service/src/main/java/com/amatrix/sic/iam/entity/UserVersionRow.java](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/UserVersionRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/material-service/src/main/java/com/amatrix/sic/material/MaterialServiceApplication.java](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialServiceApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [services/material-service/src/main/java/com/amatrix/sic/material/entity/MaterialTypeRow.java](../services/material-service/src/main/java/com/amatrix/sic/material/entity/MaterialTypeRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/material-service/src/main/java/com/amatrix/sic/material/entity/MaterialVersionRow.java](../services/material-service/src/main/java/com/amatrix/sic/material/entity/MaterialVersionRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/material-service/src/main/java/com/amatrix/sic/material/entity/ObjectDeletionJob.java](../services/material-service/src/main/java/com/amatrix/sic/material/entity/ObjectDeletionJob.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/material-service/src/main/java/com/amatrix/sic/material/entity/ReservationItemRow.java](../services/material-service/src/main/java/com/amatrix/sic/material/entity/ReservationItemRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/material-service/src/main/java/com/amatrix/sic/material/entity/ReservationRow.java](../services/material-service/src/main/java/com/amatrix/sic/material/entity/ReservationRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/notification-service/src/main/java/com/amatrix/sic/notification/NotificationServiceApplication.java](../services/notification-service/src/main/java/com/amatrix/sic/notification/NotificationServiceApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [services/notification-service/src/main/java/com/amatrix/sic/notification/entity/PendingNotification.java](../services/notification-service/src/main/java/com/amatrix/sic/notification/entity/PendingNotification.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/project-service/src/main/java/com/amatrix/sic/project/ProjectServiceApplication.java](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectServiceApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [services/project-service/src/main/java/com/amatrix/sic/project/dto/ProjectDtos.java](../services/project-service/src/main/java/com/amatrix/sic/project/dto/ProjectDtos.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/project-service/src/main/java/com/amatrix/sic/project/entity/ApplicationSagaRow.java](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ApplicationSagaRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectGrantScopeRow.java](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectGrantScopeRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectRecord.java](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectRecord.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectScopeRow.java](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectScopeRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/project-service/src/main/java/com/amatrix/sic/project/entity/UserSnapshotRow.java](../services/project-service/src/main/java/com/amatrix/sic/project/entity/UserSnapshotRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowServiceApplication.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowServiceApplication.java) | 标准 Spring Boot 启动入口，无独立业务规则 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ApplicationDraftRow.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ApplicationDraftRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/BusinessTask.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/BusinessTask.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/DefinitionRow.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/DefinitionRow.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertAssignment.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertAssignment.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertBatch.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertBatch.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertGrant.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertGrant.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertGrantScope.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertGrantScope.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertModule.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertModule.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/LeaderTask.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/LeaderTask.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ModuleRecord.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ModuleRecord.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ModuleRound.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ModuleRound.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/NodeFormModule.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/NodeFormModule.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/NodeFormRecord.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/NodeFormRecord.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ReviewBatch.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ReviewBatch.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowDefinitionSource.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowDefinitionSource.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowSagaState.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowSagaState.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowTaskScope.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowTaskScope.java) | 持久化行类型或请求数据载体；关键字段另按契约语义补充 |
| [services/workflow-service/src/main/java/com/amatrix/sic/workflow/tools/seed/WorkflowSeedExporter.java](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/tools/seed/WorkflowSeedExporter.java) | 离线迁移、运维或资产导出工具，本轮业务核心范围外 |

### 前端

| 文件 | 豁免理由 |
| --- | --- |
| [App.vue](../../SicProjectIS_fronted/src/App.vue) | 仅挂载 RouterView 的根组件，业务入口由路由和 AppShell 说明 |
| [components/HelloWorld.vue](../../SicProjectIS_fronted/src/components/HelloWorld.vue) | Vite 示例组件，不参加业务流程 |
| [features/node-forms/acceptance/index.ts](../../SicProjectIS_fronted/src/features/node-forms/acceptance/index.ts) | 仅导出命名空间常量，表单规则在共享转换模块说明 |
| [features/node-forms/application/index.ts](../../SicProjectIS_fronted/src/features/node-forms/application/index.ts) | 仅导出命名空间常量，表单规则在共享转换模块说明 |
| [features/node-forms/contract/index.ts](../../SicProjectIS_fronted/src/features/node-forms/contract/index.ts) | 仅导出命名空间常量，表单规则在共享转换模块说明 |
| [types/bpmn-js.d.ts](../../SicProjectIS_fronted/src/types/bpmn-js.d.ts) | 第三方模块声明适配，无业务规则 |

## 范围外文件

测试源码、target 生成代码、依赖、历史迁移 SQL、完整建表及种子 SQL、BPMN 资产、脚本和 CSS 不因本轮注释变更修改执行内容。生成及历史 SQL 不通过追加注释改变摘要；接口、绑定参数与动态 SQL 条件保持原样。

