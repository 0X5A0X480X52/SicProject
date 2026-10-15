# cloud-contracts

跨服务共享的数据契约库，统一 HTTP 响应、操作人上下文、事件信封，以及项目授权、申请 Saga、材料预留和文档生成的请求/结果结构。本模块产出普通 JAR，由业务服务及其他平台库依赖，没有独立启动入口、HTTP 端口或数据库。

**源码与职责**

Java 包为 `com.amatrix.sic.contracts`，当前契约集中在同一包中，主要使用 Java record。

| 契约组 | 主要类型 | 用途 |
| --- | --- | --- |
| API 响应 | `ApiResponse<T>`、`InternalError` | 统一业务响应信封与内部错误结构。 |
| 身份上下文 | `ActorContext` | 用户、部门、角色、权限、token ID 和授权版本；提供角色/权限判断。 |
| 事件 | `EventEnvelope<T>` | 事件 ID、类型、协议版本、时间、聚合标识、关联/因果标识和 payload。 |
| 身份查询 | `UserSummary`、`DepartmentSummary`、`IamOverviewStats` | IAM 向其他服务提供的身份、部门及统计投影。 |
| 项目授权 | `AccessDecision`、`ProjectOperationTarget`、`ProjectAccessBatchRequest`、`ProjectAccessResult`、`ExpertProjectGrantCommand` | 单项/批量项目操作判定、精确模块范围和专家临时授权。 |
| 申请 Saga | `ApplicationSagaCommand`、`ApplicationSagaResult` | Project 与 Workflow 协调申请创建、查询及补偿时的数据契约。 |
| 状态迁移 | `StateRecordContract`、`StateTransitionContract` | 返回一次迁移历史及新节点、状态、结束标记。 |
| 材料预留 | `MaterialReservationCommand`、`MaterialReservationResult` | 指定项目、模块、序号、材料版本和必需材料类型，返回预留租约。 |
| 文档生成 | `DocumentGenerationCommand` | Workflow 请求 Material 生成指定模板的输出材料。 |

完整定义见 [Java 源码目录](src/main/java/com/amatrix/sic/contracts)。契约不包含各服务的 Mapper、数据库实体或业务执行逻辑。

**调用和事件中的使用方式**

同步调用通常返回 `ApiResponse<T>`，结构为 `{success,data,message}`；成功工厂的 message 为 `OK`。调用方仍需处理非 2xx HTTP、反序列化失败及 `success=false`，不能仅因拿到响应对象就使用 data。

异步业务使用 `EventEnvelope.create(...)` 生成 UUID eventId、协议版本 1 和当前时间，再交给 [cloud-messaging](../cloud-messaging/README.md) 写 Outbox。eventId 是消费去重标识，eventVersion 是事件协议版本；两者都不同于用户 authVersion、项目数据版本或工作流 seq。correlationId 只用于关联步骤。

`ActorContext` 是身份数据载体。将 JWT 转成该对象由 [cloud-security](../cloud-security/README.md) 完成；实时项目授权、当前任务资格和会话有效性分别由业务策略及网关执行。

**前端接口清单**

[frontend-api-surface.csv](src/main/resources/contracts/frontend-api-surface.csv) 记录 provider、method、path、successStatus 和 responseKind。[FrontendApiSurfaceTest](../integration-tests/src/test/java/com/amatrix/sic/integration/FrontendApiSurfaceTest.java) 将其与 Controller、Gateway 首条路由和可执行 Spring Cloud Contract 对照。

CSV 是接口覆盖核对清单，不会自动生成 Controller 或前端调用代码。调整接口时，需要同步核对本清单、服务契约测试和实际调用方。

**构建与验证**

在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven：

```powershell
mvn -B -pl platform/cloud-contracts -am package
```

产物为 `platform/cloud-contracts/target/cloud-contracts-1.0.0-SNAPSHOT.jar`。本模块当前没有独立测试源码；行为兼容性主要由使用它的服务测试、契约测试及 [integration-tests](../integration-tests/README.md) 验证。

无需为本模块配置环境变量或启动容器。工程依赖与模块顺序见 [父 POM](../../pom.xml)，总体结构见 [工程 README](../../README.md)。
