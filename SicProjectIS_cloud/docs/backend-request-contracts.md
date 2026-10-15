# 后端请求与 OpenFeign 契约

本文索引后端主动 HTTP 调用和主要请求载体。当前生产代码包含 8 个 OpenFeign 客户端、20 个方法；未使用 `RestTemplate`、`WebClient`、JDK `HttpClient` 或手写 HTTP 连接。Gateway 的路由转发属于反向代理链路，不计入 Feign 客户端。

每个 Feign 方法的源码 Javadoc 已说明调用目标、Header、参数、响应及失败语义。Controller 入站 API 见 [Controller API 索引](controller-api-index.md)。

本轮覆盖及测试结果见 [后端请求注释验收报告](../verification/runs/backend-request-commenting-verification.json)。

## 1. 共同调用规则

- `@FeignClient(name=...)` 使用服务名，通过 Eureka 和 Spring Cloud LoadBalancer 解析实例。
- `/internal/**` 通常在 `Authorization` 中携带服务 Bearer token，接收端要求 `SCOPE_internal`。三个 TokenProvider 按服务实例缓存 token，在过期前 60 秒刷新。
- `/internal/tokens/service` 是例外：它以 `X-Service-Bootstrap-Secret` 兑换初始服务 token，不要求已有 Bearer token。bootstrap secret 和返回 token 都不得写入日志。
- 公共 Feign interceptor 只转发规范化后的 `X-Trace-Id`、`X-Saga-Id`、`X-Event-Id`，不自动转发用户 Authorization、Cookie 或任意入口 Header。
- 默认连接超时为 1000 ms、读取超时为 3000 ms；LoadBalancer retry 关闭，Feign circuit breaker 开启。客户端没有声明 fallback，依赖失败仍以异常返回，写请求不会伪造成功。
- `ApiResponse.success=false` 和 HTTP 非 2xx 是两层不同失败。调用方须同时检查 HTTP 调用、`success` 和非空 `data`，不能把依赖故障解释成权限拒绝、用户不存在或空列表。

## 2. OpenFeign 调用清单

| 调用方 | 目标服务 | 方法与路径 | 作用 | 源码 |
| --- | --- | --- | --- | --- |
| Material | Project | `GET /internal/projects/{projectId}/access` | 判定材料读、写、删除权限 | [MaterialProjectAccessClient](../services/material-service/src/main/java/com/amatrix/sic/material/client/MaterialProjectAccessClient.java) |
| Material | IAM | `POST /internal/tokens/service` | 兑换 material-service 内部 token | [MaterialTokenClient](../services/material-service/src/main/java/com/amatrix/sic/material/client/MaterialTokenClient.java) |
| Project | IAM | `GET /internal/departments/{deptId}/summary` | 校验部门存在、名称和启用状态 | [IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/IamDirectoryClient.java) |
| Project | IAM | `POST /internal/users/summaries` | 批量读取用户摘要，减少逐用户 RPC | [IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/IamDirectoryClient.java) |
| Project | IAM | `GET /internal/users/{userId}/summary` | 补齐或刷新单个用户快照 | [IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/IamDirectoryClient.java) |
| Project | IAM | `GET /internal/admin/overview-stats` | 读取 IAM 管理概览统计 | [IamDirectoryClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/IamDirectoryClient.java) |
| Project | IAM | `POST /internal/tokens/service` | 兑换 project-service 内部 token | [ServiceTokenClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/ServiceTokenClient.java) |
| Project | Workflow | `POST /internal/application-sagas` | 幂等创建申请模块 | [WorkflowSagaClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/WorkflowSagaClient.java) |
| Project | Workflow | `GET /internal/application-sagas/{sagaId}` | 查询申请模块创建结果用于恢复 | [WorkflowSagaClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/WorkflowSagaClient.java) |
| Project | Workflow | `DELETE /internal/application-sagas/{sagaId}` | 补偿 Workflow 侧申请数据 | [WorkflowSagaClient](../services/project-service/src/main/java/com/amatrix/sic/project/client/WorkflowSagaClient.java) |
| Workflow | Material | `POST /internal/material-reservations` | 正式迁移前预留材料版本 | [MaterialReservationClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/MaterialReservationClient.java) |
| Workflow | Material | `DELETE /internal/material-reservations/{reservationId}` | 释放失败迁移的材料租约 | [MaterialReservationClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/MaterialReservationClient.java) |
| Workflow | Project | `GET /internal/projects/{projectId}/finance-readiness` | 查询财务节点经办人配置 | [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/ProjectAccessClient.java) |
| Workflow | Project | `GET /internal/projects/{projectId}/access` | 单项项目操作判定 | [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/ProjectAccessClient.java) |
| Workflow | Project | `POST /internal/projects/access-decisions` | 批量项目、模块、轮次和节点权限判定 | [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/ProjectAccessClient.java) |
| Workflow | Project | `POST /internal/projects/{projectId}/expert-grants/sync` | 同步专家指派的临时项目授权 | [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/ProjectAccessClient.java) |
| Workflow | Project | `POST /internal/projects/{projectId}/expert-grants/revoke` | 回收专家指派范围的临时授权 | [ProjectAccessClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/ProjectAccessClient.java) |
| Workflow | IAM | `POST /internal/users/summaries` | 批量读取当前用户摘要 | [WorkflowServiceTokenClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/WorkflowServiceTokenClient.java) |
| Workflow | IAM | `POST /internal/tokens/service` | 兑换 workflow-service 内部 token | [WorkflowServiceTokenClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/WorkflowServiceTokenClient.java) |
| Workflow | IAM | `GET /internal/users/{userId}/summary` | 专家指派前核对用户和 EXPERT 角色 | [WorkflowServiceTokenClient](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/client/WorkflowServiceTokenClient.java) |

## 3. 请求载体

| 类别 | 请求结构 | 关键语义 |
| --- | --- | --- |
| IAM 入站 | `LoginRequest`、`RegisterRequest` | 密码只用于校验或哈希；注册不自动授予业务角色，项目申请会重新检查 IAM 当前部门 |
| 项目申请 | `StartProjectApplicationRequest`、`ApplicationSagaCommand` | 前者来自前端，后者是 Project 校验、生成项目 ID 后发给 Workflow 的跨服务命令；`sagaId` 是幂等与补偿关联键 |
| Workflow | `UploadWorkflowDefinitionRequest`、`StartModuleRequest`、`TransitionRequest` | 上传不等于发布；启动接口不接受 APPLICATION；迁移以 `expectedSeq` 防止旧页面覆盖新状态 |
| 专家评审 | `CreateBatchRequest`、`AssignExpertRequest`、`SubmitScoreRequest`、`ScoreItem` | 节点配置 ID、批次 ID、assignment ID 含义不同；个人提交与批次完成是两个阶段 |
| 项目鉴权 | `ProjectAccessBatchRequest`、`ProjectOperationTarget` | `ActorContext` 是被判定用户，服务 token 是调用身份；结果通过 `decisionId + projectId` 关联 |
| 专家授权 | `ExpertProjectGrantCommand` | 同步和撤销都按项目、专家、模块、轮次、节点精确描述范围 |
| 材料预留 | `MaterialReservationCommand` | `commandId` 标识一次提交尝试，引用具体材料版本；预留后 Workflow 仍需锁内复核序号 |
| 项目部门修复 | `AssignDepartmentRequest` | 仅为历史空部门项目补归属，目标部门须启用，原因写审计 |
| 异步单据 | `DocumentGenerationCommand` | 通过 Outbox/RabbitMQ 传输，不是 Feign；`commandId` 去重，snapshot 固化迁移当时数据 |

字段级说明位于 [IamDtos](../services/iam-service/src/main/java/com/amatrix/sic/iam/dto/IamDtos.java)、[ProjectDtos](../services/project-service/src/main/java/com/amatrix/sic/project/dto/ProjectDtos.java)、[WorkflowDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/WorkflowDtos.java)、[ExpertReviewDtos](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/dto/ExpertReviewDtos.java) 和 cloud-contracts 的 [ApplicationSagaCommand](../platform/cloud-contracts/src/main/java/com/amatrix/sic/contracts/ApplicationSagaCommand.java) 等共享类型。Map 形式的动态请求体已在对应 Controller API 方法 Javadoc 中逐项说明。

## 4. 当前实现边界

- `WorkflowSagaClient.create` 同时发送服务 Authorization 和 `X-User-Authorization`。当前 Workflow 创建入口只读取服务身份和命令体，没有消费后者；因此远端鉴权依据是 `SCOPE_internal`，用户身份来自已由 Project 生成的 command。该 Header 不能作为现有用户鉴权能力描述。
- `/internal/tokens/service` 当前以 bootstrap secret 鉴权并把 `serviceName` 写入 token；当前兑换 Controller 未校验 serviceName 白名单。部署必须保护 bootstrap secret，不能仅依靠调用方传入的名称建立信任。
- `ProjectOperationTarget` 的 moduleType、roundNo、taskNodeId 为 null 时，当前 Project 匹配逻辑跳过相应维度。任务办理调用必须传入当前模块、轮次和节点；宽泛的读取判定可以有意省略范围。
- OpenFeign 断路器当前没有 fallback。权限依赖故障保持拒绝或异常返回，写请求没有自动降级成功，也没有无幂等保护的业务重试。
