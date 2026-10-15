# iam-service

身份与权限服务，负责注册登录、用户与部门、角色权限、专家资格、JWT 签发及会话版本管理。默认容器内端口为 **9001**，拥有 MySQL Schema **sic_iam**。

## 包结构与职责

Java 根包为 `com.amatrix.sic.iam`，源码位于 [src/main/java/com/amatrix/sic/iam](src/main/java/com/amatrix/sic/iam)。

| 包 | 主要类 | 职责 |
| --- | --- | --- |
| 根包 | `IamServiceApplication` | 启动服务，扫描组件及配置属性。 |
| `bootstrap` | `BootstrapAdminInitializer` | 根据配置创建缺失的初始管理员，不重置已有账号密码。 |
| `config` | `IamSecurityConfig` | IAM 专属 Servlet 安全链、BCrypt 编码器和本地 RSA 公钥 JWT 解码器。 |
| `config.properties` | `IamJwtProperties`、`ServiceTokenProperties`、`BootstrapAdminProperties` | JWT、内部令牌兑换及管理员引导配置。 |
| `controller` | `AuthController`、`IamAdminController`、`ExpertQualificationController`、`JwkController` | 认证、身份管理、专家资格和 JWKS HTTP 入口。 |
| `controller.internal` | `InternalIdentityController` | 服务令牌兑换、用户/部门摘要及 IAM 概览统计。 |
| `service` | `AuthApplicationService`、`IamAdminService`、`ExpertQualificationService` | 认证、用户组织管理和资格审核的业务校验与事务。 |
| `service` | `SessionVersionService`、`IamOverviewService` | 数据库与 Redis 会话版本协调、恢复，以及身份概览查询。 |
| `security` | `JwtTokenIssuer`、`RsaKeyProvider` | RS256 JWT 签发、RSA 密钥加载/校验及公钥 kid。 |
| `dao` | `IamRepository` 及 Identity/Admin/Qualification/Session/Overview Mapper | 身份读取组合和按业务划分的 MyBatis 数据访问。 |
| `dto` | `IamDtos` | 认证及身份业务的数据结构。 |
| `entity` | `IdentityRow`、`UserVersionRow`、`AdminUserRow` 等 | Mapper 使用的持久化行模型和统计结果。 |

SQL 位于 [mapper/iam](src/main/resources/mapper/iam)，接口及 Mapper 的逐类职责见 [类职责清单](../../docs/class-responsibilities.md)。

## 接口与主要流程

| 接口族 | 用途 |
| --- | --- |
| `/api/auth/login`、`register`、`me`、`logout` | 登录、注册、当前身份及注销。 |
| `/api/admin/users/**`、`departments/**`、`roles/**`、`permissions` | 用户、部门、成员、角色和权限管理。 |
| `/api/expert-qualification/**`、`/api/admin/expert-qualification/**` | 专家资格申请、查询和分阶段审核。 |
| `/.well-known/jwks.json` | 发布 RSA 公钥，供网关及资源服务验签。 |
| `/internal/tokens/service` | 校验 `X-Service-Bootstrap-Secret` 后签发内部服务令牌。 |
| 其他 `/internal/**` 身份摘要接口 | 为业务服务提供用户、部门及概览数据，要求内部 scope。 |

登录流程为 `AuthController → AuthApplicationService → IamRepository/Mapper → BCrypt 校验 → 会话版本维护 → JwtTokenIssuer`。用户 JWT 携带 `jti`、`authVersion`（同时保留 `ver`）、角色、权限及用户/部门信息。

角色、权限、状态及相关身份变更需要同步授权版本。`SessionVersionService` 使用 Redis pending 栅栏协调数据库提交与版本发布，并定时恢复未完成状态；网关据此拒绝旧会话或暂时不可确认的会话。注销登记令牌撤销状态，不能只依赖前端删除 token。

身份变更通过公共 Outbox 发布 `iam.changed`，供 Project、Workflow、Notification 更新快照或发送刷新提示。跨服务快照由异步事件推进，不能视作实时共享同一张用户表。

## 配置与启动装配

| 配置来源/变量 | 作用 |
| --- | --- |
| [本地 application.yaml](src/main/resources/application.yaml)、`CONFIG_IMPORT` | 服务名与配置中心导入；本地默认配置中心地址为 `http://localhost:8888`。 |
| [iam-service.yaml](../../platform/config-server/src/main/resources/config/iam-service.yaml) | 9001 端口、数据库、JWT 和管理员初始化配置。 |
| `IAM_DB_URL`、`IAM_DB_USERNAME`、`IAM_DB_PASSWORD` | IAM 独立数据库连接。 |
| `JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY`、`JWT_ISSUER`、`JWT_AUDIENCE` | RSA 密钥对和 JWT 标识；有效期由 `JWT_EXPIRATION_MINUTES` 配置，默认 120 分钟。 |
| `SERVICE_TOKEN_BOOTSTRAP_SECRET` | 内部服务令牌兑换凭据。 |
| `BOOTSTRAP_ADMIN_USERNAME/PASSWORD/REAL_NAME` | 首次管理员引导，已有账号不会由此重设。 |
| [共享 application.yaml](../../platform/config-server/src/main/resources/config/application.yaml) | Eureka、Redis、RabbitMQ、连接池、MyBatis 和监控。 |

启动类扫描子包并绑定属性；`cloud-security` 提供公共身份上下文、方法安全等能力，IAM 自己定义安全链及本地公钥解码器；`cloud-messaging` 提供 Outbox/Inbox 和定时发布等公共组件。MyBatis 将 `dao` 中的 Mapper 与 XML SQL 绑定。

正常部署需要 Config Server、Eureka、MySQL、Redis、RabbitMQ。Gateway 及业务服务使用 IAM 的 JWKS；已有环境应保留原密钥对。生产初始化以 [数据库初始化与补丁说明](../../infra/mysql/patches/README.md) 和工程启动文档为准，`src/main/resources/db/migration` 中的 SQL 文件不表示启用了 Flyway 自动升级。

## 构建、启动与检查

以下命令在 `SicProjectIS_cloud` 根目录执行：

```powershell
mvn -B -pl services/iam-service -am test
mvn -B -pl services/iam-service -am -DskipTests package
```

可执行产物为 `services/iam-service/target/iam-service-1.0.0-SNAPSHOT-exec.jar`。Docker 内构建和冻结 JAR 只读挂载方式见 [工程 README](../../README.md)，容器内执行 `java -jar /application/application.jar`。

已有冻结 JAR 环境示例（沿用实际环境的项目名和配置）：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600 iam-service
```

容器检查 `/actuator/health/readiness`；应用启动后还需验证登录、JWKS 和注销失效。[测试目录](src/test/java/com/amatrix/sic/iam) 覆盖 JWT 签发、安全规则、登录、部门管理及会话版本，认证契约位于 [src/test/resources/contracts](src/test/resources/contracts)。

完整启动/验收见 [启动指南](../../docs/backend-startup-guide.md)，扩容见 [多实例说明](../../docs/multi-instance-startup.md)。重新打包不会自动替换运行中的冻结 JAR；数据库/Redis 提交协调和副本行为需真实基础设施验收。
