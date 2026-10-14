# gateway-service

系统统一 API 入口，负责路由转发、JWT 认证、Redis 会话校验、限流、请求追踪和 SSE 令牌适配。服务使用 Spring Cloud Gateway WebFlux；默认容器内端口为 **8080**，无独立业务数据库。

## 包结构与职责

Java 根包为 `com.amatrix.sic.gateway`，源码位于 [src/main/java/com/amatrix/sic/gateway](src/main/java/com/amatrix/sic/gateway)。

| 包 | 主要类 | 职责 |
| --- | --- | --- |
| 根包 | `GatewayServiceApplication` | Spring Boot 启动入口，扫描组件与配置属性。 |
| `config` | `GatewaySecurityConfig` | 注册 JWT 解码器和 WebFlux 安全链，配置访问规则及认证错误响应。 |
| `config.properties` | `GatewaySecurityProperties`、`GatewayRateLimitProperties` | 分别绑定 `sic.security` 和 `sic.gateway.rate-limit`。 |
| `filter` | `TraceIdWebFilter` | 生成或校验 `X-Trace-Id`，传递给下游并写回响应。 |
| `filter` | `SseTokenRelayWebFilter` | 将 SSE 订阅 URL 的 `token` 转为 Bearer 头，并删除转发 URL 中的令牌。 |
| `filter` | `GatewayRateLimitWebFilter` | 按来源 IP 和秒级窗口在 Redis 中计数，超限返回 429。 |
| `filter` | `GatewayRequestLoggingFilter` | 记录用户、路径、状态、耗时及 trace/saga/event 标识。 |
| `security` | `SessionValidityAuthorizationManager` | 原子检查 Redis 中的令牌撤销、用户会话版本和 pending 状态。 |
| `error` | `GatewayJsonErrorHandler`、`GatewayErrorWriter` | 将网关异常、安全拒绝及限流错误统一写为 `{success,data,message}` JSON。 |

## 请求处理与路由

正常受保护 API 的处理顺序为：

```text
客户端 → TraceId → SSE token 适配 → 限流 → JWT 认证 → Redis 会话授权
       → 匹配路由 → Gateway 日志及框架过滤链 → 负载均衡 → 业务服务
```

前三个过滤器实现 WebFlux `WebFilter`；请求日志实现 Gateway `GlobalFilter`，在路由匹配后执行。两类过滤器各自在对应过滤链中排序，不能跨链直接比较 `Order` 数值。

JWT 验证签名、时间、issuer 和 audience；会话授权进一步检查 `jti`、`subject`、`authVersion`（兼容 `ver`），以及 Redis 的 `sic:jwt:revoked:*`、`sic:jwt:version:*`、`sic:jwt:pending:*`。无效会话返回 401；会话依赖故障或 pending 状态返回 503。限流 Redis 计数失败时则继续放行。

`POST /api/auth/login`、`POST /api/auth/register` 允许匿名访问，`/internal/**` 拒绝从网关访问，其他 `/api/**` 使用会话授权。监控仅放行配置的 health、info、prometheus 端点。实际放行规则以 [GatewaySecurityConfig](src/main/java/com/amatrix/sic/gateway/config/GatewaySecurityConfig.java) 为准；集中配置的 `sic.gateway.public-paths` 当前没有对应读取逻辑。

路由位于 Config Server 的 [gateway-service.yaml](../../platform/config-server/src/main/resources/config/gateway-service.yaml)，不在 Java 类中硬编码。

| 路径族 | 下游 |
| --- | --- |
| `/api/auth/**`、专家资格及用户/部门/角色管理 | `lb://iam-service` |
| `/api/projects/*/materials/**`、`/api/material-versions/**` | `lb://material-service` |
| 项目下的申请、合同、验收等子资源，以及流程、节点表单、专家评审和工作台 | `lb://workflow-service` |
| `/api/sse/**` | `lb://notification-service` |
| `/api/project-applications/**`、项目管理及其余 `/api/projects/**` | `lb://project-service` |

`lb://` 通过 Eureka 和 LoadBalancer 选择实例。具体子资源路由排在通用项目路由之前；当前不做 `StripPrefix` 或 `RewritePath`，下游收到原 `/api/...` 路径。

## 配置与启动装配

- [本地 application.yaml](src/main/resources/application.yaml)：声明服务名，通过 `CONFIG_SERVER_URL` 导入配置中心，声明 JWT issuer、audience 和 `JWK_SET_URI`。
- [服务集中配置](../../platform/config-server/src/main/resources/config/gateway-service.yaml)：8080 端口、路由、连接/响应超时、SSE 媒体类型与限流；默认启用限流，每来源 IP 每秒 40 次。
- [共享集中配置](../../platform/config-server/src/main/resources/config/application.yaml)：Eureka、Redis 和监控；Redis 使用 `REDIS_HOST`、`REDIS_PASSWORD` 等变量。

启动类的 `@SpringBootApplication` 扫描子包，`@ConfigurationPropertiesScan` 绑定属性；Spring 自动接入 `WebFilter`、`GlobalFilter`、异常处理器及安全链。Eureka Client、Config Client 和响应式 Redis 由依赖自动装配。网关的主要运行依赖为 Config Server、Eureka、Redis、IAM 公钥端点及目标业务服务。

配置导入使用 `optional:configserver:`，配置中心不可用时仍可能尝试启动，但缺少集中路由配置的进程不具备完整转发能力。

## 构建、挂载与检查

以下命令在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven。

```powershell
mvn -B -pl services/gateway-service -am test
mvn -B -pl services/gateway-service -am -DskipTests package
```

可执行产物为 `services/gateway-service/target/gateway-service-1.0.0-SNAPSHOT-exec.jar`。基础 [Dockerfile](../../Dockerfile) 将产物复制进镜像；冻结 JAR 模式则通过 Compose override 只读挂载到 `/application/application.jar`，执行 `java -jar`。首次准备及挂载配置生成见 [工程 README](../../README.md)。

已有冻结 JAR 环境可参照以下命令，项目名、env 和 override 必须对应实际部署：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600 gateway-service
```

[基础 Compose](../../docker-compose.yml) 等待配置中心、Eureka、Redis 和五个业务服务健康后启动网关，检查 `/actuator/health/readiness`。默认宿主机入口为 `http://127.0.0.1:8080`，发布端口可由 `GATEWAY_PORT` 调整。冻结包不会随源码编译自动更新，需切换挂载源并重新创建容器。

[测试目录](src/test/java/com/amatrix/sic/gateway) 覆盖安全规则、会话有效性、JSON 错误和限流、SSE token 适配及上传下载透传。真实跨服务路由和注册传播需结合 [启动验收](../../docs/backend-startup-guide.md)；单元测试不替代部署验收。多实例部署见 [多实例说明](../../docs/multi-instance-startup.md)，该启动器将 Gateway 保持为单实例。
