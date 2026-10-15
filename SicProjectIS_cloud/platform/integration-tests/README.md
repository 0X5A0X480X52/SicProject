# integration-tests

跨模块集成与回归测试模块，验证接口契约、真实 MySQL Mapper/事务、数据库初始化、Redis 会话屏障、RabbitMQ 死信、MinIO 和多实例竞争。本模块仅有测试源码，没有应用入口、业务端口或常驻容器。

**测试目录与覆盖范围**

Java 包为 `com.amatrix.sic.integration`，当前共有 8 个测试类，位于 [src/test/java/com/amatrix/sic/integration](src/test/java/com/amatrix/sic/integration)。

| 测试类 | 主要验证内容 |
| --- | --- |
| `FrontendApiSurfaceTest` | CSV 接口清单与 Controller 的准确方法/路径、Gateway 首条路由的服务归属、可执行 Spring Cloud Contract 和响应类型。 |
| `InfrastructureIntegrationTest` | 五服务全量 SQL 初始化、RabbitMQ/Redis/MinIO 连通、真实死信重放和旧库切换 SQL 导入。 |
| `FullSchemaEquivalenceTest` | 历史 Flyway 迁移与发行全量 SQL 的列/索引/约束/表元数据等价，核对签名及专家授权亚秒生效。 |
| `MysqlInitializationEncodingTest` | 经容器入口执行初始化脚本，检查中文种子编码和修复脚本幂等、授权数据保留。 |
| `MysqlMapperIntegrationTest` | 真实 MySQL 下的生成键/空值、Inbox/Outbox 事务与租约、身份授权、并发评分、材料预留/删除等。 |
| `MysqlWorkbenchRegressionTest` | 部门补充与审计回滚、工作台分页授权、个人评审历史隔离、财务角色及项目授权双重约束。 |
| `SessionRedisIntegrationTest` | 真实 MySQL + Redis 下会话版本发布、pending 屏障、事务回滚及过时登录并发。 |
| `MysqlReplicaSafetyTest` | 两个独立服务对象通过不同数据库连接竞争，验证 Saga 恢复、管理员初始化和材料版本并发。 |

测试通过 POM 的 test 依赖复用业务服务和平台类。服务普通 JAR 用于测试类路径，`-exec.jar` 用于部署，两类产物用途不同。

**环境和测试数据**

需要 JDK 21、Maven，以及可被 Testcontainers 访问的 Docker。基础设施由测试创建，镜像包括 MySQL 8.4、RabbitMQ 4.1-management、Redis 8.2-alpine 及源码固定版本的 MinIO；镜像拉取需要可用的仓库访问或本地缓存。

FrontendApiSurfaceTest 无需 Docker，其余 7 类使用容器。6 类声明 `@Testcontainers(disabledWithoutDocker=true)`，Docker 不可用时会跳过；MysqlReplicaSafetyTest 使用普通 `@Testcontainers`，不能假定它也会跳过。应检查 Surefire 的实际执行和 skipped 数量。

测试依赖完整仓库中的 Controller、契约 YAML、Gateway 配置、[全量 schema](../../infra/mysql/schema)、[种子](../../infra/mysql/seed)、[旧库迁移 SQL](../../infra/migration) 及部分历史 migration 资源。多个测试按模块相对路径定位文件，建议从工程根目录通过 Maven reactor 执行，不要只复制测试 JAR 到其他目录。

FullSchemaEquivalenceTest 在隔离测试库中调用 Flyway，用于比较历史迁移结果；这不表示线上业务服务仍通过 Flyway 初始化数据库。实际部署初始化入口见 [MySQL 基础设施目录](../../infra/mysql)。

**运行方式**

以下命令均在 `SicProjectIS_cloud` 根目录执行：

```powershell
# 编译依赖模块并执行其测试及本模块集成测试
mvn -B -pl platform/integration-tests -am test

# 仅选择无需 Docker 的接口覆盖测试，允许上游模块没有同名测试
mvn -B -pl platform/integration-tests -am '-Dtest=FrontendApiSurfaceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

# 选择多实例数据库竞争测试，需要 Docker
mvn -B -pl platform/integration-tests -am '-Dtest=MysqlReplicaSafetyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

报告位于 `platform/integration-tests/target/surefire-reports`；`-am` 带出的其他模块报告保存在各自 target 下。完整发行验证还包含前端构建、Compose HTTP 业务流程和故障测试，见 [CI 工作流](../../../.github/workflows/cloud-verification.yml) 与 [验证指南](../../docs/verification-guide.md)。

本模块验证了跨模块代码与真实基础设施行为，但未将全部 Java 服务作为独立 HTTP 进程启动。MysqlReplicaSafetyTest 的双服务对象竞争也不同于完整多容器负载均衡验收；后者见 [多实例启动与验收](../../docs/multi-instance-startup.md)。
