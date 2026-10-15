# cloud-messaging

业务服务共用的 Outbox/Inbox 消息库，负责本地事务事件入库、多实例发送租约、RabbitMQ 确认与重试、消费去重，以及消息和 JDBC 观察指标。本模块产出普通 JAR，由拥有数据库及 RabbitMQ 连接的服务加载，没有独立进程或业务端口。

**源码与职责**

Java 根包为 `com.amatrix.sic.messaging`。

| 包/主要类 | 职责 |
| --- | --- |
| 根包 `CloudMessagingAutoConfiguration` | 装配调度、公共 Mapper、Exchange/DLQ、Outbox、消费去重及观察组件。 |
| 根包 `OutboxService` | 序列化 EventEnvelope 并在本地事务中入库。 |
| 根包 `OutboxRepository`、`OutboxRecord`、`OutboxPublisher` | 读取候选、竞争发送租约、发布并等待 confirm、记录成功/失败及下次重试时间。 |
| 根包 `ConsumedEventGuard` | 按 eventId + consumerName 插入 Inbox，重复时返回 false。 |
| 根包 `OutboxProperties`、`MessagingMetrics` | 发布配置、Outbox 积压/失败/最老年龄及死信队列 Gauge。 |
| 根包 `JdbcLatencyObserver` | 包装 DataSource、Connection 和 Statement，观察连接取得、预编译、执行和持有耗时。 |
| `dao.MessagingMapper`、`entity.OutboxEntry` | MyBatis 数据访问接口与 Outbox 查询行。 |

见 [Java 源码](src/main/java/com/amatrix/sic/messaging)、[Mapper SQL](src/main/resources/mapper/messaging/MessagingMapper.xml)、[公共表结构资源](src/main/resources/db/common)。

**消息如何从业务事务到达消费者**

```text
业务写入 + OutboxService.enqueue（同一本地事务）
    → 定时查询待发事件 → 条件 UPDATE 竞争租约
    → RabbitMQ 发布并等待 publisher confirm
    → owner 匹配时标记已发送；失败记录退避时间
消费者事务：ConsumedEventGuard.begin → 执行业务写入 → 一起提交
```

业务事务应包含 enqueue，才能保证业务与待发事件一起提交；仅入库成功不代表 RabbitMQ 已收到。消费端应在同一业务事务里调用 begin，失败回滚时去重记录也回滚。

多副本可能查到相同候选，只有条件更新成功的副本取得租约。发布结果回写必须匹配 lock_owner，过期租约可重新领取。消息发送成功到数据库标记成功之间仍有失败窗口，因此可能重复投递，消费端仍需去重。当前代码提供重试退避，没有最大尝试次数后自动转入 DLQ 的 Outbox 逻辑。

公共业务 Topic Exchange 默认 `sic.events`；死信 Exchange/队列固定为 `sic.events.dlx` / `sic.events.dead.q`。业务队列及死信参数由各服务配置，不能仅依赖本库自动为每条业务路由建队列。人工死信恢复见 [dlq-recovery-tool](../dlq-recovery-tool/README.md)。

**配置与接入条件**

自动配置通过 [AutoConfiguration.imports](src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) 注册，在 JdbcTemplate/MyBatis 自动配置之后处理，并要求存在 `SqlSessionTemplate`。公共 Mapper XML由自动配置注册；使用方提供数据库、事务管理、ObjectMapper 和 RabbitMQ 连接。

| `sic.messaging` 配置 | 默认值/行为 |
| --- | --- |
| `exchange` | `sic.events`。 |
| `batch-size` | 100。 |
| `fixed-delay-ms` | 1000 毫秒。 |
| `publisher-enabled` | boolean 默认 false；工程共享配置显式设置 true。 |
| `confirm-timeout-ms` | 10000 毫秒。 |
| `claim-lease-seconds` | 30 秒。 |
| `max-retry-seconds` | 300 秒，失败按指数退避并封顶。 |

RabbitMQ 使用 correlated publisher confirm，实际参数见 [共享集中配置](../config-server/src/main/resources/config/application.yaml)。公共 outbox_event / consumed_event 表应已经包含在服务数据库初始化中；存在 `db/common` SQL 资源不表示库会自动运行 Flyway。

JDBC 告警配置为 `sic.observability.jdbc.acquire-warning-ms/query-warning-ms/hold-warning-ms`，默认分别 100/200/1000 毫秒。消息指标为 `sic.outbox.pending`、`sic.outbox.failed`、`sic.outbox.oldest.age.seconds`、`sic.events.dead.queued`，附服务名标签；Prometheus 输出时采用注册表的命名转换。

**构建与验证**

在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven：

```powershell
mvn -B -pl platform/cloud-messaging -am test
mvn -B -pl platform/cloud-messaging -am -DskipTests package
```

产物为 `platform/cloud-messaging/target/cloud-messaging-1.0.0-SNAPSHOT.jar`。

[测试目录](src/test/java/com/amatrix/sic/messaging) 覆盖自动装配、Outbox 确认/失败、消费去重及 JDBC 观察。真实 MySQL 事务回滚、租约竞争、RabbitMQ 死信重放另由 [integration-tests](../integration-tests/README.md) 验证。
