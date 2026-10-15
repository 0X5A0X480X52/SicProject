# discovery-server

Eureka 注册中心，供 Gateway 和业务服务注册实例、拉取注册表，再由客户端 LoadBalancer 选择目标实例。默认监听 **8761**，不保存业务数据库；注册与发现由 Eureka 框架实现。

**目录与运行模式**

启动类为 [DiscoveryServerApplication](src/main/java/com/amatrix/sic/discovery/DiscoveryServerApplication.java)，位于 `com.amatrix.sic.discovery`，使用 `@EnableEurekaServer`。

| 配置 | 运行方式 |
| --- | --- |
| [application.yaml](src/main/resources/application.yaml) | 单独运行时的 standalone 模式；不向其他节点注册、不拉取注册表，关闭自我保护。 |
| [application-ha.yaml](src/main/resources/application-ha.yaml) | 激活 ha profile 后使用独立 hostname/instance-id，开启注册、拉取及自我保护，通过 Peer 地址互相同步。 |
| [主 Compose](../../docker-compose.yml) | discovery-server 与 discovery-peer 两个服务复用本应用，都启用 ha profile；容器内端口均为 8761。 |

```text
Gateway / IAM / Project / Workflow / Material / Notification
        → discovery-server:8761/eureka/
        → discovery-peer:8761/eureka/
两个注册中心通过 Peer 地址互相注册、同步注册表
```

业务请求在发现后直接发给业务实例，注册中心不转发业务 HTTP。双节点指两个明确的 Compose 服务名，不是对单个服务执行 `--scale`。

**关键配置**

| 变量/属性 | 用途 |
| --- | --- |
| `SERVER_PORT` | 默认 8761。 |
| `SPRING_PROFILES_ACTIVE=ha` | 启用双节点配置；单独运行 JAR 时默认不启用。 |
| `EUREKA_HOSTNAME` | ha 节点名，必须能被其他节点解析，并保证各节点不同。 |
| `EUREKA_PEER_URL` | ha 对端注册地址，例如 `http://discovery-peer:8761/eureka/`。 |
| 客户端 `EUREKA_URL` | 由共享集中配置读取，通常包含两个注册中心地址。 |

ha 配置的 `eureka.client.timeout` 将连接及连接池等待设置为 2000 毫秒、socket 等待设置为 5000 毫秒。

基础 Compose 将两个节点分别映射至宿主机 8761、8762。统一 JAR 部署生成的 override 则按 `EUREKA_UI_ENABLED` 管理入口：默认隐藏宿主机端口，开启时默认绑定 127.0.0.1，端口由 `EUREKA_UI_PORT_A/B` 设置。该开关由部署脚本处理，并非本 Java 应用直接读取的配置；实际映射取决于使用的 Compose 文件组合。

**构建、运行与检查**

在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven：

```powershell
mvn -B -pl platform/discovery-server -am -DskipTests package
java -jar platform/discovery-server/target/discovery-server-1.0.0-SNAPSHOT-exec.jar
```

以上以 standalone 模式运行。默认宿主机地址为 `http://127.0.0.1:8761`，可以查看管理页面；就绪检查为：

```powershell
Invoke-RestMethod http://127.0.0.1:8761/actuator/health/readiness
```

双节点应通过已有部署配置启动；完整参数、冻结 JAR 更新和故障验证见 [Eureka HA 指南](../../docs/eureka-ha.md)、[统一 JAR 部署指南](../../docs/unified-jar-startup.md)。常规验收应同时检查两个节点健康、注册表收敛，以及注册实例与实际容器的地址和端口对应。

本模块当前没有独立 Java 测试源码。仓库的 [verify_eureka_ha.py](../../scripts/verify_eureka_ha.py) 可验证 Peer 同步、单节点故障后的新客户端注册/发现、业务请求和恢复；它会临时停止一个注册中心，应按 HA 指南在指定测试环境执行。

同一 Docker 引擎中的双节点覆盖单个进程/容器故障，不覆盖宿主机故障。ha 自我保护期间注册表可能保留失联实例，注册记录需要结合真实健康与调用结果判断。其他依赖是否高可用需分别评估。
