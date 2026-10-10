# SicProjectIS_cloud 类功能索引

调研日期：2026-10-10。整体架构、数据归属和业务链路见 [项目结构总览](project-structure-overview.md)。

本索引逐项覆盖当前 164 个主 Java 源码文件、其中声明的命名内部/局部类型、44 个手写 Java 测试文件，以及启动/验收 Python 类。接口、注解、record 也列入；不包含 target 生成代码、匿名对象或依赖库内部类。源码链接均相对本文件。

“关键公开方法”来自源码声明，包含同文件内部类型的公开方法，不展开构造器和 record 自动访问器；— 表示未列出显式 public/protected 业务方法。Controller/Feign/Bean 的包内方法按职责描述阅读；内部记录类型只承载数据，并非独立部署服务。

## platform/cloud-contracts

主包：`com.amatrix.sic.contracts`。主源码 18 个，手写 Java 测试 0 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [AccessDecision](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/AccessDecision.java) | 单项访问判定：是否允许、项目版本、原因码；allow/deny 构造结果。 | `allow`、`deny` |
| [ActorContext](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ActorContext.java) | 不可变操作人上下文：用户、部门、角色、权限、Token ID、授权版本；hasRole/hasPermission 判断能力。 | `hasRole`、`hasPermission` |
| [ApiResponse](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApiResponse.java) | 统一 HTTP 响应 success/data/message；ok 与 error 构造成功/失败包装。 | `ok`、`error` |
| [ApplicationSagaCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApplicationSagaCommand.java) | Project 发给 Workflow 的申请创建命令：sagaId、项目及申请人、项目金额日期、申请标题与正文摘要。 | — |
| [ApplicationSagaResult](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApplicationSagaResult.java) | 申请 Saga 结果：Saga/项目/模块 ID、初始迁移及状态。 | — |
| [DocumentGenerationCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/DocumentGenerationCommand.java) | 工作流生成单据命令：幂等 ID、模块状态、文档/模板/输出材料类型、快照配置及快照内容。 | — |
| [EventEnvelope](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/EventEnvelope.java) | 统一事件信封：事件 ID/版本/时间、聚合、关联/因果 ID、操作人及 payload；create 自动生成事件标识。 | `create` |
| [ExpertProjectGrantCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ExpertProjectGrantCommand.java) | Workflow 同步/撤销专家项目授权的命令，携带项目、专家、模块类型、轮次、任务节点和授权人。 | — |
| [IamOverviewStats](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/IamOverviewStats.java) | 管理员概览用的用户、角色、权限统计及角色人数分布。 | — |
| [InternalError](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/InternalError.java) | 内部错误数据约定：code/message/retryable/correlationId；当前主源码未见业务引用。 | — |
| [MaterialReservationCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/MaterialReservationCommand.java) | 材料预留命令：commandId、操作人、项目、模块、expectedSeq、版本列表、必需材料类型。 | — |
| [MaterialReservationResult](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/MaterialReservationResult.java) | 材料预留结果：预留 ID、状态、过期时间、版本列表和版本到类型映射。 | — |
| [ProjectAccessBatchRequest](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ProjectAccessBatchRequest.java) | 批量访问判定请求：ActorContext 与多个 ProjectOperationTarget。 | — |
| [ProjectAccessResult](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ProjectAccessResult.java) | 单个批量判定结果，附 decisionId、允许/原因、版本、负责人及部门上下文。 | — |
| [ProjectOperationTarget](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ProjectOperationTarget.java) | 待判定项目操作及可选模块/轮次/任务节点范围。 | — |
| [StateRecordContract](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/StateRecordContract.java) | 状态历史记录对外结构：序号、轮次、事件、前后状态/节点、结果、摘要和时间。 | — |
| [StateTransitionContract](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/StateTransitionContract.java) | 一次迁移响应：状态记录、当前节点/状态、是否结束。 | — |
| [UserSummary](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/UserSummary.java) | 跨服务用户摘要：姓名、部门、角色、启用状态及 authVersion。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [IamOverviewStats.CountItem](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/IamOverviewStats.java) | 角色/统计项的 key、label、count。 |

## platform/cloud-security

主包：`com.amatrix.sic.security`。主源码 15 个，手写 Java 测试 4 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [ActorContextFactory](../platform/cloud-security/src/main/java/com/amatrix/sic/security/ActorContextFactory.java) | 将 JWT claims 转为 ActorContext；兼容 authVersion 与旧 ver，规范化角色/权限和部门字段。 | `from` |
| [ActorContextResolver](../platform/cloud-security/src/main/java/com/amatrix/sic/security/ActorContextResolver.java) | 从方法安全 Authentication 解析 JWT 操作人；非 JWT 或格式错误返回 null，供权限注解的 SpEL 使用。 | `from` |
| [ApiExceptionHandler](../platform/cloud-security/src/main/java/com/amatrix/sic/security/ApiExceptionHandler.java) | Servlet 全局异常转换：业务 HTTP 状态、权限拒绝、参数绑定错误及未预期异常，统一 ApiResponse。 | — |
| [AudienceValidator](../platform/cloud-security/src/main/java/com/amatrix/sic/security/AudienceValidator.java) | 检查 JWT audience 是否包含所需值，缺失则给出 invalid_token。 | `validate` |
| [CanAccessModule](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanAccessModule.java) | 方法权限元注解：调用名为 projectAccessPolicy 的 Bean 的 canAccessModule，验证模块读取。 | — |
| [CanAccessProject](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanAccessProject.java) | 方法权限元注解：调用 projectAccessPolicy.canAccess 验证项目读取。 | — |
| [CanModifyProject](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanModifyProject.java) | 方法权限元注解：调用 projectAccessPolicy，操作类型 WRITE_WORKFLOW_DATA。 | — |
| [CanOperateModule](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanOperateModule.java) | 方法权限元注解：调用 workflowOperationPolicy.canOperate，验证当前模块任务可操作。 | — |
| [CanReadNodeForm](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CanReadNodeForm.java) | 方法权限元注解：按 projectId/moduleInstanceId 调用 projectAccessPolicy.canAccessForm。 | — |
| [CloudResourceServerAutoConfiguration](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CloudResourceServerAutoConfiguration.java) | Servlet 安全自动装配：JWT decoder/converter、方法安全、401/403 处理、异常处理、Actor 解析、请求上下文 Filter；内部接口要求 SCOPE_internal。 | — |
| [CloudSecurityProperties](../platform/cloud-security/src/main/java/com/amatrix/sic/security/CloudSecurityProperties.java) | sic.security 配置：issuer、audience、jwkSetUri；对 issuer/audience 提供默认值。 | — |
| [FeignTracePropagationAutoConfiguration](../platform/cloud-security/src/main/java/com/amatrix/sic/security/FeignTracePropagationAutoConfiguration.java) | 装配 Feign RequestInterceptor，将已规范化的 Trace/Saga/Event 标识从请求属性或 MDC 转发。 | — |
| [JsonSecurityHandlers](../platform/cloud-security/src/main/java/com/amatrix/sic/security/JsonSecurityHandlers.java) | Spring Security AuthenticationEntryPoint/AccessDeniedHandler：以 JSON 返回 401、403。 | `commence`、`handle` |
| [JwtAuthoritiesConverter](../platform/cloud-security/src/main/java/com/amatrix/sic/security/JwtAuthoritiesConverter.java) | 将 JWT roles、permissions、scope 转为 ROLE_、PERM_、SCOPE_ 权限字符串。 | `convert` |
| [RequestLoggingContextFilter](../platform/cloud-security/src/main/java/com/amatrix/sic/security/RequestLoggingContextFilter.java) | 为 Servlet 请求建立 Trace/Saga/Event/User MDC 与请求属性，回传 Trace Header，并在结束时恢复原 MDC。 | `doFilterInternal` |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [ActorContextResolverTest](../platform/cloud-security/src/test/java/com/amatrix/sic/security/ActorContextResolverTest.java) | 验证 JWT 上下文提取、非 JWT/错误主体拒绝和旧 ver 兼容。 |
| [CloudResourceServerSecurityTest](../platform/cloud-security/src/test/java/com/amatrix/sic/security/CloudResourceServerSecurityTest.java) | 验证 Bearer/API 鉴权、internal scope 及监控路径限制。 |
| [RequestContextPropagationTest](../platform/cloud-security/src/test/java/com/amatrix/sic/security/RequestContextPropagationTest.java) | 验证标识规范化、MDC 恢复和 Feign 转发边界。 |
| [WorkflowPermissionMetaAnnotationTest](../platform/cloud-security/src/test/java/com/amatrix/sic/security/WorkflowPermissionMetaAnnotationTest.java) | 验证工作流读取注解引用统一 projectAccessPolicy Bean。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [CloudResourceServerSecurityTest.TestApplication](../platform/cloud-security/src/test/java/com/amatrix/sic/security/CloudResourceServerSecurityTest.java) | 测试专用 Spring Boot 应用，载入公共资源服务器安全配置及测试端点。 |
| [CloudResourceServerSecurityTest.TestEndpoints](../platform/cloud-security/src/test/java/com/amatrix/sic/security/CloudResourceServerSecurityTest.java) | 提供 API、内部和监控测试端点，验证公共安全链路径规则。 |

## platform/cloud-messaging

主包：`com.amatrix.sic.messaging`。主源码 8 个，手写 Java 测试 3 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [CloudMessagingAutoConfiguration](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/CloudMessagingAutoConfiguration.java) | 有 JdbcTemplate 时自动启用调度、Outbox/去重组件、业务 Topic Exchange、死信 Exchange/队列及消息指标。 | — |
| [ConsumedEventGuard](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/ConsumedEventGuard.java) | 在 consumed_event 插入 eventId+consumerName；重复键返回 false。应和业务处理同事务，失败回滚后可重试。 | `begin` |
| [MessagingMetrics](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/MessagingMetrics.java) | 注册各服务 Outbox 待发、失败、最老年龄以及公共死信队列积压 Gauge。 | — |
| [OutboxProperties](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxProperties.java) | sic.messaging 配置：交换机、批量、调度、发布开关、确认超时、领取租约、最大重试间隔。 | — |
| [OutboxPublisher](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxPublisher.java) | 定时领取待发事件，使用 RabbitMQ publisher confirm 后标记成功；失败指数退避，中断恢复线程标识。 | `publishPending` |
| [OutboxRecord](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRecord.java) | 已领取 Outbox 记录的数据投影：事件 ID/类型、routingKey、JSON、创建时间和尝试次数。 | — |
| [OutboxRepository](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRepository.java) | 写 Outbox，按条件更新领取租约，并记录发布成功/失败、下次尝试、错误和锁状态。 | `insert`、`claimPending`、`markPublished`、`markFailed` |
| [OutboxService](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxService.java) | 序列化 EventEnvelope 并在事务中写 Outbox，连接本地业务事务与后续消息发布。 | `enqueue` |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [CloudMessagingAutoConfigurationTest](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/CloudMessagingAutoConfigurationTest.java) | 验证在实际 Spring Context 中晚于 JdbcTemplate 完成消息自动装配。 |
| [ConsumedEventGuardTest](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/ConsumedEventGuardTest.java) | 验证相同消费者去重，以及业务失败时去重记录回滚。 |
| [OutboxPublisherTest](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/OutboxPublisherTest.java) | 验证只有 broker 正确认后标记成功，拒绝时安排重试。 |

## platform/discovery-server

主包：`com.amatrix.sic.discovery`。主源码 1 个，手写 Java 测试 0 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [DiscoveryServerApplication](../platform/discovery-server/src/main/java/com/amatrix/sic/discovery/DiscoveryServerApplication.java) | Spring Boot/Eureka Server 启动入口；注册中心配置在本模块 application.yaml。 | `main` |

## platform/config-server

主包：`com.amatrix.sic.config`。主源码 1 个，手写 Java 测试 0 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [ConfigServerApplication](../platform/config-server/src/main/java/com/amatrix/sic/config/ConfigServerApplication.java) | Spring Boot/Config Server 启动入口；native 配置从 classpath:/config 提供。 | `main` |

## services/gateway-service

主包：`com.amatrix.sic.gateway`。主源码 11 个，手写 Java 测试 5 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [GatewayErrorWriter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayErrorWriter.java) | 包内工具，统一写 JSON 错误、状态和 Trace Header，供限流、安全和异常处理复用。 | — |
| [GatewayJsonErrorHandler](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayJsonErrorHandler.java) | WebFlux 网关全局异常处理，保留已有 HTTP 状态，超时映射 503，输出统一错误包装。 | `handle` |
| [GatewayRateLimitProperties](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayRateLimitProperties.java) | 网关限流开关和每秒请求数，非法/未设置数量默认 40。 | — |
| [GatewayRateLimitWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayRateLimitWebFilter.java) | 按来源 IP 和秒窗口使用 Redis INCR 限流，超额写统一 429；监控路径跳过，计数 Redis 失败放行。 | `filter` |
| [GatewayRequestLoggingFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayRequestLoggingFilter.java) | GlobalFilter 记录 trace/saga/event/user、HTTP 路径、状态和耗时；不记录原始 query 或凭据。 | `filter`、`getOrder` |
| [GatewaySecurityConfig](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewaySecurityConfig.java) | WebFlux 安全链及 reactive JWT decoder；公开登录注册和指定监控路径，拒绝内部路径，API 使用会话有效性判定。 | — |
| [GatewaySecurityProperties](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewaySecurityProperties.java) | 网关 sic.security 的 issuer/audience/jwkSetUri 配置。 | — |
| [GatewayServiceApplication](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayServiceApplication.java) | 响应式 Gateway 启动入口，扫描配置属性。 | `main` |
| [SessionValidityAuthorizationManager](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/SessionValidityAuthorizationManager.java) | 核验 JWT 必需会话字段、Redis jti 注销键和用户授权版本；缺失、不一致或 Redis 错误都拒绝。 | `authorize` |
| [SseTokenRelayWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/SseTokenRelayWebFilter.java) | 仅处理 /api/sse/subscribe，把 query token 转 Bearer Header 并移除 query token；已有 Header 优先。 | `filter` |
| [TraceIdWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/TraceIdWebFilter.java) | 在安全链可能提前返回之前规范化/生成 X-Trace-Id，同时写入下游请求和响应。 | `filter`、`getOrder` |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [GatewayErrorAndRateLimitTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/GatewayErrorAndRateLimitTest.java) | 验证 404/503/429 统一包装、限流短路和 Redis 计数故障放行。 |
| [GatewayPayloadPassThroughTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/GatewayPayloadPassThroughTest.java) | 验证 multipart 内容及流式下载分块/文件名 Header 原样透传。 |
| [GatewaySecurityConfigTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/GatewaySecurityConfigTest.java) | 验证匿名/无效会话、登录放行、internal 拒绝及监控边界。 |
| [SessionValidityAuthorizationManagerTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/SessionValidityAuthorizationManagerTest.java) | 验证登记且当前版本会话、旧 ver、注销/过时、缺失/损坏/不可用 Redis 及缺少 JWT 字段。 |
| [SseTokenRelayWebFilterTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/SseTokenRelayWebFilterTest.java) | 验证 SSE token 转 Header、移除 query token 和其他请求保持原状。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [GatewaySecurityConfigTest.TestEndpoints](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/GatewaySecurityConfigTest.java) | 响应式测试端点，支撑网关 API、认证和内部路径验证。 |

## services/iam-service

主包：`com.amatrix.sic.iam`。主源码 18 个，手写 Java 测试 4 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [AuthApplicationService](../services/iam-service/src/main/java/com/amatrix/sic/iam/AuthApplicationService.java) | 规范化用户名、验证 BCrypt 密码与启用状态、注册用户、读取当前用户、签发登录响应、维护 Redis 授权版本和注销黑名单。 | `login`、`register`、`current`、`logout` |
| [AuthController](../services/iam-service/src/main/java/com/amatrix/sic/iam/AuthController.java) | /api/auth 的 login/register/me/logout HTTP 入口，提取 JWT 用户/jti/过期时间并委派业务服务。 | — |
| [BootstrapAdminInitializer](../services/iam-service/src/main/java/com/amatrix/sic/iam/BootstrapAdminInitializer.java) | 启动时按配置创建不存在的管理员并赋 SYSTEM_ADMIN；缺少配置、密码短于 10 或已有同名用户时跳过。 | `run` |
| [BootstrapAdminProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/BootstrapAdminProperties.java) | 初始管理员 username/password/realName 配置。 | — |
| [ExpertQualificationController](../services/iam-service/src/main/java/com/amatrix/sic/iam/ExpertQualificationController.java) | 个人专家资格提交/查询及管理员资格列表、部门审核、科技处审核 API。 | — |
| [ExpertQualificationService](../services/iam-service/src/main/java/com/amatrix/sic/iam/ExpertQualificationService.java) | 专家资格分阶段申请审核，校验重复/有效申请及部门范围；科技处通过后赋 EXPERT、更新授权版本及发布资格/用户变更事件。 | `submit`、`mine`、`query`、`departmentReview`、`scienceReview` |
| [IamAdminController](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamAdminController.java) | 管理员用户、角色权限、部门及部门成员 API，解析请求字段并调用 IamAdminService。 | — |
| [IamAdminService](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamAdminService.java) | 用户筛选/详情、改角色/启停、权限矩阵维护、部门 CRUD/成员管理；限制自禁用/移除自身管理员与越部门操作，更新授权版本并发 IAM 变更事件。 | `queryUsers`、`userDetail`、`updateRoles`、`updateStatus`、`permissions`、`permissionMatrix`、`updateRolePermissions`、`departments`、`createDepartment`、`updateDepartment`、`deleteDepartment`、`departmentMembers`、`candidates`、`upsertMember`、`updateMemberRoles`、`removeMember` |
| [IamDtos](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamDtos.java) | 身份接口 DTO 容器，定义登录/注册、当前用户、登录响应和含密码哈希的内部账号记录。 | `currentUser` |
| [IamJwtProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamJwtProperties.java) | sic.jwt 的 issuer、audience、有效分钟数、公私钥配置；有效期默认 120 分钟。 | — |
| [IamRepository](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamRepository.java) | JdbcTemplate 用户仓储：按用户名/ID 查询用户和部门、角色权限集合，插入用户和分配角色。 | `findByUsername`、`findById`、`insertUser`、`assignRole`、`roleCodes`、`permissionCodes` |
| [IamSecurityConfig](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamSecurityConfig.java) | IAM 专属 Servlet 安全链、BCrypt 编码器、本地公钥 JWT decoder；开放认证/JWK/服务 Token bootstrap，其他内部接口要求内部 scope。 | — |
| [IamServiceApplication](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamServiceApplication.java) | IAM Spring Boot 启动入口，扫描配置属性。 | `main` |
| [InternalIdentityController](../services/iam-service/src/main/java/com/amatrix/sic/iam/InternalIdentityController.java) | 内部服务 Token 兑换（恒定时间比较 secret）、用户摘要和管理员概览统计接口。 | — |
| [JwkController](../services/iam-service/src/main/java/com/amatrix/sic/iam/JwkController.java) | GET /.well-known/jwks.json，发布 RSA 公钥的 JWK 参数供服务与 Gateway 验签。 | — |
| [JwtTokenIssuer](../services/iam-service/src/main/java/com/amatrix/sic/iam/JwtTokenIssuer.java) | 签发用户及内部服务 RS256 JWT，设置角色、权限、scope、jti、authVersion/ver、用户/部门信息和有效期。 | `issue`、`issueServiceToken` |
| [RsaKeyProvider](../services/iam-service/src/main/java/com/amatrix/sic/iam/RsaKeyProvider.java) | 加载配置的 PKCS8/X509 RSA 密钥或生成临时 3072 位密钥；校验成对配置、匹配及至少 2048 位，生成公钥指纹 kid。 | `publicKey`、`privateKey`、`keyId` |
| [ServiceTokenProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/ServiceTokenProperties.java) | sic.service-token 的 bootstrapSecret 配置。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [IamDtos.LoginRequest](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamDtos.java) | 用户名与密码。 |
| [IamDtos.RegisterRequest](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamDtos.java) | 账号、密码、真实姓名、部门和联系方式。 |
| [IamDtos.CurrentUser](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamDtos.java) | 对外用户身份及角色/权限信息，不含密码哈希。 |
| [IamDtos.LoginResponse](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamDtos.java) | token、expiresAt、user。 |
| [IamDtos.UserAccount](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamDtos.java) | 仓储内部用户记录，包括 passwordHash、enabled、authVersion；currentUser 转对外结构。 |
| [JwtTokenIssuer.IssuedToken](../services/iam-service/src/main/java/com/amatrix/sic/iam/JwtTokenIssuer.java) | 签发结果 token、expiresAt、tokenId。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [AuthApplicationServiceTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/AuthApplicationServiceTest.java) | 验证登录写 Redis 授权版本及注销黑名单 TTL。 |
| [AuthContractBase](../services/iam-service/src/test/java/com/amatrix/sic/iam/contract/AuthContractBase.java) | IAM 契约测试基类：构造身份/管理/资格控制器和 mock 响应，供生成的契约测试复用。 |
| [IamSecurityConfigTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/IamSecurityConfigTest.java) | 验证 IAM Bearer 安全、唯一公开 bootstrap 内部入口及 JWK/监控路径。 |
| [JwtTokenIssuerTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/JwtTokenIssuerTest.java) | 验证 RS256 用户/服务 Token 字段、scope、kid 及部分/不匹配密钥启动失败。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [IamSecurityConfigTest.TestApplication](../services/iam-service/src/test/java/com/amatrix/sic/iam/IamSecurityConfigTest.java) | 测试专用 IAM 安全应用上下文。 |
| [IamSecurityConfigTest.TestEndpoints](../services/iam-service/src/test/java/com/amatrix/sic/iam/IamSecurityConfigTest.java) | 身份测试端点，支撑 API、bootstrap、JWK 及监控边界验证。 |

## services/project-service

主包：`com.amatrix.sic.project`。主源码 23 个，手写 Java 测试 6 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/IamDirectoryClient.java) | Feign 调用 IAM 内部用户摘要及管理员概览统计。 | — |
| [InternalProjectController](../services/project-service/src/main/java/com/amatrix/sic/project/InternalProjectController.java) | 内部单项/批量访问判定，以及 Workflow 专家授权同步和撤销接口。 | — |
| [InternalTokenProvider](../services/project-service/src/main/java/com/amatrix/sic/project/InternalTokenProvider.java) | 同步缓存内部 Token，在过期前 60 秒刷新，返回 Bearer Header。 | `authorization` |
| [ProjectAccessPolicy](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAccessPolicy.java) | 项目访问决策中心；批量加载项目、成员及有效授权，区分读取/材料/业务数据/角色任务操作，返回原因码和上下文。 | `canAccess`、`decide`、`decideBatch` |
| [ProjectAdminController](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAdminController.java) | GET /api/admin/projects/authorizations，列出当前管理员可管理的项目授权。 | — |
| [ProjectApplicationSagaService](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectApplicationSagaService.java) | 编排临时项目与 Workflow APPLICATION 创建；近期请求 hash 去重、失败补偿、最终确认和定时确认恢复；写申请启动事件。 | `start`、`recoverIncompleteConfirmations` |
| [ProjectAuditController](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAuditController.java) | GET /api/admin/overview 与 /api/admin/audit-logs，提供管理概览和筛选日志。 | — |
| [ProjectAuditService](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAuditService.java) | 调用 IAM 聚合用户/角色统计，结合本地授权统计生成概览；按条件查询管理日志并执行概览/审计权限校验。 | `overview`、`logs` |
| [ProjectAuthorizationController](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAuthorizationController.java) | 项目授权详情、负责人更换、成员单条/批量变更、专家/财务/代录单条/批量授权及撤销 API。 | — |
| [ProjectAuthorizationService](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAuthorizationService.java) | 实现授权管理与角色/管理能力校验；维护项目版本、成员/授权关系、审计和 project.authorization；提供内部专家 grant 幂等同步/撤销。 | `detail`、`changeLeader`、`upsertMember`、`upsertMembers`、`removeMember`、`assignGrant`、`assignGrants`、`syncExpertGrant`、`revokeExpertGrant`、`revoke`、`revokeBatch`、`manageableProjects` |
| [ProjectController](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectController.java) | 项目列表查询和 POST /api/project-applications/start 入口，将 JWT 用户/部门及原授权 Header 交给查询/Saga 服务。 | — |
| [ProjectDtos](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectDtos.java) | 发起申请的请求/响应容器，响应包含项目 ID、模块 ID、StateTransitionContract。 | — |
| [ProjectExpertAccessEndedConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectExpertAccessEndedConsumer.java) | 消费专家访问结束事件，按项目/模块/轮次/节点撤销专家 grant；历史节点引用丢失时按事件中的专家身份回退限定范围。 | `consume` |
| [ProjectQueryService](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectQueryService.java) | 以一个 SQL 按全局/部门/负责人/成员/授权过滤 ACTIVE 项目；转换角色与数字类型。 | `accessibleProjects`、`roles`、`number` |
| [ProjectSagaMetrics](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectSagaMetrics.java) | 统计未结束 Saga、失败/补偿 Saga 及最老未完成 Saga 年龄。 | — |
| [ProjectServiceApplication](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectServiceApplication.java) | Project 启动入口，启用 Feign 和配置属性扫描。 | `main` |
| [ProjectSnapshotMessagingConfiguration](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectSnapshotMessagingConfiguration.java) | 声明 IAM 快照、工作流生命周期、专家访问结束的持久队列及 Topic 绑定和死信配置。 | — |
| [ProjectUserDirectory](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectUserDirectory.java) | 本地 user_snapshot 目录查询；require 在缺少快照时从 IAM 拉取存在用户，refresh 强制拉取、upsert 更新，allEnabled 提供启用候选用户；require 本身不拒绝禁用用户。 | `require`、`refresh`、`find`、`allEnabled`、`upsert` |
| [ProjectUserSnapshotConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectUserSnapshotConsumer.java) | 去重消费 iam.changed，按用户/角色/部门变更刷新 user_snapshot，并保存 IAM 相关管理审计信息。 | `synchronize` |
| [ProjectWorkflowLifecycleConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectWorkflowLifecycleConsumer.java) | 去重消费工作流项目生命周期事件，更新 Project 的 business_status 及版本。 | `consume` |
| [ServiceClientProperties](../services/project-service/src/main/java/com/amatrix/sic/project/ServiceClientProperties.java) | Project 的 sic.service-client.bootstrapSecret 配置。 | — |
| [ServiceTokenClient](../services/project-service/src/main/java/com/amatrix/sic/project/ServiceTokenClient.java) | Feign 通过 bootstrap secret 向 IAM 换取 project-service 内部 Token。 | — |
| [WorkflowSagaClient](../services/project-service/src/main/java/com/amatrix/sic/project/WorkflowSagaClient.java) | Feign 调用 Workflow 内部申请 Saga 创建、状态查询、补偿删除；创建时转发用户授权 Header。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [ProjectAccessPolicy.ProjectScope](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAccessPolicy.java) | 用于访问决策的项目版本、负责人、部门。 |
| [ProjectAccessPolicy.Grant](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAccessPolicy.java) | 授权角色、模块、轮次和任务节点范围。 |
| [ProjectAccessPolicy.Decision](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAccessPolicy.java) | 内部 allowed/reason 决策结果，提供 allow/deny。 |
| [ProjectApplicationSagaService.ExistingSaga](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectApplicationSagaService.java) | 去重/恢复时读到的 Saga ID、项目、操作人、状态、响应 JSON。 |
| [ProjectAuthorizationService.ProjectRecord](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAuthorizationService.java) | 授权页面和能力检查所需项目主数据投影。 |
| [ProjectDtos.StartProjectApplicationRequest](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectDtos.java) | 项目编码/名称/类型/级别/金额/日期及申请标题/限额/摘要。 |
| [ProjectDtos.StartProjectApplicationResponse](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectDtos.java) | projectId、moduleInstanceId、初始 transition。 |
| [ServiceTokenClient.TokenResponse](../services/project-service/src/main/java/com/amatrix/sic/project/ServiceTokenClient.java) | Project 接收到的内部 Token、过期时间、Token ID。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [ProjectContractBase](../services/project-service/src/test/java/com/amatrix/sic/project/contract/ProjectContractBase.java) | Project 契约测试基类：构造项目/授权/概览/审计控制器及 mock 响应。 |
| [MethodSecurityProxyTest](../services/project-service/src/test/java/com/amatrix/sic/project/MethodSecurityProxyTest.java) | 验证通过 Spring Bean 代理调用时权限元注解实际生效。 |
| [ProjectAccessPolicyTest](../services/project-service/src/test/java/com/amatrix/sic/project/ProjectAccessPolicyTest.java) | 验证全局/部门/负责人/成员/grant 矩阵和无效/非 ACTIVE 项目拒绝。 |
| [ProjectApplicationSagaServiceTest](../services/project-service/src/test/java/com/amatrix/sic/project/ProjectApplicationSagaServiceTest.java) | 验证近期请求去重、失败补偿及最终确认定时恢复。 |
| [ProjectExpertAccessEndedConsumerTest](../services/project-service/src/test/java/com/amatrix/sic/project/ProjectExpertAccessEndedConsumerTest.java) | 验证历史节点引用不可解析时以批次专家身份限定撤销范围。 |
| [ProjectQueryServiceTest](../services/project-service/src/test/java/com/amatrix/sic/project/ProjectQueryServiceTest.java) | 验证超过 100 个项目的单 SQL 过滤和部门/全局范围。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [MethodSecurityProxyTest.Config](../services/project-service/src/test/java/com/amatrix/sic/project/MethodSecurityProxyTest.java) | 装配测试 Actor 解析、权限 Policy 与受保护 Bean 的 Spring 配置。 |
| [MethodSecurityProxyTest.SecuredProbe](../services/project-service/src/test/java/com/amatrix/sic/project/MethodSecurityProxyTest.java) | 带项目访问权限元注解的测试业务方法，验证 Spring 代理拦截。 |
| [ProjectQueryServiceTest.CountingDataSource](../services/project-service/src/test/java/com/amatrix/sic/project/ProjectQueryServiceTest.java) | 包装测试 DataSource，计数 SQL prepare 调用以验证单语句查询。 |

## services/workflow-service

主包：`com.amatrix.sic.workflow`。主源码 38 个，手写 Java 测试 9 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [ApplicationSagaWorkflowService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ApplicationSagaWorkflowService.java) | 按 sagaId 幂等创建申请实例、项目快照、申请正文、初始任务/历史/事件；查询结果；仅清理指定 Saga 的关联流程数据，补偿可重复。 | `create`、`find`、`compensate` |
| [ExpertReviewCompletedConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewCompletedConsumer.java) | 去重并校验 EXPERT_REVIEW_COMPLETED 事件，调用运行器尝试自动迁移对应批次。 | `consume` |
| [ExpertReviewController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewController.java) | 专家评审批次创建/详情、专家分配、评分提交和分配删除 API；保留 POST remove 兼容入口。 | — |
| [ExpertReviewDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewDtos.java) | 评审批次创建、专家分配、评分提交和分项评分请求容器。 | — |
| [ExpertReviewService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewService.java) | 当前节点/轮次内创建批次、核验 IAM 专家、分配/移除并同步 Project grant；本人提交评分、计算有效均分/阈值结果并发布完成事件。 | `create`、`assign`、`submit`、`remove`、`detail` |
| [ExpertReviewViewRepository](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewViewRepository.java) | 读取评审批次、专家分配及分项评分，形成批次详情和模块级实时评审数据投影。 | `batchDetail`、`moduleReviews` |
| [FlowableBpmnDefinitionParser](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/FlowableBpmnDefinitionParser.java) | 禁用外部实体/DTD 后用 Flowable 转换器解析 BPMN 及 rm 辅助标签，构建流程、节点、连线、材料、单据模型并补齐状态。 | `parse` |
| [InternalApplicationSagaController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/InternalApplicationSagaController.java) | 内部申请 Saga create/status/compensate HTTP 入口；Saga 不存在返回 404。 | — |
| [MaterialReservationClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/MaterialReservationClient.java) | Feign 调 Material 预留及释放接口；成功确认通过事件完成。 | — |
| [ModuleBusinessDataController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ModuleBusinessDataController.java) | GET /api/module-instances/{moduleInstanceId}/business-data，返回模块聚合业务数据。 | — |
| [ModuleSuccessorPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ModuleSuccessorPolicy.java) | 按模块及终态映射下一模块/项目阶段：批准申请→合同，批准合同→结题，接受结题→项目完成；其他终态无后继。 | `completion`、`hasSuccessor` |
| [NodeFormController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/NodeFormController.java) | 节点表单定义、读取、单例保存、历史记录新增/修改/删除 API，从请求提取模块和用户上下文。 | — |
| [NodeFormService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/NodeFormService.java) | 维护 43 个节点表单目录；按节点/状态匹配，区分三种写模式；校验当前节点和轮次，存储 JSON，聚合业务草稿与实时专家评审视图。 | `definitions`、`forNode`、`get`、`save`、`createRecord`、`updateRecord`、`deleteRecord`、`businessData` |
| [NoticeController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/NoticeController.java) | /api/notices 列表、详情、创建、更新 API。 | — |
| [NoticeService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/NoticeService.java) | 公告字段校验及 CRUD（无删除入口），按全部/部门/用户范围控制可见性；发布修改要求科技处或系统管理员。 | `list`、`get`、`create`、`update` |
| [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ProjectAccessClient.java) | Feign 调 Project 单项/批量访问判定、专家授权同步与撤销接口。 | — |
| [StructuredBusinessController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/StructuredBusinessController.java) | 项目下 applications/contracts/acceptances 读写、achievements 和 acceptance-financial-settlements 查询/新增 API。 | — |
| [StructuredBusinessService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/StructuredBusinessService.java) | 申请/合同/结题结构化正文、成果和财务结算持久化；校验当前业务节点操作人及关联模块/验收/状态记录，计算结余和执行率。 | `application`、`saveApplication`、`contract`、`saveContract`、`acceptance`、`saveAcceptance`、`achievements`、`addAchievement`、`settlements`、`addSettlement` |
| [WorkflowAssetInitializer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowAssetInitializer.java) | 启动安装三个内置 BPMN，刷新相同版本及节点/迁移配置；存在更高发布版时内置版保持 INACTIVE。 | `run` |
| [WorkflowBpmnParseResult](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowBpmnParseResult.java) | BPMN 解析结果容器：流程配置、节点列表和迁移列表；内嵌五种配置 record。 | — |
| [WorkflowClientProperties](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowClientProperties.java) | Workflow 的 sic.service-client.bootstrapSecret 配置。 | — |
| [WorkflowDefinitionCatalog](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionCatalog.java) | 运行器内部读取当前已发布定义或按 definitionId 解析模型，返回 ActiveDefinition。 | `active`、`model` |
| [WorkflowDefinitionController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionController.java) | 流程定义上传、资产列表/上传/发布、验证、发布、最新版本、详情、BPMN、节点及连线 API。 | — |
| [WorkflowDefinitionNavigator](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionNavigator.java) | 按开始事件/当前节点与 eventType 选取连线，按条件穿越网关，限制遍历次数，返回目标节点和完整迁移路径。 | `start`、`transition`、`outgoing`、`initialTransition` |
| [WorkflowDefinitionService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionService.java) | 定义生命周期管理与权限检查；上传 DRAFT、校验必需字段/重复节点/连线引用等，发布 ACTIVE 并停用同模块旧发布版本。 | `upload`、`listAssets`、`uploadAsset`、`publishAsset`、`validate`、`publish`、`list`、`latest`、`detail`、`bpmn`、`nodes`、`transitions` |
| [WorkflowDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDtos.java) | 流程定义上传、模块启动和状态迁移的请求 DTO 容器。 | — |
| [WorkflowInternalTokenProvider](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowInternalTokenProvider.java) | 缓存 workflow-service 内部 Token，提前 60 秒刷新并提供 Bearer Header。 | `authorization` |
| [WorkflowOperationPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowOperationPolicy.java) | 当前 OPEN 任务操作判定：指派人/候选角色/专家分配本地筛选，再批量向 Project 校验精确模块轮次节点范围；系统管理员有特殊放行。 | `canOperate`、`exactCanOperate`、`operableModuleIds` |
| [WorkflowProjectAccessPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowProjectAccessPolicy.java) | 以 projectAccessPolicy Bean 名实现项目/模块/节点表单读取；必要时根据当前专家分配同步缺失的 Project 授权。 | `canAccess`、`canAccessModule`、`canAccessForm` |
| [WorkflowProjectAuthorizationConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowProjectAuthorizationConsumer.java) | 消费项目授权事件；对 PROJECT_LEADER_CHANGED 更新项目负责人快照和当前未完成负责人任务。 | `consume` |
| [WorkflowProjectLifecycleConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowProjectLifecycleConsumer.java) | 消费生命周期事件，更新本地 project_snapshot.lifecycle_stage。 | `consume` |
| [WorkflowRuntimeController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowRuntimeController.java) | 模块启动、普通/legacy 状态迁移、runtime-view 与 workflow-workbench/items API。 | — |
| [WorkflowRuntimeService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowRuntimeService.java) | 自建工作流运行核心：实例/任务/状态历史、序号行锁、轮次、材料预留引用、运行视图、待办；专家完成自动迁移、成功后继模块及三类恢复任务。 | `start`、`transition`、`runtimeView`、`workbench`、`autoTransitionAfterExpertReviewComplete`、`recoverMissingSuccessors`、`reconcileProjectLeaderTasks`、`recoverEndedExpertAccess` |
| [WorkflowServiceApplication](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowServiceApplication.java) | Workflow 启动入口，启用 Feign 和配置属性扫描。 | `main` |
| [WorkflowServiceTokenClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowServiceTokenClient.java) | Feign 调 IAM 获取内部 Token 和用户摘要，用于内部调用与专家资格检查。 | — |
| [WorkflowSnapshotMessagingConfiguration](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowSnapshotMessagingConfiguration.java) | 声明 IAM 快照、项目生命周期、专家评审完成和项目授权队列，配置对应 routing key 与死信。 | — |
| [WorkflowTransitionActionService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowTransitionActionService.java) | 校验内置动作键，按节点完成/流程结束的单据配置生成幂等命令；发布文档生成和项目生命周期事件。 | `execute`、`enqueueLifecycle` |
| [WorkflowUserSnapshotConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowUserSnapshotConsumer.java) | 消费 IAM 变更，按用户/角色/部门事件从 IAM 拉取摘要并更新 Workflow 的 user_snapshot。 | `synchronize` |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [ExpertReviewDtos.CreateBatchRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewDtos.java) | 模块/节点、评审类型标题、人数、阈值、规则和去极值设置。 |
| [ExpertReviewDtos.AssignExpertRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewDtos.java) | 专家 ID、姓名、机构和职称快照。 |
| [ExpertReviewDtos.SubmitScoreRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewDtos.java) | 利益冲突、有效性、结论、意见和分项评分列表。 |
| [ExpertReviewDtos.ScoreItem](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewDtos.java) | 评分项目、权重、满分、得分和意见。 |
| [ExpertReviewService.Module](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewService.java) | 评审操作锁定的模块、定义、当前节点/状态/轮次。 |
| [ExpertReviewService.Batch](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewService.java) | 评审批次、模块、节点、轮次、阈值、去极值和状态。 |
| [ExpertReviewService.Assignment](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewService.java) | 专家分配 ID、批次、专家用户和提交状态。 |
| [ModuleSuccessorPolicy.Completion](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ModuleSuccessorPolicy.java) | 下个模块类型和项目生命周期阶段；hasSuccessor 判断是否自动创建后继。 |
| [NodeFormService.ModuleContext](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/NodeFormService.java) | 表单校验需要的项目、模块类型、节点、状态、轮次和结束标记。 |
| [StructuredBusinessService.ModuleRound](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/StructuredBusinessService.java) | 正文读取用的模块 ID 与当前轮次。 |
| [StructuredBusinessService.BusinessTask](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/StructuredBusinessService.java) | 业务数据写入匹配到的可操作模块与轮次。 |
| [WorkflowBpmnParseResult.ProcessConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowBpmnParseResult.java) | 流程 key/name/moduleType/version/status。 |
| [WorkflowBpmnParseResult.NodeConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowBpmnParseResult.java) | 节点 ID/名称/类型/状态/泳道、负责主体、候选角色、代录主体、材料要求和单据配置。 |
| [WorkflowBpmnParseResult.MaterialRequirementConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowBpmnParseResult.java) | 材料类型、时点、必需性、数量、用途、validatorKey、文件类型和大小配置。 |
| [WorkflowBpmnParseResult.DocumentConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowBpmnParseResult.java) | 单据类型、生成时点、模板、快照结构/视图、输出材料类型及启用/必需标记。 |
| [WorkflowBpmnParseResult.TransitionConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowBpmnParseResult.java) | 连线源/目标、事件/结果、条件类型/键/值/处理器/表达式及动作键列表。 |
| [WorkflowDefinitionCatalog.ActiveDefinition](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionCatalog.java) | 定义 ID 与解析后的 BPMN 模型。 |
| [WorkflowDefinitionNavigator.ResolvedTarget](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionNavigator.java) | 解析出的目标节点和穿越的迁移列表；initialTransition 取最初连线。 |
| [WorkflowDefinitionService.DefinitionRow](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionService.java) | 数据库定义 ID、代码、名称、版本、状态、发布标记及 BPMN XML。 |
| [WorkflowDefinitionService.Validation](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDefinitionService.java) | 校验错误/警告与角色/状态集合。 |
| [WorkflowDtos.UploadWorkflowDefinitionRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDtos.java) | bpmnXml 上传正文。 |
| [WorkflowDtos.StartModuleRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDtos.java) | 要启动的 moduleType。 |
| [WorkflowDtos.TransitionRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowDtos.java) | eventType、expectedSeq、result/remark、材料版本 ID、formCode 和 nodeFormData；当前运行器主要使用 nodeFormData。 |
| [WorkflowOperationPolicy.Task](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowOperationPolicy.java) | 批量任务权限判定所需任务/模块/项目/节点/指派/候选角色/轮次。 |
| [WorkflowRuntimeService.LeaderTask](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowRuntimeService.java) | 负责人任务修复方法中的局部 record：任务、项目、模块、轮次和节点。 |
| [WorkflowRuntimeService.ModuleRecord](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowRuntimeService.java) | 运行实例投影：项目、启动人、定义、节点、状态、序号、轮次、结束标志及时间。 |
| [WorkflowRuntimeService.ReviewBatch](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowRuntimeService.java) | 自动迁移与校验用评审批次/模块/节点/轮次/状态/创建人。 |
| [WorkflowServiceTokenClient.Token](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowServiceTokenClient.java) | Workflow 接收到的内部 Token、过期时间、Token ID。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [ApplicationSagaCompensationTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/ApplicationSagaCompensationTest.java) | 验证重复补偿及仅清理目标 Saga 关联数据。 |
| [WorkflowContractBase](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/contract/WorkflowContractBase.java) | Workflow 契约测试基类：定义、运行、表单、业务数据和评审接口的 mock 场景。 |
| [ModuleSuccessorPolicyTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/ModuleSuccessorPolicyTest.java) | 验证只有成功终态映射后继模块。 |
| [NodeFormBusinessDataTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/NodeFormBusinessDataTest.java) | 验证模块业务正文叠加实时专家分配数据。 |
| [NodeFormCatalogTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/NodeFormCatalogTest.java) | 验证完整前端表单目录及内置 BPMN 用户任务覆盖。 |
| [WorkflowDefinitionModelTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowDefinitionModelTest.java) | 验证三个内置资产解析、申请条件网关及合同材料要求。 |
| [WorkflowExpectedSequenceConcurrencyTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowExpectedSequenceConcurrencyTest.java) | 验证同 expectedSeq 仅一请求提交、Outbox 失败回滚/释放材料、后继唯一创建及缺失后继恢复。 |
| [WorkflowOperationPolicyTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowOperationPolicyTest.java) | 验证指派/候选角色/任务状态/管理员操作策略及 AOP 操作人上下文。 |
| [WorkflowTransitionActionServiceTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowTransitionActionServiceTest.java) | 验证节点/流程单据命令及生命周期事件输出。 |

## services/material-service

主包：`com.amatrix.sic.material`。主源码 20 个，手写 Java 测试 5 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [GeneratedDocumentEventConsumer](../services/material-service/src/main/java/com/amatrix/sic/material/GeneratedDocumentEventConsumer.java) | 校验并去重 DOCUMENT_GENERATION_REQUESTED，调用单据生成服务。 | `consume` |
| [GeneratedDocumentRenderer](../services/material-service/src/main/java/com/amatrix/sic/material/GeneratedDocumentRenderer.java) | 把命令和 JSON 快照渲染为确定性的一页 PDF，手工写 PDF 结构；非 ASCII 转问号，无业务模板引擎。 | `render` |
| [GeneratedDocumentService](../services/material-service/src/main/java/com/amatrix/sic/material/GeneratedDocumentService.java) | 按 commandId 去重生成 PDF、哈希与 MinIO 对象；创建/复用输出材料类型及材料，生成新 ACTIVE 版本并关联 generated_document。 | `generate` |
| [InternalMaterialReservationController](../services/material-service/src/main/java/com/amatrix/sic/material/InternalMaterialReservationController.java) | 内部材料预留、确认、释放接口。 | — |
| [MaterialAccessPolicy](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialAccessPolicy.java) | 将操作人和 MATERIAL_READ/WRITE/DELETE 等操作发给 Project，使用内部 Token，按响应判定权限。 | `canAccess` |
| [MaterialApplicationService](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialApplicationService.java) | 向 Project 校验读写权限；上传 MinIO 并事务保存版本/SHA-256/事件，失败尝试清理对象；列表/下载；禁止删除生成单据及已预留/确认版本，其他删除建立持久清理任务。 | `upload`、`persistUpload`、`list`、`download`、`delete` |
| [MaterialCleanupMetrics](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialCleanupMetrics.java) | 暴露 PENDING 对象删除任务数量及其中已失败过的任务数量；没有材料预留数量指标。 | — |
| [MaterialClientProperties](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialClientProperties.java) | Material 的 sic.service-client.bootstrapSecret 配置。 | — |
| [MaterialController](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialController.java) | 材料版本 multipart 上传、项目材料列表、版本流式下载和删除 API；下载用 UTF-8 Content-Disposition 文件名。 | — |
| [MaterialMessagingConfiguration](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialMessagingConfiguration.java) | 声明材料预留确认和单据生成队列、Topic 绑定及死信路由。 | — |
| [MaterialProjectAccessClient](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialProjectAccessClient.java) | Feign 调用 Project 的内部单项访问决策。 | — |
| [MaterialProperties](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialProperties.java) | sic.minio 的 endpoint/accessKey/secretKey/bucket 配置，bucket 默认 sic-materials。 | — |
| [MaterialReservationEventConsumer](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialReservationEventConsumer.java) | 去重消费材料确认命令，读取 reservationId 并确认预留。 | `consume` |
| [MaterialReservationService](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialReservationService.java) | 按 commandId 幂等预留版本，校验项目与必需类型；确认/释放/过期清理，支持对象未删除情况下的迟到确认。 | `reserve`、`confirm`、`release`、`cleanup` |
| [MaterialServiceApplication](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialServiceApplication.java) | Material 启动入口，启用 Feign 和配置属性扫描。 | `main` |
| [MaterialTokenClient](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialTokenClient.java) | Feign 向 IAM 换取 material-service 内部 Token。 | — |
| [MaterialTokenProvider](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialTokenProvider.java) | 缓存内部 Token，过期前 60 秒刷新，提供 Bearer Header。 | `authorization` |
| [MinioConfiguration](../services/material-service/src/main/java/com/amatrix/sic/material/MinioConfiguration.java) | 根据 MaterialProperties 创建 MinioClient Bean。 | — |
| [MinioObjectStorage](../services/material-service/src/main/java/com/amatrix/sic/material/MinioObjectStorage.java) | 初始化检查/创建 bucket，封装对象 put/get/remove 与存储异常。 | `ensureBucket`、`put`、`get`、`remove` |
| [ObjectDeletionService](../services/material-service/src/main/java/com/amatrix/sic/material/ObjectDeletionService.java) | 定时读取 PENDING 对象删除任务，删除 MinIO 对象后标记完成；异常保存错误、次数及指数退避时间，允许幂等重试。 | `deletePendingObjects` |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [MaterialApplicationService.Download](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialApplicationService.java) | 下载资源、原文件名、内容类型和大小。 |
| [MaterialApplicationService.TypeRecord](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialApplicationService.java) | 材料类型元数据投影。 |
| [MaterialApplicationService.VersionRecord](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialApplicationService.java) | 材料版本及所属项目/对象信息投影。 |
| [MaterialReservationService.Item](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialReservationService.java) | 材料版本 ID、所属项目和材料类型。 |
| [MaterialTokenClient.Token](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialTokenClient.java) | 内部 Token、有效期、Token ID。 |
| [ObjectDeletionService.Job](../services/material-service/src/main/java/com/amatrix/sic/material/ObjectDeletionService.java) | 对象删除任务 ID、objectKey、已尝试次数。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [MaterialContractBase](../services/material-service/src/test/java/com/amatrix/sic/material/contract/MaterialContractBase.java) | Material 契约测试基类：材料上传、列表、下载和删除的 mock 响应。 |
| [GeneratedDocumentRendererTest](../services/material-service/src/test/java/com/amatrix/sic/material/GeneratedDocumentRendererTest.java) | 验证同一快照得到确定性 PDF。 |
| [MaterialDeletionTransactionTest](../services/material-service/src/test/java/com/amatrix/sic/material/MaterialDeletionTransactionTest.java) | 验证 Outbox 失败回滚元数据且不删对象、成功事务创建持久清理任务。 |
| [MaterialReservationServiceTest](../services/material-service/src/test/java/com/amatrix/sic/material/MaterialReservationServiceTest.java) | 验证预留/确认幂等、缺少/已删除/跨项目材料拒绝、释放与迟到确认边界。 |
| [ObjectDeletionServiceTest](../services/material-service/src/test/java/com/amatrix/sic/material/ObjectDeletionServiceTest.java) | 验证对象清理完成及失败后幂等重试。 |

## services/notification-service

主包：`com.amatrix.sic.notification`。主源码 7 个，手写 Java 测试 3 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [NotificationEventConsumers](../services/notification-service/src/main/java/com/amatrix/sic/notification/NotificationEventConsumers.java) | store 将模块状态事件去重持久化并发 notification.live；live 以实例专属 consumerName 去重并触发 SSE 投递。 | `store`、`live` |
| [NotificationMessagingConfiguration](../services/notification-service/src/main/java/com/amatrix/sic/notification/NotificationMessagingConfiguration.java) | 持久化模块事件队列与每实例匿名 live 队列的声明、绑定及持久队列死信配置。 | — |
| [NotificationMetrics](../services/notification-service/src/main/java/com/amatrix/sic/notification/NotificationMetrics.java) | 暴露待投递通知数量与最老 QUEUED 通知年龄。 | — |
| [NotificationServiceApplication](../services/notification-service/src/main/java/com/amatrix/sic/notification/NotificationServiceApplication.java) | Notification Spring Boot 启动入口。 | `main` |
| [PendingNotificationDeliveryService](../services/notification-service/src/main/java/com/amatrix/sic/notification/PendingNotificationDeliveryService.java) | 按 userId 定向或广播，发送成功后更新持久状态；重连按用户补发，定时重试 QUEUED 并记录失败次数。 | `deliver`、`replayTo`、`retryQueued` |
| [SseConnectionRegistry](../services/notification-service/src/main/java/com/amatrix/sic/notification/SseConnectionRegistry.java) | 按用户维护线程安全 SSE 连接集合、CONNECTED、广播/定向发送、15 秒心跳和断开清理，并注册连接数 Gauge。 | `subscribe`、`broadcast`、`sendTo`、`heartbeat`、`size` |
| [SseController](../services/notification-service/src/main/java/com/amatrix/sic/notification/SseController.java) | GET /api/sse/subscribe，从 JWT 识别用户，注册 SSE 连接并补发该用户待投递消息。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [PendingNotificationDeliveryService.Pending](../services/notification-service/src/main/java/com/amatrix/sic/notification/PendingNotificationDeliveryService.java) | 待推送事件 ID、用户、事件名和解析后的 payload。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [NotificationContractBase](../services/notification-service/src/test/java/com/amatrix/sic/notification/contract/NotificationContractBase.java) | SSE 契约测试基类，构造订阅控制器及 mock 连接/补发服务。 |
| [NotificationEventConsumersTest](../services/notification-service/src/test/java/com/amatrix/sic/notification/NotificationEventConsumersTest.java) | 验证实例级 live 去重允许不同实例投递、相同实例重复事件被忽略。 |
| [PendingNotificationDeliveryServiceTest](../services/notification-service/src/test/java/com/amatrix/sic/notification/PendingNotificationDeliveryServiceTest.java) | 验证有连接才标记投递、重连仅补当前用户、定时重试和不可达计数。 |

## platform/integration-tests

此模块没有 src/main/java，作为跨模块测试工程。

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [FrontendApiSurfaceTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 验证 CSV 前端调用对应准确 Controller、Gateway 首条路由和可执行契约/响应类型。 |
| [InfrastructureIntegrationTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/InfrastructureIntegrationTest.java) | Testcontainers 验证五服务 Flyway、Rabbit/Redis/MinIO、死信重放和旧库空基线迁移。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [FrontendApiSurfaceTest.HttpEndpoint](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 方法和规范化路径组成的端点标识，用于对比。 |
| [FrontendApiSurfaceTest.ApiContract](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | CSV 中的 provider、方法、路径、成功状态与响应类型。 |
| [FrontendApiSurfaceTest.ProviderContract](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 可执行契约的端点、状态、Content-Type 和响应正文。 |
| [FrontendApiSurfaceTest.GatewayRoute](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 网关服务归属及 Path pattern 列表，检查首条路由匹配。 |

## platform/migration-tool

主包：`com.amatrix.sic.migration`。主源码 1 个，手写 Java 测试 1 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [LegacyFileMigrationTool](../platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java) | 一次性 prepare-files/verify-files CLI：防路径逃逸、核验旧哈希、上传确定性对象 key、建立清单，再下载逐字节哈希/大小/数量校验。 | `main` |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [LegacyFileMigrationTool.LegacyFile](../platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java) | 旧材料版本 ID、URL、预期哈希和文件名。 |
| [LegacyFileMigrationTool.Config](../platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java) | 迁移用 JDBC、MinIO、bucket、uploads 根目录配置，从环境变量读取。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [LegacyFileMigrationToolTest](../platform/migration-tool/src/test/java/com/amatrix/sic/migration/LegacyFileMigrationToolTest.java) | 验证旧 API 文件 URL 解析及路径逃逸拒绝。 |

## platform/dlq-recovery-tool

主包：`com.amatrix.sic.dlq`。主源码 3 个，手写 Java 测试 2 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [DeadLetterReplayProperties](../platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DeadLetterReplayProperties.java) | 校验显式重放口令、1～1000 条批次上限及确认超时。 | — |
| [DeadLetterReplayService](../platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DeadLetterReplayService.java) | 从公共 DLQ 拉取，解析 x-death 中白名单原路由，mandatory 发布并等待 confirm；成功 ACK，失败 NACK/requeue；增加重放标记并清除 TTL。 | `replay` |
| [DlqRecoveryToolApplication](../platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DlqRecoveryToolApplication.java) | 无数据源的一次性 Spring Boot 工具入口，校验参数、执行有界重放、记录数量并退出。 | `main` |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [DeadLetterReplayPropertiesTest](../platform/dlq-recovery-tool/src/test/java/com/amatrix/sic/dlq/DeadLetterReplayPropertiesTest.java) | 验证必须确认、有界批次与合法参数。 |
| [DeadLetterReplayServiceTest](../platform/dlq-recovery-tool/src/test/java/com/amatrix/sic/dlq/DeadLetterReplayServiceTest.java) | 验证确认后 ACK、发布失败或无路由 requeue、未知路由拒绝及 Rabbit 字节编码路由解析。 |

## 脚本中的 Python 类

| 文件及类 | 实现职责 | 关键方法 |
| --- | --- | --- |
| [startup_common.py：StartupError](../scripts/startup_common.py) | 启动流程统一错误类型，供命令执行、环境/资源验证及主入口报告失败。 | 继承统一异常/测试入口 |
| [startup_common.py：Redactor](../scripts/startup_common.py) | 清理已知敏感值、JWT 和 query token，供命令输出及日志脱敏。 | `redact` |
| [startup_common.py：RunLogger](../scripts/startup_common.py) | 创建带时间戳和 latest 的日志文件、同步输出脱敏消息并轮换旧日志。 | `write`、`raw`、`close` |
| [startup_common.py：CommandRunner](../scripts/startup_common.py) | 不经 shell 执行命令数组，捕获或流式输出结果、记录退出码/耗时及错误尾部，统一日志与脱敏。 | `capture`、`stream` |
| [test_startup.py：StartupTests](../scripts/tests/test_startup.py) | unittest 脚本测试类，验证环境文件、脱敏、资源归属/卷、端口冲突、重置口令、RSA 密钥和日志轮换。 | `env_file`、`test_env_literals_comments_and_optional_empty`、`test_env_rejects_duplicate_placeholder_control_and_expression`、`test_required_environment_fresh_and_history`、`test_redaction`、`test_json_utf8_array_and_ndjson`、`test_json_error_contains_location_without_secret`、`test_storage_history_and_cross_project_guards`、`test_container_compose_labels_are_read_from_docker_inspect_config`、`test_command_display_keeps_argument_boundaries`、`test_build_command_uses_compose_global_plain_progress`、`test_port_conflict_from_foreign_running_container`、`test_reset_confirmation_is_exact`、`test_rsa_pair_validation_uses_derived_public_key`、`test_logger_writes_latest_and_rotates_to_twenty` |
| [verify_compose_e2e.py：Verifier](../scripts/verify_compose_e2e.py) | Compose 自动验收器，检查健康、配置、注册、鉴权、API 路由、SSE 和注销，保存逐项结果。 | `request`、`gateway_get`、`api`、`check`、`readiness`、`container_health`、`config_discovery`、`unauthenticated`、`jwt_payload`、`login`、`service_routes`、`sse`、`logout`、`run` |

## 无独立类的资源和脚本

| 位置 | 功能 |
| --- | --- |
| `services/*/src/main/resources/db/migration` | Flyway 领域建表及结构升级。 |
| `platform/cloud-messaging/src/main/resources/db/common` | 各服务共用的 Outbox/Inbox 建表与领取重试字段升级。 |
| `services/workflow-service/src/main/resources/workflows` | 项目申请、纵向合同、项目结题三个辅助标签版 BPMN。 |
| `platform/config-server/src/main/resources/config` | 公共安全/消息/发现/监控参数及服务路由、数据库、端口。 |
| `platform/cloud-contracts/src/main/resources/contracts/frontend-api-surface.csv` | 前端接口面清单，供路由和契约覆盖验证。 |
| `services/*/src/test/resources/contracts` | 68 份 Spring Cloud Contract YAML，描述请求、响应与状态；JSON 是部分响应夹具。 |
| `infra/mysql/init-databases.sh` | 创建五业务 Schema 和独立账号；旧库 staging Schema 由切换流程创建。 |
| `infra/migration/import-legacy.sql` | 从 sic_legacy 向五领域 Schema 拆分导入旧业务数据。 |
| `infra/migration/verify-legacy-import.sql` | 导入数量、引用及流程序号等校验。 |
| `scripts/start_backend.py`、`scripts/start-backend.ps1` | 一键启动主程序及 PowerShell 包装。 |
| `scripts/verify_compose_e2e.py`、`scripts/verify-compose-e2e.ps1` | Compose 后端验收主程序及包装。 |
| `scripts/cutover.ps1` | 一次性单体到 Cloud 切换编排。 |
| `scripts/generate-jwt-keys.ps1` | RSA 密钥生成与输出。 |
| `scripts/tests/verify_history_retention.py` | 写入验证样本、经过 History 重启后核验数据库/文件保留的场景工具。 |
| `verification/*.json` | 仓库中保存的历史运行结果，不是本次调研重新运行的结果。 |

清单校验结果：164 个主源码文件、52 个主源码命名内部/局部类型、44 个手写 Java 测试文件、12 个测试内部类型、6 个 Python 类均有职责条目。
