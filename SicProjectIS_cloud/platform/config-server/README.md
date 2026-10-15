# config-server

集中配置服务，为 Gateway 和五个业务服务提供共享及各自的配置。当前采用 Spring Cloud Config 的 native 模式，从发布包内 `classpath:/config` 读取 YAML；默认端口 **8888**，无独立数据库。

**目录与配置分工**

启动入口为 [ConfigServerApplication](src/main/java/com/amatrix/sic/config/ConfigServerApplication.java)，通过 `@EnableConfigServer` 启用配置服务。Java 包为 `com.amatrix.sic.config`，当前没有额外 Controller 或 service 层。

| 资源 | 用途 |
| --- | --- |
| [本地 application.yaml](src/main/resources/application.yaml) | 服务名、8888 端口、native profile、classpath 搜索位置及健康端点。 |
| [config/application.yaml](src/main/resources/config/application.yaml) | 所有客户端的 Eureka、RabbitMQ、Redis、Hikari、MyBatis、Feign/断路器、监控、安全和消息配置。 |
| [config/gateway-service.yaml](src/main/resources/config/gateway-service.yaml) | 网关端口、路由、HTTP 超时及限流等。 |
| [config/iam-service.yaml](src/main/resources/config/iam-service.yaml) | IAM 数据源、身份/会话及初始化等配置。 |
| [config/project-service.yaml](src/main/resources/config/project-service.yaml) | Project 数据源及服务内部调用等配置。 |
| [config/workflow-service.yaml](src/main/resources/config/workflow-service.yaml) | Workflow 数据源及服务内部调用等配置。 |
| [config/material-service.yaml](src/main/resources/config/material-service.yaml) | Material 数据源、MinIO 和文件存储等配置。 |
| [config/notification-service.yaml](src/main/resources/config/notification-service.yaml) | Notification 数据源、通知与 SSE 等配置。 |

`resources/application.yaml` 控制配置服务自身，`resources/config/application.yaml` 是下发给客户端的共享配置，两者作用不同。当前不使用 Git 配置仓库。

**配置加载链路**

```text
客户端 application.yaml 声明服务名 + spring.config.import
    → 请求 Config Server 的 /{application}/{profile}
    → 合并 config/application.yaml 与 config/{application}.yaml
    → 客户端结合部署环境变量启动自动配置和业务组件
```

例如 `/workflow-service/default` 返回 Workflow 的配置环境，`/gateway-service/default` 返回 Gateway 的配置环境。工程的业务客户端通过 `CONFIG_IMPORT` 或 `CONFIG_SERVER_URL` 导入，Compose 使用 `http://config-server:8888`。optional 导入允许配置中心不可用时尝试启动，不代表缺少集中配置的客户端仍然具备完整能力。

共享配置中的主要变量包括 `EUREKA_URL`、`RABBITMQ_HOST/PORT/USERNAME/PASSWORD`、`REDIS_HOST/PORT/PASSWORD`、`DB_POOL_SIZE`、`JWT_ISSUER`、`JWT_AUDIENCE`、`JWK_SET_URI`。服务专属数据库及存储参数见对应 YAML 和 [环境变量示例](../../.env.example)。

当前共享配置默认使用两个 Eureka 地址；Feign 默认连接/读取超时为 1000/3000 毫秒并接入断路器。配置值应以当前 YAML 和部署覆盖为准。

**构建、运行与检查**

以下命令在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven：

```powershell
mvn -B -pl platform/config-server -am -DskipTests package
java -jar platform/config-server/target/config-server-1.0.0-SNAPSHOT-exec.jar
```

`SERVER_PORT` 可覆盖默认端口。检查本地进程：

```powershell
Invoke-RestMethod http://127.0.0.1:8888/actuator/health/readiness
```

Config Server 不依赖其他业务服务完成自身启动；配置中写有数据库/RabbitMQ 等地址，也不表示它会替客户端连接这些依赖。

Compose 服务名为 `config-server`，基础配置映射宿主机 8888；完整启动与冻结 JAR 挂载见 [工程 README](../../README.md) 和 [统一 JAR 部署指南](../../docs/unified-jar-startup.md)。classpath 配置随 JAR 发布：修改源码 YAML 后，需要重新打包并更新实际部署的包。当前没有实现将文件改动自动推送至全部客户端的机制。

本模块当前没有独立测试源码。就绪检查之外，还应确认客户端取得配置、注册成功且业务可用；相关入口见 [启动验收指南](../../docs/backend-startup-guide.md)。
