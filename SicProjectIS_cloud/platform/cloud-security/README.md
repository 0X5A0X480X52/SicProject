# cloud-security

Servlet 业务服务共用的安全与请求上下文库，提供 JWT 资源服务器装配、方法权限注解、ActorContext 解析、统一错误响应和 Feign 关联标识传播。本模块产出普通 JAR，没有独立端口或数据库。

**源码与职责**

Java 根包为 `com.amatrix.sic.security`，当前类集中在同一包中。

| 主要类/资源 | 职责 |
| --- | --- |
| `CloudResourceServerAutoConfiguration` | Servlet 安全链、JWT decoder/converter、方法安全及公共组件装配。 |
| `CloudSecurityProperties`、`AudienceValidator` | 绑定 issuer/audience/JWK 地址，校验目标 audience。 |
| `JwtAuthoritiesConverter` | 将 roles、permissions、scope 转为 ROLE_、PERM_、SCOPE_ 权限。 |
| `ActorContextFactory`、`ActorContextResolver` | 从 JWT / Authentication 读取用户、部门、权限及 authVersion，兼容旧 ver。 |
| `CanAccessProject`、`CanModifyProject`、`CanAccessModule`、`CanReadNodeForm`、`CanOperateModule` | 声明项目、表单、模块读取及当前任务操作约束。 |
| `JsonSecurityHandlers`、`ApiExceptionHandler` | 将认证、权限、校验、业务及依赖异常转换为统一 API JSON。 |
| `RequestLoggingContextFilter` | 校验或生成 Trace ID，建立 Trace/Saga/Event/User MDC，响应回传 Trace ID，结束时恢复上下文。 |
| `FeignTracePropagationAutoConfiguration` | 转发已经规范化的 X-Trace-Id、X-Saga-Id、X-Event-Id。 |
| `META-INF/spring/...AutoConfiguration.imports` | 向 Spring Boot 声明上述两个自动配置入口。 |

见 [Java 源码](src/main/java/com/amatrix/sic/security) 和 [自动配置注册文件](src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)。

**认证、授权与上下文链路**

```text
Bearer JWT → 签名/时间/issuer/audience 校验 → Authority 转换
           → HTTP 访问规则 → 方法权限策略 → 业务方法
请求关联标识 → Request Attribute + MDC → Feign 关联标识头
```

默认安全链允许 health、info、prometheus，拒绝其他 actuator；`/internal/**` 要求 `SCOPE_internal`，`/api/**` 要求登录，其他路径拒绝。使用无状态会话，关闭 CSRF 和 HTTP Basic。服务可以提供自定义 SecurityFilterChain 或相应 Bean 替换默认装配，实际规则应同时核对服务自己的安全配置。

权限元注解依赖使用方提供名为 `projectAccessPolicy` 或 `workflowOperationPolicy` 的 Bean，例如 `CanOperateModule` 调用 `workflowOperationPolicy.canOperate(actor, moduleInstanceId)`。注解自身不保存授权数据，也不替业务服务实现项目范围判定；方法参数名称需要保留，父 POM 已设置 `maven.compiler.parameters=true`。

Feign 拦截器只传播三个关联标识，没有自动复制 Authorization/Cookie。内部服务令牌仍由各服务的客户端令牌组件取得和设置。Redis token 撤销、授权版本和 pending 屏障检查位于 [gateway-service](../../services/gateway-service/README.md)，不在本库中。

**配置与装配条件**

| 配置 | 默认值/要求 |
| --- | --- |
| `sic.security.issuer` | `sic-iam`。 |
| `sic.security.audience` | `sic-api`。 |
| `sic.security.jwk-set-uri` | 默认 decoder 必填；缺失会阻止装配。 |
| `sic.observability.load-balancer-evidence-enabled` | 默认 false；开启时打印服务/实例/trace/路径/状态/耗时证据。 |

公共配置见 [Config Server application.yaml](../config-server/src/main/resources/config/application.yaml)，通常用 `JWT_ISSUER`、`JWT_AUDIENCE`、`JWK_SET_URI` 设置。

资源服务器自动配置要求 Servlet Web 应用及安全类存在；Feign 配置还要求 RequestInterceptor 在类路径。WebFlux Gateway 使用自己的安全实现。本模块不签发 token、不创建用户、不管理会话存储。

**构建与验证**

以下命令在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven：

```powershell
mvn -B -pl platform/cloud-security -am test
mvn -B -pl platform/cloud-security -am -DskipTests package
```

普通 JAR 为 `platform/cloud-security/target/cloud-security-1.0.0-SNAPSHOT.jar`，由服务作为依赖加载，无需单独 `java -jar`。

[测试目录](src/test/java/com/amatrix/sic/security) 覆盖资源服务器规则、Actor 解析、权限元注解及请求关联标识传播。测试不代替服务级项目权限和网关会话验收；系统验证入口见 [验证指南](../../docs/verification-guide.md)。
