# notification-service

事件通知与 SSE 服务，将工作流、项目授权及身份变更转换为持久化通知和前端刷新提示。默认容器内端口为 **9005**，拥有 MySQL Schema **sic_notification**。HTTP 层使用 Spring MVC `SseEmitter`。

## 包结构与职责

Java 根包为 `com.amatrix.sic.notification`，源码位于 [src/main/java/com/amatrix/sic/notification](src/main/java/com/amatrix/sic/notification)。

| 包 | 主要类 | 职责 |
| --- | --- | --- |
| 根包 | `NotificationServiceApplication` | Spring Boot 启动和组件扫描。 |
| `config` | `NotificationMessagingConfiguration` | 声明持久事件队列、死信路由及每实例匿名 live 队列。 |
| `controller` | `SseController` | 从已验证 JWT 识别用户，建立连接并补发该用户待投递消息。 |
| `messaging.consumer` | `NotificationEventConsumers` | `store` 去重持久化业务事件并发出 live 事件，`live` 触发实例内投递。 |
| `service` | `PendingNotificationDeliveryService` | 定向/广播投递、用户重连补发、定时重试和持久投递状态。 |
| `sse` | `SseConnectionRegistry` | 按用户管理进程内连接，CONNECTED、发送、15 秒心跳和断开清理。 |
| `dao` | `NotificationMapper` | 通知入库、待投递查询、成功/重试状态更新。 |
| `entity` | `PendingNotification` | 待投递通知行模型。 |
| `metrics` | `NotificationMetrics` | 待投递通知数量及最老通知年龄；连接数 Gauge 由连接注册表维护。 |

SQL 位于 [mapper/notification](src/main/resources/mapper/notification)。连接注册和持久化投递逻辑分开，避免把进程内连接表当作可靠通知存储。

## 事件与 SSE 流程

```text
workflow.module-state-changed / project.authorization / iam.changed
  → 持久事件队列 → Inbox 去重 → 通知入库 + Outbox
  → notification.live → 每实例匿名队列 → 本实例 SSE 连接
```

持久队列 `notification.module-events.q` 由服务副本共享消费；`notification.live` 绑定每个实例的匿名队列，让持有不同用户连接的副本都能收到实时提示。live 消费使用实例专属 consumerName 去重。

模块事件保存原提示数据，其他输入事件转换为 `WORKBENCH_INVALIDATED` 提示。payload 带 `userId` 时定向发送，否则广播。推送只提供重新查询所需信息，业务状态和权限仍通过业务 API 确认。

外部订阅入口为 **`GET /api/sse/subscribe`**，返回 `text/event-stream`，不使用普通 `ApiResponse` 包装。通过 Gateway 时可使用查询参数 `token`，由网关转换为 Bearer 头并移除转发 URL 中的 token；Notification 本身从 JWT subject 识别用户。

连接建立时发送 `CONNECTED` 并尝试补发用户待投递通知；注册表每 15 秒发送 `HEARTBEAT`。连接保存在当前进程内，客户端断开或实例重启后需重新连接。投递成功表示通道接受消息，不表示用户已阅读，也不保证恢复全部历史事件。

## 配置与启动装配

- [本地 application.yaml](src/main/resources/application.yaml)：服务名、`CONFIG_IMPORT` 和 MyBatis XML 路径。
- [notification-service.yaml](../../platform/config-server/src/main/resources/config/notification-service.yaml)：9005 端口与 `NOTIFICATION_DB_URL/USERNAME/PASSWORD`。
- [共享 application.yaml](../../platform/config-server/src/main/resources/config/application.yaml)：Eureka、RabbitMQ、JWT 公钥地址、连接池、MyBatis 和监控。
- `sic.notification.delivery-delay-ms`：待投递通知重试周期，默认 5000 ms。

启动入口扫描组件；公共 `cloud-security` 自动装配 Servlet JWT 安全和身份上下文，`cloud-messaging` 装配 Outbox/Inbox、RabbitMQ 与定时任务支持。服务自身的 `config` 声明队列及绑定，`@RabbitListener` 注册消费者，`@Scheduled` 驱动重试和心跳。

主要运行依赖为 Config Server、Eureka、MySQL、RabbitMQ 和 IAM 公钥端点。Gateway 负责订阅请求的会话校验和路由，Notification 的 SSE 连接按用户登记。业务事件经 RabbitMQ 接收，不通过通知服务反向修改项目或工作流状态。

## 构建、启动与检查

以下命令在 `SicProjectIS_cloud` 根目录执行：

```powershell
mvn -B -pl services/notification-service -am test
mvn -B -pl services/notification-service -am -DskipTests package
```

可执行产物为 `services/notification-service/target/notification-service-1.0.0-SNAPSHOT-exec.jar`。Docker 镜像及冻结 JAR 只读挂载方式见 [工程 README](../../README.md)。

已有冻结 JAR 环境示例（沿用对应的部署配置）：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600 notification-service
```

容器检查 `/actuator/health/readiness`；连通检查还应经网关建立 SSE 并收到 `CONNECTED`。数据库初始化及升级使用发行 SQL/补丁，`db/migration` 资源不代表启用了 Flyway。冻结 JAR 源码变更需重新发布并切换挂载源。

[测试目录](src/test/java/com/amatrix/sic/notification) 覆盖事件消费者、持久化投递与重试，契约位于 [src/test/resources/contracts](src/test/resources/contracts)。多实例时需验证每个副本连接都收到 live 事件，参见 [多实例说明](../../docs/multi-instance-startup.md) 和 [启动验收](../../docs/backend-startup-guide.md)；单元测试不能替代真实 RabbitMQ/SSE 投递检查。
