# material-service

材料与对象存储服务，负责上传下载、材料版本与哈希、工作流引用预留/确认、生成单据及对象删除恢复。默认容器内端口为 **9004**，拥有 MySQL Schema **sic_material**，文件内容存储在 MinIO。

## 包结构与职责

Java 根包为 `com.amatrix.sic.material`，源码位于 [src/main/java/com/amatrix/sic/material](src/main/java/com/amatrix/sic/material)。

| 包 | 主要类 | 职责 |
| --- | --- | --- |
| 根包 | `MaterialServiceApplication` | 扫描组件、Feign 客户端及配置属性。 |
| `config` | `MinioConfiguration`、`MaterialMessagingConfiguration` | 创建 MinioClient，声明引用确认及单据生成消息队列。 |
| `config.properties` | `MaterialProperties`、`MaterialClientProperties` | MinIO endpoint/bucket/凭据及内部令牌配置。 |
| `controller` | `MaterialController` | multipart 上传、列表、版本下载和删除 HTTP 入口。 |
| `controller.internal` | `InternalMaterialReservationController` | Workflow 使用的材料预留、确认及释放接口。 |
| `service` | `MaterialApplicationService` | 项目授权检查、材料元数据/版本、上传失败补偿及删除编排。 |
| `service.reservation` | `MaterialReservationService` | 按 commandId 幂等预留，确认/释放引用和过期清理。 |
| `service.document` | `GeneratedDocumentService`、`GeneratedDocumentRenderer` | 按命令幂等生成 PDF、存储对象和材料版本。 |
| `service.cleanup` | `ObjectDeletionService` | 重试持久化对象删除任务并记录失败。 |
| `storage` | `MinioObjectStorage` | 检查/创建 bucket，封装对象 put/get/remove。 |
| `policy` | `MaterialAccessPolicy` | 将 MATERIAL_READ/WRITE/DELETE 等操作交由 Project 判断范围授权。 |
| `client`、`client.auth` | `MaterialProjectAccessClient`、`MaterialTokenClient`、`MaterialTokenProvider` | Project 访问判断、IAM 内部令牌兑换及缓存。 |
| `messaging.consumer` | `MaterialReservationEventConsumer`、`GeneratedDocumentEventConsumer` | 去重处理引用确认和单据生成事件。 |
| `dao`、`entity` | 材料、预留、单据、对象删除 Mapper 及对应行模型 | MySQL 元数据和恢复任务持久化。 |
| `metrics` | `MaterialCleanupMetrics` | 待删除任务、重试/失败及未确认预留指标。 |

SQL 位于 [mapper/material](src/main/resources/mapper/material)。对象存储适配集中在 `storage`，材料业务规则和数据库事务位于 `service`。

## 接口与主要流程

| 接口 | 用途 |
| --- | --- |
| `/api/projects/{projectId}/materials/**` | 材料列表与 multipart 上传。 |
| `GET /api/material-versions/{id}/download` | 流式下载，保留内容类型和 UTF-8 文件名。 |
| `DELETE /api/material-versions/{id}` | 检查引用/权限后逻辑删除并登记对象删除任务。 |
| `/internal/material-reservations/**` | 预留、确认、释放指定版本供 Workflow 引用。 |

上传流程为 `MaterialController → MaterialApplicationService → Project 授权 → MinIO 写入 → 数据库版本/哈希及 Outbox`。对象存储与数据库分开提交，失败时尝试补偿；不能把两者视为一个原子事务。

引用流程为 `Workflow → reserve(commandId) → 本地业务提交 → material.reservation.confirm 事件 → 确认引用`。释放与过期清理由预留服务处理，并支持对象尚未删除时的迟到确认。已确认/受预留保护的版本和生成单据有删除限制。

删除元数据及登记对象删除任务在数据库事务中完成，再由 `ObjectDeletionService` 定时删除 MinIO 对象、重试并记录退避状态，避免只靠一次同步删除。

`document.generation.requested` 由 `GeneratedDocumentEventConsumer` 处理，`GeneratedDocumentService` 按 commandId 去重。当前 renderer 是确定性的一页 PDF 渲染，非 ASCII 字符会转为问号；它的格式能力以源码及测试为准。

## 配置与启动装配

| 配置来源/变量 | 作用 |
| --- | --- |
| [本地 application.yaml](src/main/resources/application.yaml)、`CONFIG_IMPORT` | 服务名与配置中心导入。 |
| [material-service.yaml](../../platform/config-server/src/main/resources/config/material-service.yaml) | 9004 端口、数据库、multipart 和 MinIO 配置。 |
| `MATERIAL_DB_URL/USERNAME/PASSWORD` | 本服务数据库。 |
| `MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET` | 对象存储；bucket 默认 `sic-materials`。 |
| `MAX_FILE_SIZE`、`MAX_REQUEST_SIZE` | 默认分别为 50MB 和 55MB。 |
| `SERVICE_TOKEN_BOOTSTRAP_SECRET` | 内部令牌兑换。 |
| `sic.material.object-cleanup-ms`、`sic.material.reservation-cleanup-ms` | 对象删除/预留清理周期，默认 5000/60000 ms。 |

共享 [application.yaml](../../platform/config-server/src/main/resources/config/application.yaml) 提供 Eureka、JWT、RabbitMQ、MyBatis、连接池和 Feign 超时/断路器。启动类扫描组件/属性和 Feign 客户端，公共模块接入安全、消息与调度，MinIO 配置创建客户端，存储适配器初始化 bucket。

主要依赖为 Config Server、Eureka、MySQL、RabbitMQ、MinIO、IAM 和 Project。外部用户访问仍需项目范围授权；内部预留接口要求内部 scope。

## 构建、启动与检查

以下命令在 `SicProjectIS_cloud` 根目录执行：

```powershell
mvn -B -pl services/material-service -am test
mvn -B -pl services/material-service -am -DskipTests package
```

可执行产物为 `services/material-service/target/material-service-1.0.0-SNAPSHOT-exec.jar`。初始化、Docker 构建及冻结 JAR 挂载流程见 [工程 README](../../README.md)。

已有冻结 JAR 环境示例（使用原项目、凭据和 override）：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600 material-service
```

容器检查 `/actuator/health/readiness`，材料 API 经网关转发。数据库升级使用发行 SQL/补丁，`db/migration` 文件不会自动启用 Flyway；更新冻结包需切换挂载源。

[测试目录](src/test/java/com/amatrix/sic/material) 覆盖预留、删除事务、对象删除恢复、PDF 渲染和 bucket 初始化，契约位于 [src/test/resources/contracts](src/test/resources/contracts)。实际文件上传/下载、引用确认和副本行为需真实 MySQL/MinIO/RabbitMQ 验收，参见 [启动指南](../../docs/backend-startup-guide.md) 和 [多实例说明](../../docs/multi-instance-startup.md)。
