# SicProjectIS_cloud 类功能索引

首次调研日期：2026-10-10。整体架构、数据归属和业务链路见 [项目结构总览](project-structure-overview.md)。

本次核对日期：2026-10-12，与 `ver.springcloud` 分支 `de761c5` 源码逐文件对齐（2026-10-10 之后新增的文件已补条目，已有条目按新包路径重排）。2026-10-11 的 `1728b1f` 把五个业务服务从平铺包重排为 controller/service/policy/client/dao/entity 等分层包并新增 Mapper 与行类型，本索引据此重排全部源码路径并补齐新增条目。

本索引逐项覆盖当前 242 个主 Java 源码文件、其中声明的命名内部/局部类型、61 个手写 Java 测试文件，以及启动/验收 Python 类。接口、注解、record 也列入；不包含 target 生成代码、匿名对象或依赖库内部类。源码链接均相对本文件。

“关键公开方法”来自源码声明，包含同文件内部类型的公开方法，不展开构造器和 record 自动访问器；— 表示未列出显式 public/protected 业务方法。Controller/Feign/Bean 的包内方法按职责描述阅读；内部记录类型只承载数据，并非独立部署服务。

## platform/cloud-contracts

主包：`com.amatrix.sic.contracts`。主源码 19 个，手写 Java 测试 0 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [AccessDecision](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/AccessDecision.java) | 单项访问判定：是否允许、项目版本、原因码；allow/deny 构造结果。 | `allow`、`deny` |
| [ActorContext](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ActorContext.java) | 不可变操作人上下文：用户、部门、角色、权限、Token ID、授权版本；hasRole/hasPermission 判断能力。 | `hasRole`、`hasPermission` |
| [ApiResponse](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApiResponse.java) | 统一 HTTP 响应 success/data/message；ok 与 error 构造成功/失败包装。 | `ok`、`error` |
| [ApplicationSagaCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApplicationSagaCommand.java) | Project 发给 Workflow 的申请创建命令：sagaId、项目及申请人、项目金额日期、申请标题与正文摘要。 | — |
| [ApplicationSagaResult](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApplicationSagaResult.java) | 申请 Saga 结果：Saga/项目/模块 ID、初始迁移及状态。 | — |
| [DepartmentSummary](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/DepartmentSummary.java) | 跨服务部门摘要：部门 ID、部门名称与启用状态。 | — |
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

主包：`com.amatrix.sic.messaging`。主源码 11 个，手写 Java 测试 5 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [CloudMessagingAutoConfiguration](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/CloudMessagingAutoConfiguration.java) | 在存在 `SqlSessionTemplate` 时启用调度、Outbox/去重组件、编程注册的 MessagingMapper、JDBC 延迟观察者、业务 Topic Exchange、死信 Exchange/队列及消息指标；`@AutoConfigureAfter` 同时声明在 JdbcTemplate 与 MyBatis 自动配置之后。 | — |
| [ConsumedEventGuard](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/ConsumedEventGuard.java) | 在 consumed_event 插入 eventId+consumerName；重复键返回 false。应和业务处理同事务，失败回滚后可重试。 | `begin` |
| [JdbcLatencyObserver](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/JdbcLatencyObserver.java) | BeanPostProcessor 包装 DataSource 及其连接/语句，按 acquire/prepare/statement/hold 阶段计时并记录超阈值告警；只注入校验通过的 traceId。 | `postProcessAfterInitialization` |
| [MessagingMapper](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/dao/MessagingMapper.java) | 本模块 MyBatis 接口：Outbox 插入、候选查询与租约领取，发布成功/失败回写，消费去重插入及待发/失败/最老年龄计数。 | — |
| [MessagingMetrics](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/MessagingMetrics.java) | 注册各服务 Outbox 待发、失败、最老年龄以及公共死信队列积压 Gauge。 | — |
| [OutboxEntry](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/entity/OutboxEntry.java) | MyBatis 查询行投影（eventId 为字符串）：事件 ID/类型、routingKey、JSON、创建时间和尝试次数。 | — |
| [OutboxProperties](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxProperties.java) | sic.messaging 配置：交换机、批量、调度、发布开关、确认超时、领取租约、最大重试间隔。 | — |
| [OutboxPublisher](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxPublisher.java) | 定时领取待发事件，使用 RabbitMQ publisher confirm 后标记成功；失败指数退避，中断恢复线程标识。 | `publishPending` |
| [OutboxRecord](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRecord.java) | 仓储层投影（eventId 为 UUID），供发布器确认/失败回写：事件 ID/类型、routingKey、JSON、创建时间和尝试次数。 | — |
| [OutboxRepository](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxRepository.java) | 写 Outbox，按条件更新领取租约，并记录发布成功/失败、下次尝试、错误和锁状态。 | `insert`、`claimPending`、`markPublished`、`markFailed` |
| [OutboxService](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/OutboxService.java) | 序列化 EventEnvelope 并在事务中写 Outbox，连接本地业务事务与后续消息发布。 | `enqueue` |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [JdbcLatencyObserver.ConnectionSupplier](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/JdbcLatencyObserver.java) | 连接工厂函数式接口，供代理在计时后取得真实连接。 |
| [JdbcLatencyObserver.ObservedSource](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/JdbcLatencyObserver.java) | DataSource 包装代理（继承 DelegatingDataSource）：按阶段计时并透传原数据源。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [CloudMessagingAutoConfigurationTest](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/CloudMessagingAutoConfigurationTest.java) | 验证在实际 Spring Context（JdbcTemplate 与 MyBatis 自动配置之后）中完成消息自动装配，并断言 Outbox/去重/发布器 Bean 存在。 |
| [ConsumedEventGuardTest](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/ConsumedEventGuardTest.java) | 验证相同消费者去重，以及业务失败时去重记录回滚。 |
| [JdbcLatencyObserverTest](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/JdbcLatencyObserverTest.java) | 验证观察代理保持事务连接复用与回滚、按阶段记录指标，且 traceId 校验与指标故障不影响 SQL 异常。 |
| [MessagingTestMapper](../platform/cloud-messaging/src/test/java/com/amatrix/sic/messaging/MessagingTestMapper.java) | 测试夹具：用编程方式装配 SqlSessionFactory 并指向 MessagingMapper.xml，供消息测试取 Mapper。 |
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
| [GatewayErrorWriter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/error/GatewayErrorWriter.java) | 统一写出网关错误的 JSON 响应体，保持 {success,data,message} 形状。 | `write` |
| [GatewayJsonErrorHandler](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/error/GatewayJsonErrorHandler.java) | 将网关未处理异常映射为 JSON 错误响应。 | `handle` |
| [GatewayRateLimitProperties](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/config/properties/GatewayRateLimitProperties.java) | sic.gateway.rate-limit 配置：开关、窗口秒数与每窗口请求上限。 | — |
| [GatewayRateLimitWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/GatewayRateLimitWebFilter.java) | 按来源 IP 与每秒窗口在 Redis 计数限流；Redis 失败时放行，超限返回 429。 | `filter` |
| [GatewayRequestLoggingFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/GatewayRequestLoggingFilter.java) | 记录路径/状态/耗时/用户与关联标识，并汇总到请求日志。 | `filter` |
| [GatewaySecurityConfig](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/config/GatewaySecurityConfig.java) | WebFlux 安全链：JWT 校验、登录注册放行、内部路径拒绝、SSE token 转换与监控端点放行。 | — |
| [GatewaySecurityProperties](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/config/properties/GatewaySecurityProperties.java) | sic.security 的 issuer/audience 与公开路径配置。 | — |
| [GatewayServiceApplication](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayServiceApplication.java) | Gateway 启动入口。 | `main` |
| [SessionValidityAuthorizationManager](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/security/SessionValidityAuthorizationManager.java) | 在 JWT 校验后查询 Redis 的注销 jti 与用户会话版本，判定会话是否仍然有效。 | `check` |
| [SseTokenRelayWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/SseTokenRelayWebFilter.java) | 把 SSE 订阅 query 中的 token 转成 Bearer Header 并移除 URL 中的 token。 | `filter` |
| [TraceIdWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/filter/TraceIdWebFilter.java) | 在安全链可能提前返回之前规范化/生成 X-Trace-Id，同时写入下游请求和响应。 | `filter`、`getOrder` |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [GatewayErrorAndRateLimitTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/error/GatewayErrorAndRateLimitTest.java) | 验证 404/503/429 统一包装、限流短路和 Redis 计数故障放行。 |
| [GatewayPayloadPassThroughTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/filter/GatewayPayloadPassThroughTest.java) | 验证 multipart 内容及流式下载分块/文件名 Header 原样透传。 |
| [GatewaySecurityConfigTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/config/GatewaySecurityConfigTest.java) | 验证匿名/无效会话、登录放行、internal 拒绝及监控边界。 |
| [SessionValidityAuthorizationManagerTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/security/SessionValidityAuthorizationManagerTest.java) | 验证登记且当前版本会话、旧 ver、注销/过时、缺失/损坏/不可用 Redis 及缺少 JWT 字段。 |
| [SseTokenRelayWebFilterTest](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/filter/SseTokenRelayWebFilterTest.java) | 验证 SSE token 转 Header、移除 query token 和其他请求保持原状。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [GatewaySecurityConfigTest.TestEndpoints](../services/gateway-service/src/test/java/com/amatrix/sic/gateway/config/GatewaySecurityConfigTest.java) | 响应式测试端点，支撑网关 API、认证和内部路径验证。 |

## services/iam-service

主包：`com.amatrix.sic.iam`。主源码 31 个，手写 Java 测试 6 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [AdminMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/AdminMapper.java) | 管理端 MyBatis 接口：用户筛选与详情、角色权限矩阵、部门树与成员、角色和部门 ID 解析、授权版本锁定与递增，以及用户角色/权限/部门的增删改。 | — |
| [AdminUserRow](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/AdminUserRow.java) | 管理端用户列表行：用户 ID/账号/姓名、部门、联系方式、启用状态与角色码。 | — |
| [AuthApplicationService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/AuthApplicationService.java) | 规范化用户名、验证 BCrypt 密码与启用状态、注册用户、读取当前用户、签发登录响应、维护 Redis 授权版本和注销黑名单。 | `login`、`register`、`current`、`logout` |
| [AuthController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/AuthController.java) | /api/auth 的 login/register/me/logout HTTP 入口，提取 JWT 用户/jti/过期时间并委派业务服务。 | — |
| [BootstrapAdminInitializer](../services/iam-service/src/main/java/com/amatrix/sic/iam/bootstrap/BootstrapAdminInitializer.java) | 启动时按配置创建不存在的管理员并赋 SYSTEM_ADMIN；缺少配置、密码短于 10 或已有同名用户时跳过。 | `run` |
| [BootstrapAdminProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/properties/BootstrapAdminProperties.java) | 初始管理员 username/password/realName 配置。 | — |
| [ExpertQualificationController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/ExpertQualificationController.java) | 个人专家资格提交/查询及管理员资格列表、部门审核、科技处审核 API。 | — |
| [ExpertQualificationService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/ExpertQualificationService.java) | 专家资格分阶段申请审核，校验重复/有效申请及部门范围；科技处通过后赋 EXPERT、更新授权版本及发布资格/用户变更事件。 | `submit`、`mine`、`query`、`departmentReview`、`scienceReview` |
| [IamAdminController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java) | 管理员用户、角色权限、部门及部门成员 API，解析请求字段并调用 IamAdminService。 | — |
| [IamAdminService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/IamAdminService.java) | 用户筛选/详情、改角色/启停、权限矩阵维护、部门 CRUD/成员管理；限制自禁用/移除自身管理员与越部门操作，更新授权版本并发 IAM 变更事件。 | `queryUsers`、`userDetail`、`updateRoles`、`updateStatus`、`permissions`、`permissionMatrix`、`updateRolePermissions`、`departments`、`createDepartment`、`updateDepartment`、`deleteDepartment`、`departmentMembers`、`candidates`、`upsertMember`、`updateMemberRoles`、`removeMember` |
| [IamCounters](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/IamCounters.java) | 概览计数：用户、启用用户、角色、权限四个总数。 | — |
| [IamDtos](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java) | 身份接口 DTO 容器，定义登录/注册、当前用户、登录响应和含密码哈希的内部账号记录。 | `currentUser` |
| [IamJwtProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/properties/IamJwtProperties.java) | sic.jwt 的 issuer、audience、有效分钟数、公私钥配置；有效期默认 120 分钟。 | — |
| [IamOverviewService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/IamOverviewService.java) | 只读汇总用户/启用用户/角色/权限计数与角色人数分布，组装管理员概览 IamOverviewStats；本身是 Mapper 的薄封装。 | `overview` |
| [IamRepository](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/IamRepository.java) | 用户仓储：`@Repository` 包装 IdentityMapper；findByUsername/findById 在只读事务内查询用户、部门与角色权限，summaries 批量取用户摘要与角色；insertUser 无事务注解，assignRole 在读写事务内先锁角色与用户再写映射。 | `findByUsername`、`findById`、`summaries`、`insertUser`、`assignRole`、`roleCodes`、`permissionCodes` |
| [IamSecurityConfig](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/IamSecurityConfig.java) | IAM 专属 Servlet 安全链、BCrypt 编码器、本地公钥 JWT decoder；开放认证/JWK/服务 Token bootstrap，其他内部接口要求内部 scope。 | — |
| [IamServiceApplication](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamServiceApplication.java) | IAM Spring Boot 启动入口，扫描配置属性。 | `main` |
| [IdentityMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/IdentityMapper.java) | 身份 MyBatis 接口：按用户名或 ID 取账号、批量用户摘要与角色、插入用户并分配角色，以及行锁与角色/权限查询。 | — |
| [IdentityRow](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/IdentityRow.java) | 登录用账号行：账号、密码哈希、姓名、部门、联系方式、启用状态与授权版本。 | — |
| [InternalIdentityController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java) | 内部服务 Token 兑换（恒定时间比较 secret）、用户摘要和管理员概览统计接口。 | — |
| [JwkController](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/JwkController.java) | GET /.well-known/jwks.json，发布 RSA 公钥的 JWK 参数供服务与 Gateway 验签。 | — |
| [JwtTokenIssuer](../services/iam-service/src/main/java/com/amatrix/sic/iam/security/JwtTokenIssuer.java) | 签发用户及内部服务 RS256 JWT，设置角色、权限、scope、jti、authVersion/ver、用户/部门信息和有效期。 | `issue`、`issueServiceToken` |
| [OverviewMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/OverviewMapper.java) | 概览 MyBatis 接口：一次取用户/角色/权限计数，并按角色统计人数。 | — |
| [QualificationMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/QualificationMapper.java) | 专家资格 MyBatis 接口：锁定申请人、判断是否有生效申请、插入与筛选申请、部门/科技处审核写回，以及授权版本读取与递增。 | — |
| [RsaKeyProvider](../services/iam-service/src/main/java/com/amatrix/sic/iam/security/RsaKeyProvider.java) | 加载配置的 PKCS8/X509 RSA 密钥或生成临时 3072 位密钥；校验成对配置、匹配及至少 2048 位，生成公钥指纹 kid。 | `publicKey`、`privateKey`、`keyId` |
| [ServiceTokenProperties](../services/iam-service/src/main/java/com/amatrix/sic/iam/config/properties/ServiceTokenProperties.java) | sic.service-token 的 bootstrapSecret 配置。 | — |
| [SessionMapper](../services/iam-service/src/main/java/com/amatrix/sic/iam/dao/SessionMapper.java) | 会话版本 MyBatis 接口：按用户加行锁读取当前授权版本，供提交后发布栅栏使用。 | — |
| [SessionVersionService](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/SessionVersionService.java) | 以 Redis pending 键把授权版本发布推迟到数据库提交之后：提交前占栅栏、提交后发布，回滚只释放本方栅栏，并定时恢复遗留栅栏。 | `publishLogin`、`publishAfterCommit`、`recover` |
| [SummaryRow](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/SummaryRow.java) | 内部用户摘要行：用户 ID、账号、姓名、部门、启用状态与授权版本。 | — |
| [UserRoleRow](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/UserRoleRow.java) | 用户与角色码的一对一映射行。 | — |
| [UserVersionRow](../services/iam-service/src/main/java/com/amatrix/sic/iam/entity/UserVersionRow.java) | 加锁读取到的用户授权版本行。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [IamDtos.LoginRequest](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java) | 用户名与密码。 |
| [IamDtos.RegisterRequest](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java) | 账号、密码、真实姓名、部门和联系方式。 |
| [IamDtos.CurrentUser](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java) | 对外用户身份及角色/权限信息，不含密码哈希。 |
| [IamDtos.LoginResponse](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java) | token、expiresAt、user。 |
| [IamDtos.UserAccount](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java) | 仓储内部用户记录，包括 passwordHash、enabled、authVersion；currentUser 转对外结构。 |
| [JwtTokenIssuer.IssuedToken](../services/iam-service/src/main/java/com/amatrix/sic/iam/security/JwtTokenIssuer.java) | 签发结果 token、expiresAt、tokenId。 |
| [SessionVersionService.Pending](../services/iam-service/src/main/java/com/amatrix/sic/iam/service/SessionVersionService.java) | 提交后发布用的栅栏条目，实现 TransactionSynchronization：记录用户、版本与栅栏标识。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [AuthApplicationServiceTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/service/AuthApplicationServiceTest.java) | 验证登录写 Redis 授权版本及注销黑名单 TTL。 |
| [AuthContractBase](../services/iam-service/src/test/java/com/amatrix/sic/iam/contract/AuthContractBase.java) | IAM 契约测试基类：构造身份/管理/资格控制器和 mock 响应，供生成的契约测试复用。 |
| [IamDepartmentManagementTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/service/IamDepartmentManagementTest.java) | 验证部门成员视图的全局/无部门/跨部门边界、空部门编码生成与编辑保留，以及仅调部门不改角色。 |
| [IamSecurityConfigTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/config/IamSecurityConfigTest.java) | 验证 IAM Bearer 安全、唯一公开 bootstrap 内部入口及 JWK/监控路径。 |
| [JwtTokenIssuerTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/security/JwtTokenIssuerTest.java) | 验证 RS256 用户/服务 Token 字段、scope、kid 及部分/不匹配密钥启动失败。 |
| [SessionVersionServiceTest](../services/iam-service/src/test/java/com/amatrix/sic/iam/service/SessionVersionServiceTest.java) | 验证授权版本只在数据库提交后发布、无法占栅栏时回滚数据库、发布失败保留已提交版本与栅栏、过时登录被拒。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [IamSecurityConfigTest.TestApplication](../services/iam-service/src/test/java/com/amatrix/sic/iam/config/IamSecurityConfigTest.java) | 测试专用 IAM 安全应用上下文。 |
| [IamSecurityConfigTest.TestEndpoints](../services/iam-service/src/test/java/com/amatrix/sic/iam/config/IamSecurityConfigTest.java) | 身份测试端点，支撑 API、bootstrap、JWK 及监控边界验证。 |

## services/project-service

主包：`com.amatrix.sic.project`。主源码 37 个，手写 Java 测试 7 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [ApplicationSagaMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ApplicationSagaMapper.java) | 申请 Saga MyBatis 接口：插入临时项目与负责人成员及 Saga 记录，推进 WORKFLOW_CREATED 与确认/失败/完成/补偿状态，查询可恢复与近期同 hash 的 Saga，并统计未完成/失败/最老年龄。 | — |
| [ApplicationSagaRow](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ApplicationSagaRow.java) | Saga 去重与恢复投影：Saga ID、项目、操作人、状态与响应 JSON。 | — |
| [IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/IamDirectoryClient.java) | Feign 调用 IAM 内部用户摘要及管理员概览统计。 | — |
| [InternalProjectController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/internal/InternalProjectController.java) | 内部单项/批量访问判定，以及 Workflow 专家授权同步和撤销接口。 | — |
| [InternalTokenProvider](../services/project-service/src/main/java/com/amatrix/sic/project/client/auth/InternalTokenProvider.java) | 同步缓存内部 Token，在过期前 60 秒刷新，返回 Bearer Header。 | `authorization` |
| [ProjectAccessPolicy](../services/project-service/src/main/java/com/amatrix/sic/project/policy/ProjectAccessPolicy.java) | 项目访问决策中心；批量加载项目、成员及有效授权，区分读取/材料/业务数据/角色任务操作，返回原因码和上下文。 | `canAccess`、`decide`、`decideBatch` |
| [ProjectAdminController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAdminController.java) | GET /api/admin/projects/authorizations，列出当前管理员可管理的项目授权。 | — |
| [ProjectApplicationSagaService](../services/project-service/src/main/java/com/amatrix/sic/project/service/saga/ProjectApplicationSagaService.java) | 编排临时项目与 Workflow APPLICATION 创建；近期请求 hash 去重、失败补偿、最终确认和定时确认恢复；写申请启动事件。 | `start`、`recoverIncompleteConfirmations` |
| [ProjectAuditController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuditController.java) | GET /api/admin/overview 与 /api/admin/audit-logs，提供管理概览和筛选日志。 | — |
| [ProjectAuditMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectAuditMapper.java) | 审计与概览 MyBatis 接口：写入本地及 IAM 管理日志、按条件筛选日志、统计有效授权与日志数、按用户 ID 取名。 | — |
| [ProjectAuditService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectAuditService.java) | 调用 IAM 聚合用户/角色统计，结合本地授权统计生成概览；按条件查询管理日志并执行概览/审计权限校验。 | `overview`、`logs` |
| [ProjectAuthorizationController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java) | 项目授权详情、负责人更换、成员单条/批量变更、专家/财务/代录单条/批量授权及撤销 API。 | — |
| [ProjectAuthorizationMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectAuthorizationMapper.java) | 授权中心 MyBatis 接口：读取与加锁项目、可管理项目、成员与授权查询、负责人更换、部门指派、财务候选人、成员升降级与增删、grant 幂等插入与撤销、专家访问结束、版本与生命周期更新。 | — |
| [ProjectAuthorizationService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectAuthorizationService.java) | 实现授权管理与角色/管理能力校验；维护项目版本、成员/授权关系、审计和 project.authorization；提供内部专家 grant 幂等同步/撤销。 | `detail`、`changeLeader`、`upsertMember`、`upsertMembers`、`removeMember`、`assignGrant`、`assignGrants`、`syncExpertGrant`、`revokeExpertGrant`、`revoke`、`revokeBatch`、`manageableProjects` |
| [ProjectController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectController.java) | 项目列表查询和 POST /api/project-applications/start 入口，将 JWT 用户/部门及原授权 Header 交给查询/Saga 服务。 | — |
| [ProjectDepartmentController](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectDepartmentController.java) | PUT /api/projects/{projectId}/department 入口，从 JWT 构造操作人并委托部门服务；请求体为内部 record AssignDepartmentRequest。 | — |
| [ProjectDepartmentService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectDepartmentService.java) | 为无归属项目补充部门：先经 IAM 校验部门存在且启用，再在事务内锁项目、写审计并发布 project.authorization 事件。 | `requireEnabled`、`assignMissing` |
| [ProjectDtos](../services/project-service/src/main/java/com/amatrix/sic/project/dto/ProjectDtos.java) | 发起申请的请求/响应容器，响应包含项目 ID、模块 ID、StateTransitionContract。 | — |
| [ProjectExpertAccessEndedConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectExpertAccessEndedConsumer.java) | 消费专家访问结束事件，按项目/模块/轮次/节点撤销专家 grant；历史节点引用丢失时按事件中的专家身份回退限定范围。 | `consume` |
| [ProjectFinanceReadinessService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectFinanceReadinessService.java) | 按项目、模块、轮次、节点取财务授权候选人，统计启用且具 FINANCE_ADMIN 角色的人数，返回财务办理是否就绪及提示。 | `readiness` |
| [ProjectGrantScopeRow](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectGrantScopeRow.java) | 一条有效授权的范围行：项目、角色、模块类型、轮次与任务节点。 | — |
| [ProjectQueryMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectQueryMapper.java) | 项目列表 MyBatis 接口：以单条 SQL 按全局/部门/负责人/成员/有效授权（含专家与财务开关）过滤 ACTIVE 项目。 | — |
| [ProjectQueryService](../services/project-service/src/main/java/com/amatrix/sic/project/service/ProjectQueryService.java) | 以一个 SQL 按全局/部门/负责人/成员/授权过滤 ACTIVE 项目；转换角色与数字类型。 | `accessibleProjects`、`roles`、`number` |
| [ProjectRecord](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectRecord.java) | 项目主数据行：编码、名称、部门、负责人、类型/级别/阶段，供授权与能力检查。 | — |
| [ProjectSagaMetrics](../services/project-service/src/main/java/com/amatrix/sic/project/metrics/ProjectSagaMetrics.java) | 统计未结束 Saga、失败/补偿 Saga 及最老未完成 Saga 年龄。 | — |
| [ProjectScopeMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/ProjectScopeMapper.java) | 批量判权 MyBatis 接口：按项目 ID 集合取项目版本与负责人、当前用户的成员关系、有效授权范围。 | — |
| [ProjectScopeRow](../services/project-service/src/main/java/com/amatrix/sic/project/entity/ProjectScopeRow.java) | 批量判权用项目行：项目 ID、版本、负责人与部门。 | — |
| [ProjectServiceApplication](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectServiceApplication.java) | Project 启动入口，启用 Feign 和配置属性扫描。 | `main` |
| [ProjectSnapshotMessagingConfiguration](../services/project-service/src/main/java/com/amatrix/sic/project/config/ProjectSnapshotMessagingConfiguration.java) | 声明 IAM 快照、工作流生命周期、专家访问结束的持久队列及 Topic 绑定和死信配置。 | — |
| [ProjectUserDirectory](../services/project-service/src/main/java/com/amatrix/sic/project/service/directory/ProjectUserDirectory.java) | 本地 user_snapshot 目录查询；require 在缺少快照时从 IAM 拉取存在用户，refresh 强制拉取、upsert 更新，allEnabled 提供启用候选用户；require 本身不拒绝禁用用户。 | `require`、`refresh`、`find`、`allEnabled`、`upsert` |
| [ProjectUserSnapshotConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectUserSnapshotConsumer.java) | 去重消费 iam.changed，按用户/角色/部门变更刷新 user_snapshot，并保存 IAM 相关管理审计信息。 | `synchronize` |
| [ProjectWorkflowLifecycleConsumer](../services/project-service/src/main/java/com/amatrix/sic/project/messaging/consumer/ProjectWorkflowLifecycleConsumer.java) | 去重消费工作流项目生命周期事件，更新 Project 的 business_status 及版本。 | `consume` |
| [ServiceClientProperties](../services/project-service/src/main/java/com/amatrix/sic/project/config/properties/ServiceClientProperties.java) | Project 的 sic.service-client.bootstrapSecret 配置。 | — |
| [ServiceTokenClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/ServiceTokenClient.java) | Feign 通过 bootstrap secret 向 IAM 换取 project-service 内部 Token。 | — |
| [UserSnapshotMapper](../services/project-service/src/main/java/com/amatrix/sic/project/dao/UserSnapshotMapper.java) | 用户快照 MyBatis 接口：按 ID 单个或批量读取快照、读取全部启用用户，以及按 IAM 摘要 upsert 快照与角色 JSON。 | — |
| [UserSnapshotRow](../services/project-service/src/main/java/com/amatrix/sic/project/entity/UserSnapshotRow.java) | 本地用户快照行：账号、姓名、部门、角色 JSON、启用状态与版本。 | — |
| [WorkflowSagaClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/WorkflowSagaClient.java) | Feign 调用 Workflow 内部申请 Saga 创建、状态查询、补偿删除；创建时转发用户授权 Header。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [ProjectAccessPolicy.Decision](../services/project-service/src/main/java/com/amatrix/sic/project/policy/ProjectAccessPolicy.java) | 内部 allowed/reason 决策结果，提供 allow/deny。 |
| [ProjectAccessPolicy.Grant](../services/project-service/src/main/java/com/amatrix/sic/project/policy/ProjectAccessPolicy.java) | 授权角色、模块、轮次和任务节点范围。 |
| [ProjectDepartmentController.AssignDepartmentRequest](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectDepartmentController.java) | 补充部门的请求体：目标部门 ID 与操作原因。 |
| [ProjectDtos.StartProjectApplicationRequest](../services/project-service/src/main/java/com/amatrix/sic/project/dto/ProjectDtos.java) | 项目编码/名称/类型/级别/金额/日期及申请标题/限额/摘要。 |
| [ProjectDtos.StartProjectApplicationResponse](../services/project-service/src/main/java/com/amatrix/sic/project/dto/ProjectDtos.java) | projectId、moduleInstanceId、初始 transition。 |
| [ProjectUserDirectory.SelectorSnapshot](../services/project-service/src/main/java/com/amatrix/sic/project/service/directory/ProjectUserDirectory.java) | 用户选择器短期缓存条目：epoch、过期时间与用户摘要列表。 |
| [ServiceTokenClient.TokenResponse](../services/project-service/src/main/java/com/amatrix/sic/project/client/ServiceTokenClient.java) | Project 接收到的内部 Token、过期时间、Token ID。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [MethodSecurityProxyTest](../services/project-service/src/test/java/com/amatrix/sic/project/MethodSecurityProxyTest.java) | 验证通过 Spring Bean 代理调用时权限元注解实际生效。 |
| [ProjectAccessPolicyTest](../services/project-service/src/test/java/com/amatrix/sic/project/policy/ProjectAccessPolicyTest.java) | 验证全局/部门/负责人/成员/grant 矩阵和无效/非 ACTIVE 项目拒绝。 |
| [ProjectApplicationSagaServiceTest](../services/project-service/src/test/java/com/amatrix/sic/project/service/saga/ProjectApplicationSagaServiceTest.java) | 验证近期请求去重、失败补偿及最终确认定时恢复。 |
| [ProjectContractBase](../services/project-service/src/test/java/com/amatrix/sic/project/contract/ProjectContractBase.java) | Project 契约测试基类：构造项目/授权/概览/审计控制器及 mock 响应。 |
| [ProjectExpertAccessEndedConsumerTest](../services/project-service/src/test/java/com/amatrix/sic/project/messaging/consumer/ProjectExpertAccessEndedConsumerTest.java) | 验证历史节点引用不可解析时以批次专家身份限定撤销范围。 |
| [ProjectQueryServiceTest](../services/project-service/src/test/java/com/amatrix/sic/project/service/ProjectQueryServiceTest.java) | 验证超过 100 个项目的单 SQL 过滤和部门/全局范围。 |
| [ProjectTestMapper](../services/project-service/src/test/java/com/amatrix/sic/project/ProjectTestMapper.java) | 测试夹具：按给定 DataSource 与 XML 资源装配 SqlSessionFactory 并开启 callSettersOnNulls，供 Project Mapper 测试取接口。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [MethodSecurityProxyTest.Config](../services/project-service/src/test/java/com/amatrix/sic/project/MethodSecurityProxyTest.java) | 装配测试 Actor 解析、权限 Policy 与受保护 Bean 的 Spring 配置。 |
| [MethodSecurityProxyTest.SecuredProbe](../services/project-service/src/test/java/com/amatrix/sic/project/MethodSecurityProxyTest.java) | 带项目访问权限元注解的测试业务方法，验证 Spring 代理拦截。 |
| [ProjectQueryServiceTest.CountingDataSource](../services/project-service/src/test/java/com/amatrix/sic/project/service/ProjectQueryServiceTest.java) | 包装测试 DataSource，计数 SQL prepare 调用以验证单语句查询。 |

## services/workflow-service

主包：`com.amatrix.sic.workflow`。主源码 74 个，手写 Java 测试 14 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [ApplicationDraftRow](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ApplicationDraftRow.java) | 申请草稿行：模块、项目、标题、限额标记、摘要、JSON 正文与创建时间。 | — |
| [ApplicationSagaWorkflowService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/saga/ApplicationSagaWorkflowService.java) | 按 sagaId 幂等创建申请实例、项目快照、申请正文、初始任务/历史/事件；查询结果；仅清理指定 Saga 的关联流程数据，补偿可重复。 | `create`、`find`、`compensate` |
| [BusinessTask](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/BusinessTask.java) | 业务任务的模块与轮次定位。 | — |
| [DefinitionRow](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/DefinitionRow.java) | 定义行：编码、名称、版本、状态、是否发布与 BPMN XML。 | — |
| [ExpertAssignment](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertAssignment.java) | 专家分配行：批次、专家用户与状态。 | — |
| [ExpertBatch](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertBatch.java) | 评审批次行：模块、节点、轮次、去极值开关、通过/推荐阈值与状态。 | — |
| [ExpertGrant](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertGrant.java) | 专家授权的项目、模块类型与节点范围。 | — |
| [ExpertGrantScope](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertGrantScope.java) | 专家授权范围行：项目、模块类型、轮次、授权人、节点与专家姓名。 | — |
| [ExpertModule](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ExpertModule.java) | 可评审模块行：定义、节点、状态与轮次。 | — |
| [ExpertPersonalReviewController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertPersonalReviewController.java) | GET /api/expert-reviews/my-assignments 及其详情入口，从 JWT 解析操作人并委托本人评审服务。 | — |
| [ExpertPersonalReviewService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/review/ExpertPersonalReviewService.java) | 查询当前专家的评审任务列表与详情：校验分页与状态白名单，按可操作模块标注 canReview 并附本人评分。 | `list`、`detail` |
| [ExpertReviewCompletedConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/ExpertReviewCompletedConsumer.java) | 去重并校验 EXPERT_REVIEW_COMPLETED 事件，调用运行器尝试自动迁移对应批次。 | `consume` |
| [ExpertReviewController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java) | 专家评审批次创建/详情、专家分配、评分提交和分配删除 API；保留 POST remove 兼容入口。 | — |
| [ExpertReviewDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/ExpertReviewDtos.java) | 评审批次创建、专家分配、评分提交和分项评分请求容器。 | — |
| [ExpertReviewMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/ExpertReviewMapper.java) | 评审读写 MyBatis 接口：批次、专家分配、评分与批次状态的插入、锁定与更新。 | — |
| [ExpertReviewService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/review/ExpertReviewService.java) | 当前节点/轮次内创建批次、核验 IAM 专家、分配/移除并同步 Project grant；本人提交评分、计算有效均分/阈值结果并发布完成事件。 | `create`、`assign`、`submit`、`remove`、`detail` |
| [ExpertReviewViewMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/ExpertReviewViewMapper.java) | 评审读模型 MyBatis 接口：批次、分配、评分与本人评审历史的查询。 | — |
| [ExpertReviewViewRepository](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/review/ExpertReviewViewRepository.java) | 读取评审批次、专家分配及分项评分，形成批次详情和模块级实时评审数据投影。 | `batchDetail`、`moduleReviews` |
| [FlowableBpmnDefinitionParser](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/parser/FlowableBpmnDefinitionParser.java) | 禁用外部实体/DTD 后用 Flowable 转换器解析 BPMN 及 rm 辅助标签，构建流程、节点、连线、材料、单据模型并补齐状态。 | `parse` |
| [InternalApplicationSagaController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/internal/InternalApplicationSagaController.java) | 内部申请 Saga create/status/compensate HTTP 入口；Saga 不存在返回 404。 | — |
| [LeaderTask](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/LeaderTask.java) | 负责人任务行：任务、项目、模块类型、轮次与节点。 | — |
| [MaterialReservationClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/MaterialReservationClient.java) | Feign 调 Material 预留及释放接口；成功确认通过事件完成。 | — |
| [ModuleBusinessDataController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ModuleBusinessDataController.java) | GET /api/module-instances/{moduleInstanceId}/business-data，返回模块聚合业务数据。 | — |
| [ModuleRecord](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ModuleRecord.java) | 模块实例行：项目、发起人、类型、定义、当前节点与状态、序号、轮次、结束标记及时间戳。 | — |
| [ModuleRound](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ModuleRound.java) | 模块 ID 与轮次的定位行。 | — |
| [ModuleSuccessorPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/ModuleSuccessorPolicy.java) | 按模块及终态映射下一模块/项目阶段：批准申请→合同，批准合同→结题，接受结题→项目完成；其他终态无后继。 | `completion`、`hasSuccessor` |
| [NodeFormController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java) | 节点表单定义、读取、单例保存、历史记录新增/修改/删除 API，从请求提取模块和用户上下文。 | — |
| [NodeFormMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/NodeFormMapper.java) | 节点表单 MyBatis 接口：按模块、节点、轮次读写表单记录及历史记录查询。 | — |
| [NodeFormModule](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/NodeFormModule.java) | 表单用模块上下文：项目、模块类型、节点、状态、轮次与结束标记。 | — |
| [NodeFormRecord](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/NodeFormRecord.java) | 已存表单记录：记录 ID 与 JSON 正文。 | — |
| [NodeFormService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/form/NodeFormService.java) | 维护 43 个节点表单目录；按节点/状态匹配，区分三种写模式；校验当前节点和轮次，存储 JSON，聚合业务草稿与实时专家评审视图。 | `definitions`、`forNode`、`get`、`save`、`createRecord`、`updateRecord`、`deleteRecord`、`businessData` |
| [NoticeController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NoticeController.java) | /api/notices 列表、详情、创建、更新 API。 | — |
| [NoticeMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/NoticeMapper.java) | 公告 MyBatis 接口：公告写入、按全部/部门/用户范围查询与维护。 | — |
| [NoticeService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/business/NoticeService.java) | 公告字段校验及 CRUD（无删除入口），按全部/部门/用户范围控制可见性；发布修改要求科技处或系统管理员。 | `list`、`get`、`create`、`update` |
| [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/ProjectAccessClient.java) | Feign 调 Project 单项/批量访问判定、专家授权同步与撤销接口。 | — |
| [ProjectSnapshotMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/ProjectSnapshotMapper.java) | 项目快照 MyBatis 接口：更新项目负责人/部门与来源版本（支持单调条件）、把当前 OPEN 的负责人任务改派给新负责人，以及更新生命周期阶段。 | — |
| [ReviewBatch](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/ReviewBatch.java) | 评审批次行：模块实例、节点、轮次、状态与创建人。 | — |
| [StructuredBusinessController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java) | 项目下 applications/contracts/acceptances 读写、achievements 和 acceptance-financial-settlements 查询/新增 API。 | — |
| [StructuredBusinessMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/StructuredBusinessMapper.java) | 结构化业务正文 MyBatis 接口：申请、合同、结题正文与成果、财务结算的读写。 | — |
| [StructuredBusinessService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/business/StructuredBusinessService.java) | 申请/合同/结题结构化正文、成果和财务结算持久化；校验当前业务节点操作人及关联模块/验收/状态记录，计算结余和执行率。 | `application`、`saveApplication`、`contract`、`saveContract`、`acceptance`、`saveAcceptance`、`achievements`、`addAchievement`、`settlements`、`addSettlement` |
| [WorkflowAssetCatalog](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowAssetCatalog.java) | 内置发布资产的静态目录：固定三个辅助标签版 BPMN 名称与 classpath 基础路径，并按名读取资产文本。 | `names`、`basePath`、`read` |
| [WorkflowAssetInitializer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/bootstrap/WorkflowAssetInitializer.java) | 启动安装三个内置 BPMN，刷新相同版本及节点/迁移配置；存在更高发布版时内置版保持 INACTIVE。 | `run` |
| [WorkflowBpmnParseResult](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/model/WorkflowBpmnParseResult.java) | BPMN 解析结果容器：流程配置、节点列表和迁移列表；内嵌五种配置 record。 | — |
| [WorkflowCatalogMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowCatalogMapper.java) | 目录查询 MyBatis 接口：按模块码取最新已发布定义的 BPMN XML（id 与 bpmnXml），或按定义 ID 取 BPMN XML，供运行期解析模型。 | — |
| [WorkflowClientProperties](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/config/properties/WorkflowClientProperties.java) | Workflow 的 sic.service-client.bootstrapSecret 配置。 | — |
| [WorkflowDefinitionCatalog](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowDefinitionCatalog.java) | 运行器内部读取当前已发布定义或按 definitionId 解析模型，返回 ActiveDefinition。 | `active`、`model` |
| [WorkflowDefinitionController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java) | 流程定义上传、资产列表/上传/发布、验证、发布、最新版本、详情、BPMN、节点及连线 API。 | — |
| [WorkflowDefinitionMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowDefinitionMapper.java) | 定义 MyBatis 接口：定义与版本状态写入、发布/停用切换、BPMN XML 读取，以及节点与连线配置的删除重建。 | — |
| [WorkflowDefinitionModelStore](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/definition/WorkflowDefinitionModelStore.java) | 定义模型写入：先删旧节点与连线，再插入节点/连线并序列化材料、单据与动作配置；以 MANDATORY 事务参与调用方事务。 | `persist` |
| [WorkflowDefinitionNavigator](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowDefinitionNavigator.java) | 按开始事件/当前节点与 eventType 选取连线，按条件穿越网关，限制遍历次数，返回目标节点和完整迁移路径。 | `start`、`transition`、`outgoing`、`initialTransition` |
| [WorkflowDefinitionService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/definition/WorkflowDefinitionService.java) | 定义生命周期管理与权限检查；上传 DRAFT、校验必需字段/重复节点/连线引用等，发布 ACTIVE 并停用同模块旧发布版本。 | `upload`、`listAssets`、`uploadAsset`、`publishAsset`、`validate`、`publish`、`list`、`latest`、`detail`、`bpmn`、`nodes`、`transitions` |
| [WorkflowDefinitionSource](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowDefinitionSource.java) | 定义源文件行：定义 ID 与 BPMN XML。 | — |
| [WorkflowDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/WorkflowDtos.java) | 流程定义上传、模块启动和状态迁移的请求 DTO 容器。 | — |
| [WorkflowInternalTokenProvider](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/auth/WorkflowInternalTokenProvider.java) | 缓存 workflow-service 内部 Token，提前 60 秒刷新并提供 Bearer Header。 | `authorization` |
| [WorkflowOperationPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/WorkflowOperationPolicy.java) | 当前 OPEN 任务操作判定：指派人/候选角色/专家分配本地筛选，再批量向 Project 校验精确模块轮次节点范围；系统管理员有特殊放行。 | `canOperate`、`exactCanOperate`、`operableModuleIds` |
| [WorkflowProjectAccessPolicy](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/WorkflowProjectAccessPolicy.java) | 以 projectAccessPolicy Bean 名实现项目/模块/节点表单读取；必要时根据当前专家分配同步缺失的 Project 授权。 | `canAccess`、`canAccessModule`、`canAccessForm` |
| [WorkflowProjectAuthorizationConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowProjectAuthorizationConsumer.java) | 消费项目授权事件；对 PROJECT_LEADER_CHANGED 更新项目负责人快照和当前未完成负责人任务。 | `consume` |
| [WorkflowProjectLifecycleConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowProjectLifecycleConsumer.java) | 消费生命周期事件，更新本地 project_snapshot.lifecycle_stage。 | `consume` |
| [WorkflowRuntimeController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowRuntimeController.java) | 模块启动、普通/legacy 状态迁移、runtime-view 与 workflow-workbench/items API。 | — |
| [WorkflowRuntimeMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowRuntimeMapper.java) | 运行器 MyBatis 接口：模块实例（可加行锁）与任务/状态历史的读写、序号推进、材料引用，以及工作台候选分页、评审批次与分配计数、缺失后继/负责人任务/专家访问范围恢复查询。 | — |
| [WorkflowRuntimeService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowRuntimeService.java) | 自建工作流运行核心：实例/任务/状态历史、序号行锁、轮次、材料预留引用、运行视图、待办；专家完成自动迁移、成功后继模块及三类恢复任务。 | `start`、`transition`、`runtimeView`、`workbench`、`autoTransitionAfterExpertReviewComplete`、`recoverMissingSuccessors`、`reconcileProjectLeaderTasks`、`recoverEndedExpertAccess` |
| [WorkflowSagaMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowSagaMapper.java) | 申请 Saga MyBatis 接口：按 sagaId 查询状态与模块、创建申请模块/项目快照/申请正文，并在补偿时按模块级联删除任务、状态、表单、材料引用、评审、验收与合同等关联数据。 | — |
| [WorkflowSagaState](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowSagaState.java) | 申请 Saga 的流程序号与状态投影：模块、项目、节点、状态、序号、轮次，以及一次迁移的前后状态、事件、结果和摘要。 | — |
| [WorkflowScopeMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowScopeMapper.java) | 批量判权 MyBatis 接口：按项目 ID 集合取 OPEN 任务与专家可评审模块、按模块解析所属项目，并按定义分页取关联项目 ID。 | — |
| [WorkflowSeedExporter](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/tools/seed/WorkflowSeedExporter.java) | 离线发布工具：用运行期同一解析器把内置 BPMN 导出为定义/节点/连线 SQL，`--missing-only` 只补缺失模块码。 | `main` |
| [WorkflowServiceApplication](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowServiceApplication.java) | Workflow 启动入口，启用 Feign 和配置属性扫描。 | `main` |
| [WorkflowServiceTokenClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/WorkflowServiceTokenClient.java) | Feign 调 IAM 获取内部 Token 和用户摘要，用于内部调用与专家资格检查。 | — |
| [WorkflowSnapshotMessagingConfiguration](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/config/WorkflowSnapshotMessagingConfiguration.java) | 声明 IAM 快照、项目生命周期、专家评审完成和项目授权队列，配置对应 routing key 与死信。 | — |
| [WorkflowTaskScope](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/entity/WorkflowTaskScope.java) | 批量判权用任务行：任务、模块、项目、模块类型、节点、指派人、候选角色与轮次。 | — |
| [WorkflowTransitionActionService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowTransitionActionService.java) | 校验内置动作键，按节点完成/流程结束的单据配置生成幂等命令；发布文档生成和项目生命周期事件。 | `execute`、`enqueueLifecycle` |
| [WorkflowUserSnapshotConsumer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/WorkflowUserSnapshotConsumer.java) | 消费 IAM 变更，按用户/角色/部门事件从 IAM 拉取摘要并更新 Workflow 的 user_snapshot。 | `synchronize` |
| [WorkflowUserSnapshotMapper](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dao/WorkflowUserSnapshotMapper.java) | 用户快照 MyBatis 接口：按 IAM 摘要 upsert 本地 user_snapshot 的账号、姓名、启用状态与来源版本（以 authVersion 单调保护）。 | — |
| [WorkflowWorkbenchController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowWorkbenchController.java) | GET /api/workflow-workbench/query 入口，解析视图、模块类型、关键字与分页参数并委托工作台查询服务。 | — |
| [WorkflowWorkbenchQueryService](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowWorkbenchQueryService.java) | 工作台视图查询：按可操作模块或 Project 批量访问判定过滤候选行，游标式分页并标注待办与可操作性。 | `query` |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [ExpertReviewDtos.AssignExpertRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/ExpertReviewDtos.java) | 专家 ID、姓名、机构和职称快照。 |
| [ExpertReviewDtos.CreateBatchRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/ExpertReviewDtos.java) | 模块/节点、评审类型标题、人数、阈值、规则和去极值设置。 |
| [ExpertReviewDtos.ScoreItem](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/ExpertReviewDtos.java) | 评分项目、权重、满分、得分和意见。 |
| [ExpertReviewDtos.SubmitScoreRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/ExpertReviewDtos.java) | 利益冲突、有效性、结论、意见和分项评分列表。 |
| [ModuleSuccessorPolicy.Completion](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/ModuleSuccessorPolicy.java) | 下个模块类型和项目生命周期阶段；hasSuccessor 判断是否自动创建后继。 |
| [WorkflowBpmnParseResult.DocumentConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/model/WorkflowBpmnParseResult.java) | 单据类型、生成时点、模板、快照结构/视图、输出材料类型及启用/必需标记。 |
| [WorkflowBpmnParseResult.MaterialRequirementConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/model/WorkflowBpmnParseResult.java) | 材料类型、时点、必需性、数量、用途、validatorKey、文件类型和大小配置。 |
| [WorkflowBpmnParseResult.NodeConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/model/WorkflowBpmnParseResult.java) | 节点 ID/名称/类型/状态/泳道、负责主体、候选角色、代录主体、材料要求和单据配置。 |
| [WorkflowBpmnParseResult.ProcessConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/model/WorkflowBpmnParseResult.java) | 流程 key/name/moduleType/version/status。 |
| [WorkflowBpmnParseResult.TransitionConfig](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/model/WorkflowBpmnParseResult.java) | 连线源/目标、事件/结果、条件类型/键/值/处理器/表达式及动作键列表。 |
| [WorkflowDefinitionCatalog.ActiveDefinition](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowDefinitionCatalog.java) | 定义 ID 与解析后的 BPMN 模型。 |
| [WorkflowDefinitionNavigator.ResolvedTarget](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/workflow/definition/WorkflowDefinitionNavigator.java) | 解析出的目标节点和穿越的迁移列表；initialTransition 取最初连线。 |
| [WorkflowDefinitionService.Validation](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/definition/WorkflowDefinitionService.java) | 校验错误/警告与角色/状态集合。 |
| [WorkflowDtos.StartModuleRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/WorkflowDtos.java) | 要启动的 moduleType。 |
| [WorkflowDtos.TransitionRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/WorkflowDtos.java) | eventType、expectedSeq、result/remark、材料版本 ID、formCode 和 nodeFormData；当前运行器主要使用 nodeFormData。 |
| [WorkflowDtos.UploadWorkflowDefinitionRequest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/WorkflowDtos.java) | bpmnXml 上传正文。 |
| [WorkflowOperationPolicy.RuntimeAccess](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/WorkflowOperationPolicy.java) | 运行视图批量判权结果：是否可读与是否可操作。 |
| [WorkflowServiceTokenClient.Token](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/WorkflowServiceTokenClient.java) | Workflow 接收到的内部 Token、过期时间、Token ID。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [ApplicationSagaCompensationTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/ApplicationSagaCompensationTest.java) | 验证重复补偿及仅清理目标 Saga 关联数据。 |
| [ModuleSuccessorPolicyTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/policy/ModuleSuccessorPolicyTest.java) | 验证只有成功终态映射后继模块。 |
| [NodeFormBusinessDataTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/NodeFormBusinessDataTest.java) | 验证模块业务正文叠加实时专家分配数据。 |
| [NodeFormCatalogTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/NodeFormCatalogTest.java) | 验证完整前端表单目录及内置 BPMN 用户任务覆盖。 |
| [NodeFormTransitionPersistenceTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/NodeFormTransitionPersistenceTest.java) | 验证表单与业务草稿同事务写入与回滚、跨轮次记录区分与旧节点键读取、表单或项目不匹配时拒绝写入。 |
| [WorkflowAssetRestartTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowAssetRestartTest.java) | 验证内置资产安装后再次启动保留节点 ID、已发布自定义节点名与 published 标记不被覆盖。 |
| [WorkflowContractBase](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/contract/WorkflowContractBase.java) | Workflow 契约测试基类：定义、运行、表单、业务数据和评审接口的 mock 场景。 |
| [WorkflowDefinitionAccessTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/policy/WorkflowDefinitionAccessTest.java) | 验证定义读取授权：按关联项目分批判定、拒绝不可访问项目或外来 decision，并覆盖后页可访问项目。 |
| [WorkflowDefinitionModelTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/service/runtime/WorkflowDefinitionModelTest.java) | 验证三个内置资产解析、申请条件网关及合同材料要求。 |
| [WorkflowExpectedSequenceConcurrencyTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowExpectedSequenceConcurrencyTest.java) | 验证同 expectedSeq 仅一请求提交、Outbox 失败回滚/释放材料、后继唯一创建及缺失后继恢复。 |
| [WorkflowOperationPolicyTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/policy/WorkflowOperationPolicyTest.java) | 验证指派/候选角色/任务状态/管理员操作策略及 AOP 操作人上下文。 |
| [WorkflowReleaseSeedTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowReleaseSeedTest.java) | 验证离线种子导出的全新与 `--missing-only` SQL 与 infra/mysql/seed 下两个脚本逐字一致。 |
| [WorkflowTestMapper](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/WorkflowTestMapper.java) | 测试夹具：按多个 XML 资源装配 SqlSessionFactory 并开启 callSettersOnNulls，供运行器与 Saga 测试取 Mapper。 |
| [WorkflowTransitionActionServiceTest](../services/workflow-service/src/test/java/com/amatrix/sic/workflow/service/runtime/WorkflowTransitionActionServiceTest.java) | 验证节点/流程单据命令及生命周期事件输出。 |

## services/material-service

主包：`com.amatrix.sic.material`。主源码 29 个，手写 Java 测试 6 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [GeneratedDocumentEventConsumer](../services/material-service/src/main/java/com/amatrix/sic/material/messaging/consumer/GeneratedDocumentEventConsumer.java) | 校验并去重 DOCUMENT_GENERATION_REQUESTED，调用单据生成服务。 | `consume` |
| [GeneratedDocumentMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/GeneratedDocumentMapper.java) | 生成单据 MyBatis 接口：以 INSERT IGNORE 幂等写入生成记录，并按 commandId 把材料版本 ID 回写到该生成单据行。 | — |
| [GeneratedDocumentRenderer](../services/material-service/src/main/java/com/amatrix/sic/material/service/document/GeneratedDocumentRenderer.java) | 把命令和 JSON 快照渲染为确定性的一页 PDF，手工写 PDF 结构；非 ASCII 转问号，无业务模板引擎。 | `render` |
| [GeneratedDocumentService](../services/material-service/src/main/java/com/amatrix/sic/material/service/document/GeneratedDocumentService.java) | 按 commandId 去重生成 PDF、哈希与 MinIO 对象；创建/复用输出材料类型及材料，生成新 ACTIVE 版本并关联 generated_document。 | `generate` |
| [InternalMaterialReservationController](../services/material-service/src/main/java/com/amatrix/sic/material/controller/internal/InternalMaterialReservationController.java) | 内部材料预留、确认、释放接口。 | — |
| [MaterialAccessPolicy](../services/material-service/src/main/java/com/amatrix/sic/material/policy/MaterialAccessPolicy.java) | 将操作人和 MATERIAL_READ/WRITE/DELETE 等操作发给 Project，使用内部 Token，按响应判定权限。 | `canAccess` |
| [MaterialApplicationService](../services/material-service/src/main/java/com/amatrix/sic/material/service/MaterialApplicationService.java) | 向 Project 校验读写权限；上传 MinIO 并事务保存版本/SHA-256/事件，失败尝试清理对象；列表/下载；禁止删除生成单据及已预留/确认版本，其他删除建立持久清理任务。 | `upload`、`persistUpload`、`list`、`download`、`delete` |
| [MaterialCleanupMetrics](../services/material-service/src/main/java/com/amatrix/sic/material/metrics/MaterialCleanupMetrics.java) | 暴露 PENDING 对象删除任务数量及其中已失败过的任务数量；没有材料预留数量指标。 | — |
| [MaterialClientProperties](../services/material-service/src/main/java/com/amatrix/sic/material/config/properties/MaterialClientProperties.java) | Material 的 sic.service-client.bootstrapSecret 配置。 | — |
| [MaterialController](../services/material-service/src/main/java/com/amatrix/sic/material/controller/MaterialController.java) | 材料版本 multipart 上传、项目材料列表、版本流式下载和删除 API；下载用 UTF-8 Content-Disposition 文件名。 | — |
| [MaterialMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/MaterialMapper.java) | 材料 MyBatis 接口：材料类型的确保与生成及查询、材料行加锁获取、版本号递增与旧版本 SUPERSEDED、版本插入/加锁读取/删除，以及生成单据与预留占用判定。 | — |
| [MaterialMessagingConfiguration](../services/material-service/src/main/java/com/amatrix/sic/material/config/MaterialMessagingConfiguration.java) | 声明材料预留确认和单据生成队列、Topic 绑定及死信路由。 | — |
| [MaterialProjectAccessClient](../services/material-service/src/main/java/com/amatrix/sic/material/client/MaterialProjectAccessClient.java) | Feign 调用 Project 的内部单项访问决策。 | — |
| [MaterialProperties](../services/material-service/src/main/java/com/amatrix/sic/material/config/properties/MaterialProperties.java) | sic.minio 的 endpoint/accessKey/secretKey/bucket 配置，bucket 默认 sic-materials。 | — |
| [MaterialReservationEventConsumer](../services/material-service/src/main/java/com/amatrix/sic/material/messaging/consumer/MaterialReservationEventConsumer.java) | 去重消费材料确认命令，读取 reservationId 并确认预留。 | `consume` |
| [MaterialReservationMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/MaterialReservationMapper.java) | 材料预留 MyBatis 接口：按 commandId 幂等查询、预留与明细插入、可选版本加锁、确认/释放/过期清理及状态读取。 | — |
| [MaterialReservationService](../services/material-service/src/main/java/com/amatrix/sic/material/service/reservation/MaterialReservationService.java) | 按 commandId 幂等预留版本，校验项目与必需类型；确认/释放/过期清理，支持对象未删除情况下的迟到确认。 | `reserve`、`confirm`、`release`、`cleanup` |
| [MaterialServiceApplication](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialServiceApplication.java) | Material 启动入口，启用 Feign 和配置属性扫描。 | `main` |
| [MaterialTokenClient](../services/material-service/src/main/java/com/amatrix/sic/material/client/MaterialTokenClient.java) | Feign 向 IAM 换取 material-service 内部 Token。 | — |
| [MaterialTokenProvider](../services/material-service/src/main/java/com/amatrix/sic/material/client/auth/MaterialTokenProvider.java) | 缓存内部 Token，过期前 60 秒刷新，提供 Bearer Header。 | `authorization` |
| [MaterialTypeRow](../services/material-service/src/main/java/com/amatrix/sic/material/entity/MaterialTypeRow.java) | 材料类型行：编码、名称、模块类型、允许扩展名与大小上限。 | — |
| [MaterialVersionRow](../services/material-service/src/main/java/com/amatrix/sic/material/entity/MaterialVersionRow.java) | 材料版本行：对象 key、原文件名、内容类型、大小与所属项目。 | — |
| [MinioConfiguration](../services/material-service/src/main/java/com/amatrix/sic/material/config/MinioConfiguration.java) | 根据 MaterialProperties 创建 MinioClient Bean。 | — |
| [MinioObjectStorage](../services/material-service/src/main/java/com/amatrix/sic/material/storage/MinioObjectStorage.java) | 初始化检查/创建 bucket，封装对象 put/get/remove 与存储异常。 | `ensureBucket`、`put`、`get`、`remove` |
| [ObjectDeletionJob](../services/material-service/src/main/java/com/amatrix/sic/material/entity/ObjectDeletionJob.java) | 待删除对象任务行：任务 ID、对象 key 与已尝试次数。 | — |
| [ObjectDeletionMapper](../services/material-service/src/main/java/com/amatrix/sic/material/dao/ObjectDeletionMapper.java) | 对象删除任务 MyBatis 接口：取 PENDING 任务、标记完成或失败退避，以及待处理与失败计数。 | — |
| [ObjectDeletionService](../services/material-service/src/main/java/com/amatrix/sic/material/service/cleanup/ObjectDeletionService.java) | 定时读取 PENDING 对象删除任务，删除 MinIO 对象后标记完成；异常保存错误、次数及指数退避时间，允许幂等重试。 | `deletePendingObjects` |
| [ReservationItemRow](../services/material-service/src/main/java/com/amatrix/sic/material/entity/ReservationItemRow.java) | 预留明细行：材料版本、所属项目与材料类型码。 | — |
| [ReservationRow](../services/material-service/src/main/java/com/amatrix/sic/material/entity/ReservationRow.java) | 预留行：预留 ID、状态与过期时间。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [MaterialApplicationService.Download](../services/material-service/src/main/java/com/amatrix/sic/material/service/MaterialApplicationService.java) | 下载资源、原文件名、内容类型和大小。 |
| [MaterialTokenClient.Token](../services/material-service/src/main/java/com/amatrix/sic/material/client/MaterialTokenClient.java) | 内部 Token、有效期、Token ID。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [GeneratedDocumentRendererTest](../services/material-service/src/test/java/com/amatrix/sic/material/service/document/GeneratedDocumentRendererTest.java) | 验证同一快照得到确定性 PDF。 |
| [MaterialContractBase](../services/material-service/src/test/java/com/amatrix/sic/material/contract/MaterialContractBase.java) | Material 契约测试基类：材料上传、列表、下载和删除的 mock 响应。 |
| [MaterialDeletionTransactionTest](../services/material-service/src/test/java/com/amatrix/sic/material/MaterialDeletionTransactionTest.java) | 验证 Outbox 失败回滚元数据且不删对象、成功事务创建持久清理任务。 |
| [MaterialReservationServiceTest](../services/material-service/src/test/java/com/amatrix/sic/material/service/reservation/MaterialReservationServiceTest.java) | 验证预留/确认幂等、缺少/已删除/跨项目材料拒绝、释放与迟到确认边界。 |
| [MaterialTestMapper](../services/material-service/src/test/java/com/amatrix/sic/material/MaterialTestMapper.java) | 测试夹具：按给定 DataSource 与 XML 资源装配 SqlSessionFactory 并开启 callSettersOnNulls，供材料 Mapper 测试取接口。 |
| [ObjectDeletionServiceTest](../services/material-service/src/test/java/com/amatrix/sic/material/service/cleanup/ObjectDeletionServiceTest.java) | 验证对象清理完成及失败后幂等重试。 |

## services/notification-service

主包：`com.amatrix.sic.notification`。主源码 9 个，手写 Java 测试 4 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [NotificationEventConsumers](../services/notification-service/src/main/java/com/amatrix/sic/notification/messaging/consumer/NotificationEventConsumers.java) | store 将模块状态事件（以及由 project.authorization、iam.changed 改写的工作台失效提示）去重持久化并发 notification.live；live 以实例专属 consumerName 去重并触发 SSE 投递。 | `store`、`live` |
| [NotificationMapper](../services/notification-service/src/main/java/com/amatrix/sic/notification/dao/NotificationMapper.java) | 通知 MyBatis 接口：插入待投递记录、按用户取 QUEUED（区分定向与广播）、按 eventId 标记投递或重试，以及待投递数与最老年龄计数。 | — |
| [NotificationMessagingConfiguration](../services/notification-service/src/main/java/com/amatrix/sic/notification/config/NotificationMessagingConfiguration.java) | 持久化模块事件队列与每实例匿名 live 队列的声明、绑定及持久队列死信配置。 | — |
| [NotificationMetrics](../services/notification-service/src/main/java/com/amatrix/sic/notification/metrics/NotificationMetrics.java) | 暴露待投递通知数量与最老 QUEUED 通知年龄。 | — |
| [NotificationServiceApplication](../services/notification-service/src/main/java/com/amatrix/sic/notification/NotificationServiceApplication.java) | Notification Spring Boot 启动入口。 | `main` |
| [PendingNotification](../services/notification-service/src/main/java/com/amatrix/sic/notification/entity/PendingNotification.java) | 待推送记录行：事件 ID、目标用户、事件名与 payload JSON。 | — |
| [PendingNotificationDeliveryService](../services/notification-service/src/main/java/com/amatrix/sic/notification/service/PendingNotificationDeliveryService.java) | 按 userId 定向或广播，发送成功后更新持久状态；重连按用户补发，定时重试 QUEUED 并记录失败次数。 | `deliver`、`replayTo`、`retryQueued` |
| [SseConnectionRegistry](../services/notification-service/src/main/java/com/amatrix/sic/notification/sse/SseConnectionRegistry.java) | 按用户维护线程安全 SSE 连接集合、CONNECTED、广播/定向发送、15 秒心跳和断开清理，并注册连接数 Gauge。 | `subscribe`、`broadcast`、`sendTo`、`heartbeat`、`size` |
| [SseController](../services/notification-service/src/main/java/com/amatrix/sic/notification/controller/SseController.java) | GET /api/sse/subscribe，从 JWT 识别用户，注册 SSE 连接并补发该用户待投递消息。 | — |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [PendingNotificationDeliveryService.Pending](../services/notification-service/src/main/java/com/amatrix/sic/notification/service/PendingNotificationDeliveryService.java) | 待推送事件 ID、用户、事件名和解析后的 payload。 |

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [NotificationContractBase](../services/notification-service/src/test/java/com/amatrix/sic/notification/contract/NotificationContractBase.java) | SSE 契约测试基类，构造订阅控制器及 mock 连接/补发服务。 |
| [NotificationEventConsumersTest](../services/notification-service/src/test/java/com/amatrix/sic/notification/messaging/consumer/NotificationEventConsumersTest.java) | 验证实例级 live 去重允许不同实例投递、相同实例重复事件被忽略。 |
| [NotificationTestMapper](../services/notification-service/src/test/java/com/amatrix/sic/notification/dao/NotificationTestMapper.java) | 测试夹具：装配指向 NotificationMapper.xml 的 SqlSessionFactory，供通知持久化测试取 Mapper。 |
| [PendingNotificationDeliveryServiceTest](../services/notification-service/src/test/java/com/amatrix/sic/notification/service/PendingNotificationDeliveryServiceTest.java) | 验证有连接才标记投递、重连仅补当前用户、定时重试和不可达计数。 |

## platform/integration-tests

此模块没有 src/main/java，作为跨模块测试工程。7 个测试全部是跨模块或真实基础设施验证；其中 6 个声明 `@Testcontainers(disabledWithoutDocker = true)`，Docker 不可用时跳过而不是失败，仅 FrontendApiSurfaceTest 不需要容器。

测试与契约基类：这些条目描述测试意图，不表示本次已执行。

| 源码类型 | 验证/支撑的功能 |
| --- | --- |
| [FrontendApiSurfaceTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 验证 CSV 前端调用对应准确 Controller、Gateway 首条路由和可执行契约/响应类型。 |
| [FullSchemaEquivalenceTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FullSchemaEquivalenceTest.java) | 以 Testcontainers MySQL 逐表比对 Flyway 迁移库与新 schema 库的列、索引、约束与元数据并核对签名，兼验专家授权亚秒内生效。 |
| [InfrastructureIntegrationTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/InfrastructureIntegrationTest.java) | Testcontainers 验证五服务 Flyway、Rabbit/Redis/MinIO、死信重放和旧库空基线迁移。 |
| [MysqlInitializationEncodingTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/MysqlInitializationEncodingTest.java) | 经容器入口执行 init-databases.sh 核对 35 个中文种子名称、描述与部门名，并验证修复脚本幂等且不动授权数据。 |
| [MysqlMapperIntegrationTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/MysqlMapperIntegrationTest.java) | 以真实 MySQL 验证各服务 Mapper 与服务：生成键与空字段、Inbox/Outbox 回滚与领取租约、并发评分与授权串行、材料预留与删除串行。 |
| [MysqlWorkbenchRegressionTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/MysqlWorkbenchRegressionTest.java) | 验证部门补充审计与发布失败回滚、无或停用部门不开事务、工作台分页鉴权、评审历史不露他人评分及财务就绪双重条件。 |
| [SessionRedisIntegrationTest](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/SessionRedisIntegrationTest.java) | 以真实 MySQL 与 Redis 验证发布失败持续栅栏与恢复、回滚仅释放本方栅栏、并发过时登录 409、批量发布与外来栅栏保留。 |

测试内部类型：

| 类型 | 功能 |
| --- | --- |
| [FrontendApiSurfaceTest.ApiContract](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | CSV 中的 provider、方法、路径、成功状态与响应类型。 |
| [FrontendApiSurfaceTest.GatewayRoute](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 网关服务归属及 Path pattern 列表，检查首条路由匹配。 |
| [FrontendApiSurfaceTest.HttpEndpoint](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 方法和规范化路径组成的端点标识，用于对比。 |
| [FrontendApiSurfaceTest.ProviderContract](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) | 可执行契约的端点、状态、Content-Type 和响应正文。 |
| [SessionRedisIntegrationTest.FaultingRedis](../platform/integration-tests/src/test/java/com/amatrix/sic/integration/SessionRedisIntegrationTest.java) | 故障注入用 Redis 模板：可控地让版本发布失败，验证栅栏与恢复。 |

## platform/migration-tool

主包：`com.amatrix.sic.migration`。主源码 1 个，手写 Java 测试 1 个。

| 源码类型 | 实现职责 | 关键公开方法 |
| --- | --- | --- |
| [LegacyFileMigrationTool](../platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java) | 一次性 prepare-files/verify-files CLI：防路径逃逸、核验旧哈希、上传确定性对象 key、建立清单，再下载逐字节哈希/大小/数量校验。 | `main` |

内部/局部数据类型：

| 类型 | 实现职责 |
| --- | --- |
| [LegacyFileMigrationTool.Config](../platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java) | 迁移用 JDBC、MinIO、bucket、uploads 根目录配置，从环境变量读取。 |
| [LegacyFileMigrationTool.LegacyFile](../platform/migration-tool/src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java) | 旧材料版本 ID、URL、预期哈希和文件名。 |

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
| `services/*/src/main/resources/db/migration` | 领域建表与结构升级的历史迁移脚本；运行服务已不执行 Flyway，完整结构以 infra/mysql/schema 为准，二者等价由 FullSchemaEquivalenceTest 校验。 |
| `platform/cloud-messaging/src/main/resources/db/common` | 各服务共用的 Outbox/Inbox 建表与领取重试字段升级。 |
| `services/workflow-service/src/main/resources/workflows/bpmn_auxiliary_tags_augmented` | 项目申请、纵向合同、项目结题三个辅助标签版 BPMN。 |
| `platform/config-server/src/main/resources/config` | 公共安全/消息/发现/监控参数及服务路由、数据库、端口。 |
| `platform/cloud-contracts/src/main/resources/contracts/frontend-api-surface.csv` | 前端接口面清单，供路由和契约覆盖验证。 |
| `services/*/src/test/resources/contracts` | 72 份 Spring Cloud Contract YAML（IAM 26、Project 19、Workflow 22、Material 4、Notification 1），描述请求、响应与状态；Workflow 另有 2 份 JSON 响应夹具。 |
| `infra/mysql/init-databases.sh` | 创建五业务 Schema 和独立账号；旧库 staging Schema 由切换流程创建。 |
| `infra/migration/import-legacy.sql` | 从 sic_legacy 向五领域 Schema 拆分导入旧业务数据。 |
| `infra/migration/verify-legacy-import.sql` | 导入数量、引用及流程序号等校验。 |
| `scripts/start_backend.py`、`scripts/start-backend.ps1` | 一键启动主程序及 PowerShell 包装。 |
| `scripts/verify_compose_e2e.py`、`scripts/verify-compose-e2e.ps1` | Compose 后端验收主程序及包装。 |
| `scripts/cutover.ps1` | 一次性单体到 Cloud 切换编排。 |
| `scripts/generate-jwt-keys.ps1` | RSA 密钥生成与输出。 |
| `scripts/tests/verify_history_retention.py` | 写入验证样本、经过 History 重启后核验数据库/文件保留的场景工具。 |
| `verification/*.json` | 仓库中保存的历史运行结果，不是本次调研重新运行的结果。 |

清单校验结果：242 个主源码文件、41 个主源码命名内部/局部类型、61 个手写 Java 测试文件（51 个含 @Test，10 个是 Mapper 与契约测试夹具）、13 个测试内部类型，以及启动与 Compose 验收相关的 6 个 Python 类，均有职责条目。
