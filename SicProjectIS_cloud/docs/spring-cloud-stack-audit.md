# Spring Cloud 技术栈核对

调研日期：2026-10-10。依据当前 POM、配置、源码及离线 Maven 解析依赖核对。范围包括父工程与全部 14 个模块；未启动应用或对线上/部署环境做故障注入，外部配置覆盖的运行结果仍需部署验收。

## 1. 对所述五项的结论

| 描述 | 当前结论 | 判断依据 |
| --- | --- | --- |
| 服务注册与发现：Eureka | **符合** | 独立 Eureka Server；六个业务/网关服务引用 Eureka Client starter；公共配置提供注册中心地址 |
| 服务间通信主要基于 OpenFeign | **同步 HTTP 调用符合，需补充异步消息** | Project、Workflow、Material 共 8 个 Feign 接口；事件协作另用 RabbitMQ/Outbox，浏览器通知用 SSE |
| 负载均衡：Spring Cloud LoadBalancer | **符合，依赖由 Eureka starter 传递引入** | 解析依赖树有 starter/loadbalancer；Feign 按服务名调用，Gateway 使用 `lb://` 路由 |
| 网关：Spring Cloud Gateway | **符合，使用 Server WebFlux 版本** | `spring-cloud-starter-gateway-server-webflux`；集中配置中 11 条 `lb://` 路由 |
| 熔断与降级：Resilience4j | **目前不符合，未接入** | 解析依赖树没有 Resilience4j/CircuitBreaker starter，源码与配置没有启用、装饰器、Feign fallback 或 Gateway CircuitBreaker filter |

可用于当前项目说明的准确表述：

> 项目以 Eureka 实现服务注册与发现，以 OpenFeign 实现主要的同步服务调用，以 Spring Cloud LoadBalancer 完成按服务名的实例选择，以 Spring Cloud Gateway Server WebFlux 提供统一网关；异步业务事件使用 RabbitMQ、Outbox 与消费去重。Resilience4j 熔断与降级尚未接入。

“支持 Resilience4j 的框架”和“项目已经启用 Resilience4j”是两层结论。当前只能确认前者的可扩展性，不能将第五项写成已实现。

## 2. 版本与解析结果

[父 POM](../pom.xml) 声明：

| 项目 | 当前值 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.8 |
| Spring Cloud BOM | 2025.1.3 |
| Spring Cloud Contract | 5.0.3 |

全部模块的 `dependency:tree` 审计执行成功。与五项核对直接相关的解析版本为：

| 解析依赖 | 版本 |
| --- | --- |
| spring-cloud-starter-netflix-eureka-server/client | 5.0.2 |
| spring-cloud-starter-openfeign / spring-cloud-openfeign-core | 5.0.3 |
| spring-cloud-starter-loadbalancer / spring-cloud-loadbalancer | 5.0.3 |
| spring-cloud-starter-gateway-server-webflux | 5.0.3 |
| 两类 Resilience4j CircuitBreaker starter / io.github.resilience4j 组件 | 未出现 |

上表来自本地工程的解析结果，不是宣称这些版本是互联网最新版本。`dependencyManagement` 管理某个组件版本，也不代表该组件自动进入运行 classpath。

## 3. Eureka：有服务端，也有客户端

- [discovery-server POM](../platform/discovery-server/pom.xml) 引入 `spring-cloud-starter-netflix-eureka-server`。
- [DiscoveryServerApplication](../platform/discovery-server/src/main/java/com/amatrix/sic/discovery/DiscoveryServerApplication.java) 使用 `@EnableEurekaServer`。
- [服务端 application.yaml](../platform/discovery-server/src/main/resources/application.yaml) 使用 8761 端口，并关闭这个独立服务端自身的注册与拉取。
- 六个服务的 POM 均有 `spring-cloud-starter-netflix-eureka-client`：Gateway、IAM、Project、Workflow、Material、Notification。
- [集中公共配置](../platform/config-server/src/main/resources/config/application.yaml) 设置 `eureka.client.service-url.defaultZone`，默认指向 `http://discovery-server:8761/eureka/`，可由 `EUREKA_URL` 覆盖。

代码和配置组合支持“Eureka 注册与发现”的设计。实际实例是否注册成功、是否能发现多个实例，需要在启动后的 Eureka/Actuator 或调用验证中确认。

## 4. OpenFeign：8 个接口，集中在三个调用方

| 调用方 | 接口 | `@FeignClient` 目标 |
| --- | --- | --- |
| Project | `IamDirectoryClient` | iam-service，用户摘要/统计 |
| Project | `ServiceTokenClient` | iam-service，内部服务 Token |
| Project | `WorkflowSagaClient` | workflow-service，申请 Saga 创建/查询/补偿 |
| Workflow | `ProjectAccessClient` | project-service，项目访问判定及授权协作 |
| Workflow | `MaterialReservationClient` | material-service，材料预留/释放 |
| Workflow | `WorkflowServiceTokenClient` | iam-service，Token 与用户摘要 |
| Material | `MaterialProjectAccessClient` | project-service，材料操作所需项目访问判定 |
| Material | `MaterialTokenClient` | iam-service，内部服务 Token |

这三个服务都引用 OpenFeign starter，并在应用类使用 `@EnableFeignClients`。接口按 `name` 指定服务，不在注解中固定 `url`；同一目标的接口有不同 `contextId`。示例源码：

- [Project 的 IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/IamDirectoryClient.java)
- [Workflow 的 ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ProjectAccessClient.java)
- [Material 的 MaterialProjectAccessClient](../services/material-service/src/main/java/com/amatrix/sic/material/MaterialProjectAccessClient.java)

同步 HTTP 调用链为：Project → IAM/Workflow；Workflow → Project/Material/IAM；Material → Project/IAM。

“全部服务通信都基于 OpenFeign”不准确。业务状态、快照、授权生命周期、生成单据和通知还有 RabbitMQ 事件路径；Gateway 代理转发不是 Feign；Config Client 拉取配置、资源服务器读取 JWK 地址也有各自的 HTTP 机制。IAM/Notification 当前没有 Feign 客户端接口，公共安全库引入 `feign-core` 用于拦截器不能算这两个应用已经在调用远程服务。

官方文档说明了 Feign 的 Eureka 服务发现和 LoadBalancer 集成；为客户端指定固定 URL 则会改变服务名解析路径，因此后续分包应保留当前调用方式。[OpenFeign 官方说明](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)

## 5. LoadBalancer：不是缺依赖，而是间接依赖

服务 POM 未显式声明 `spring-cloud-starter-loadbalancer`，但解析树确认以下链条已经存在：

```text
spring-cloud-starter-netflix-eureka-client:5.0.2
└── spring-cloud-starter-loadbalancer:5.0.3
    └── spring-cloud-loadbalancer:5.0.3
```

Gateway、IAM、Project、Workflow、Material、Notification 都有该链条。业务同步 HTTP 的 Feign 客户端在服务名调用模式下使用负载均衡客户端；Gateway 使用响应式 LoadBalancer 解析 `lb://`。

这是客户端侧的实例选择能力。它不等于数据库、RabbitMQ 或 MinIO 本身做了负载均衡，也不证明当前 Compose 已部署多个业务实例。多实例分布、健康实例摘除和故障行为仍应通过部署测试确认。

后续可以为了清晰声明意图，在实际依赖该能力的模块中显式加入 LoadBalancer starter，由现有 BOM 管理版本；这是可选的依赖表达改进，当前无需据此判定能力缺失。分层重构也不需要复制 Demo 中的自定义 LoadBalancer 配置。

## 6. Gateway：响应式网关与服务名路由

[Gateway POM](../services/gateway-service/pom.xml) 引入 `spring-cloud-starter-gateway-server-webflux`。[集中 Gateway 配置](../platform/config-server/src/main/resources/config/gateway-service.yaml) 使用：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: project-routes
              uri: lb://project-service
              predicates:
                - Path=/api/projects/**
```

实际文件有 11 条路由，覆盖五个后端业务服务；具体子资源路由排在广泛的项目路由之前。配置中还包含连接超时、响应超时、Gateway 指标和 SSE streaming media type。

自定义组件承担会话鉴权、统一错误响应、Redis 限流、SSE Token 转发、TraceId 和日志；底层路由转发仍是 Spring Cloud Gateway。`lb://` 通过 ReactiveLoadBalancerClientFilter 把服务名解析成实际主机端口，符合官方说明。[Gateway Global Filters](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html)

网关出现 503、配置 HTTP 超时或返回统一 JSON 错误，不表示已经实现基于错误统计的熔断状态机。

## 7. Resilience4j：当前没有接入证据

当前五项中唯一明确缺失的是 Resilience4j 熔断与降级。核对分三层：

| 层次 | 实际结果 |
| --- | --- |
| Maven 解析运行依赖 | 所有模块均无 `spring-cloud-starter-circuitbreaker-resilience4j`、`spring-cloud-starter-circuitbreaker-reactor-resilience4j` 或 `io.github.resilience4j:*` |
| 源码接入 | 无 `@CircuitBreaker`/相关 Resilience4j 装饰器或 CircuitBreakerFactory 使用；8 个 Feign 接口均未配置 `fallback/fallbackFactory` |
| 配置启用 | 无 `spring.cloud.openfeign.circuitbreaker.enabled=true`、`resilience4j.*` 配置或 Gateway `CircuitBreaker` filter |

以下现有能力需要单独命名：

| 当前行为 | 真实含义 |
| --- | --- |
| Saga 查询恢复与补偿 | 跨服务业务一致性恢复，不是统计失败率后开启/关闭调用的熔断器 |
| Outbox 重发、消息监听重试与 DLQ | 消息可靠性与失败恢复，不是同步 HTTP 熔断 |
| Redis 限流错误时继续放行 | 限流组件的固定异常策略，源码为 `onErrorReturn(-1L)`；没有 Resilience4j 状态机 |
| Redis 会话校验错误时拒绝 | 鉴权失败关闭策略，源码返回拒绝的 AuthorizationDecision；不等于自动熔断 |
| `ExpertReviewController.removeFallback` | 专家评审移除接口的 POST 兼容入口，直接调用同类 remove；名字中的 Fallback 不是 Feign 降级 |

上述异常策略见 [GatewayRateLimitWebFilter](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayRateLimitWebFilter.java)、[SessionValidityAuthorizationManager](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/SessionValidityAuthorizationManager.java)、[ExpertReviewController](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewController.java)。

## 8. 后续补齐方案：与目录重构独立实施

如果五项是目标技术栈，需要新增实际接入和验证工作。下面是方案，不是当前实现状态。

| 范围 | 后续动作 |
| --- | --- |
| Project / Workflow / Material 的同步 Feign 调用 | 加 `spring-cloud-starter-circuitbreaker-resilience4j`，版本由现有 Spring Cloud BOM 管理；启用 `spring.cloud.openfeign.circuitbreaker.enabled=true`；按客户端方法制定异常分类与降级策略 |
| Gateway 的选定路由 | 加 `spring-cloud-starter-circuitbreaker-reactor-resilience4j`；对合适的路由配置 CircuitBreaker filter 与明确的错误响应入口 |
| IAM / Notification | 当前没有外呼 Feign，用例需要时再加；不为凑齐相同 POM 机械加入依赖 |
| 监控与验收 | 暴露所需指标，验证失败率/慢调用、OPEN/半开/恢复、fallback 行为和 TraceId；用停服、超时与持续错误注入验收 |

Spring Cloud CircuitBreaker 官方区分同步与响应式两个 starter；选择与当前应用一致的版本体系。[CircuitBreaker Starters](https://docs.spring.io/spring-cloud-circuitbreaker/reference/spring-cloud-circuitbreaker-resilience4j/starters.html)

Feign 的断路器包装需要 classpath 中有 CircuitBreaker，并开启相应配置；需要定制降级时再提供 Spring Bean 实现的 fallback 或 fallbackFactory。[Feign CircuitBreaker 与 fallback](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)

Gateway 的接入还需要路由过滤器配置，单纯加入响应式 starter 不会自动给所有路由加熔断；可用 `fallbackUri: forward:/...` 指向网关内部处理入口。[Gateway CircuitBreaker Filter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/circuitbreaker-filter-factory.html)

建议 fallback 类型放入 `client.fallback`，断路器装配放 `config`，Gateway 错误入口按 HTTP 职责归入 controller/error。它们是后续新增类，本次分层清单不把它们伪装成现有实现。

### 8.1 先定义用例失败语义

| 调用用途 | 建议失败行为 |
| --- | --- |
| 权限、项目访问判定 | 下游不可用时拒绝继续业务访问；不能 fallback 为允许。应区分真正无权和依赖不可用，明确错误码/503 等服务错误契约 |
| 服务 Token 获取 | 保留已有未过期 Token 的缓存边界；刷新失败不能伪造或返回已失效 Token |
| 用户展示摘要/概览 | 可评估有来源与时效标记的本地快照降级；不能把过期授权信息用于权限决定 |
| 创建申请、创建模块、授权、材料预留 | 禁止返回虚假的成功。保留 Saga/命令标识与幂等处理；超时后远端可能已提交，需查询结果再决定恢复/补偿 |
| SSE 长连接、文件上传/下载 | 单独设计超时和路由保护，避免普通短请求的 TimeLimiter 套用到长连接；文件流重放/重试需单独评估 |
| 通知、快照、生成单据事件 | 继续依靠 Outbox、消费事务与去重；HTTP 熔断不能代替事件可靠性 |

不要给所有客户端机械添加重试。读写操作、幂等性和 Saga 必须逐方法评估；熔断也不代表远程请求被撤回或远端事务回滚。建议先做一个具有明确失败契约的只读调用试点，再扩展到写用例与网关。

### 8.2 接入完成的验收标准

1. 依赖树出现对应 starter 和 Resilience4j 实现；应用上下文能创建实际使用的断路器。
2. 对指定调用制造持续失败，证明断路器进入 OPEN 并停止继续调用下游；恢复后能经过半开恢复。
3. 无权限、非法请求等业务错误与基础设施错误按约定处理；权限调用无法通过降级绕过。
4. 写调用超时不被当作确定失败或成功，幂等查询/Saga 恢复与补偿仍有效。
5. Gateway 的 JSON/SSE/文件响应契约保持，断路器状态与降级次数可观测。

## 9. 本次验证与复现

本次做了源码/POM/配置检索，并在云工程根目录运行以下命令，父工程与 14 个模块的依赖树均为 SUCCESS：

```powershell
mvn -o dependency:tree "-Dincludes=org.springframework.cloud:spring-cloud-starter-netflix-eureka-server,org.springframework.cloud:spring-cloud-starter-netflix-eureka-client,org.springframework.cloud:spring-cloud-starter-loadbalancer,org.springframework.cloud:spring-cloud-loadbalancer,org.springframework.cloud:spring-cloud-starter-openfeign,org.springframework.cloud:spring-cloud-openfeign-core,org.springframework.cloud:spring-cloud-starter-gateway-server-webflux,org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j,org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j,io.github.resilience4j:*"
```

`-o` 使用本地 Maven 缓存，适合本次已缓存的工程；换到未准备依赖的新环境需要先解析依赖。原始运行输出保存在 `target/cloud-stack-full-dependency-tree.txt`，属于构建产物，执行 clean 后可能删除；关键版本和依赖链已经记录于本文。

当前 Resilience4j 检索排除了 `target` 生成物，覆盖主源码、配置、POM，以及实际解析依赖。没有启动服务、变更部署、增加依赖或运行业务回归测试。上述结果证明当前仓库的设计与依赖状态，不代替运行环境验证。
