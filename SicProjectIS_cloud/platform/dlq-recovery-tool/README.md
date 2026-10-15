# dlq-recovery-tool

RabbitMQ 公共死信队列的一次性有界重放工具。根据死信 x-death 中的原 routing key，将支持的消息重新发布到 sic.events，确认成功后才 ACK 原死信。无 Web 端口，不使用业务数据库，也不从 Config Server 加载配置。

**源码与职责**

Java 包为 `com.amatrix.sic.dlq`。

| 类/资源 | 职责 |
| --- | --- |
| [DlqRecoveryToolApplication](src/main/java/com/amatrix/sic/dlq/DlqRecoveryToolApplication.java) | Spring Boot 一次性入口；排除 DataSource 自动配置，运行完记录数量并退出。 |
| [DeadLetterReplayProperties](src/main/java/com/amatrix/sic/dlq/DeadLetterReplayProperties.java) | 校验显式确认串、消息数量上限及确认超时。 |
| [DeadLetterReplayService](src/main/java/com/amatrix/sic/dlq/DeadLetterReplayService.java) | 拉取死信、恢复白名单路由、发布确认、ACK/NACK 及增加重放标记。 |
| [application.yaml](src/main/resources/application.yaml) | 禁用 Web，设置 RabbitMQ 连接和 sic.dlq-replay 参数。 |

依赖 [cloud-messaging](../cloud-messaging/README.md) 中的公共死信队列常量，但不会因此启用业务数据库 Outbox。RabbitMQ 的交换机、业务队列和绑定需要已经存在。

**重放顺序与支持范围**

```text
basicGet(sic.events.dead.q, autoAck=false)
    → 从 x-death 查原始白名单 routing key
    → mandatory 发布到 sic.events
    → 等待 publisher confirm，并检查 basic.return
    → 成功 ACK 原死信；失败 NACK/requeue，停止本次执行
```

支持的 routing key 固定为：

- iam.changed
- workflow.project-lifecycle-changed
- workflow.expert-review-completed
- workflow.module-state-changed
- material.reservation.confirm
- document.generation.requested

未知路由或缺少可用 x-death 时拒绝，当前消息重新入队并停止，不能假设工具会自动跳过它继续处理后面的消息。`project.authorization`、`workflow.expert-access-ended` 等不在当前白名单。

重放保留原消息 body 和原属性，增加 `x-sic-replay-count`、`x-sic-replayed-at`，清除原 expiration。事件信封的 eventId 不变，因此已经成功处理过的消费者可能按 Inbox 去重跳过；工具不清除业务去重记录，也不代表业务必定重新执行。

发布成功与原死信 ACK 之间仍存在失败窗口，可能再次重放同一事件，业务消费者仍需幂等。最大消息数限制的是本次成功重放数量，第一次失败会提前终止。

**环境变量**

| 变量 | 默认值/约束 |
| --- | --- |
| `RABBITMQ_HOST` | rabbitmq。 |
| `RABBITMQ_PORT` | 5672。 |
| `RABBITMQ_USERNAME` | sic。 |
| `RABBITMQ_PASSWORD` | 从环境提供。 |
| `DLQ_REPLAY_CONFIRM` | 必须等于 REPLAY_DEAD_LETTERS，否则拒绝运行。 |
| `DLQ_REPLAY_MAX_MESSAGES` | 默认 10，允许 1～1000。 |
| `DLQ_REPLAY_CONFIRM_TIMEOUT` | 默认 10s，必须为正数时长。 |

确认串要求来自工具自身 `DeadLetterReplayProperties.validate()`，用于区分正常启动与显式运维重放。Compose 将本工具放在 operations profile，普通启动不会运行它。

**构建、运行与验证**

以下命令在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven：

```powershell
mvn -B -pl platform/dlq-recovery-tool -am test
mvn -B -pl platform/dlq-recovery-tool -am -DskipTests package
```

在已确定的部署环境进行实际重放时：

```powershell
$env:DLQ_REPLAY_CONFIRM = 'REPLAY_DEAD_LETTERS'
$env:DLQ_REPLAY_MAX_MESSAGES = '10'
try {
    docker compose --env-file .env --profile operations run --rm dlq-recovery-tool
} finally {
    Remove-Item Env:DLQ_REPLAY_CONFIRM -ErrorAction SilentlyContinue
    Remove-Item Env:DLQ_REPLAY_MAX_MESSAGES -ErrorAction SilentlyContinue
}
```

已有部署需要使用对应的 project-name、env 和 Compose override。可执行产物为 `platform/dlq-recovery-tool/target/dlq-recovery-tool-1.0.0-SNAPSHOT-exec.jar`，环境就绪后也可直接 `java -jar`。

[测试目录](src/test/java/com/amatrix/sic/dlq) 覆盖参数校验、成功确认后 ACK、发布失败/无路由时重新入队、未知路由拒绝和 Rabbit 字节路由解析。真实 RabbitMQ 重放由 [InfrastructureIntegrationTest](../integration-tests/src/test/java/com/amatrix/sic/integration/InfrastructureIntegrationTest.java) 验证。操作步骤和故障处置见 [切换手册](../../docs/cutover-runbook.md)。
