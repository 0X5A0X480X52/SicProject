# 鉴权与普通业务流转的数据库压力：现状和优化方案

调研日期：2026-10-10。范围为当前 `SicProjectIS_cloud` 源码、集中配置与迁移 SQL。
问题场景包含项目/工作流权限、批量授权，以及普通业务流转。本文给出实施方案，尚未修改业务实现或线上配置。

## 1. 结论与证据边界

项目已经使用 Redis，普通 JWT 验签也在服务本地完成。Redis 当前主要保存登录版本、注销标记、网关限流计数，并没有缓存 Project 的业务权限判定。

从源码看，应优先处理 **嵌套查询导致的连接争用、持锁事务中的远程调用、批量授权重复装配完整详情**，再引入业务权限缓存。普通业务流转存在这些路径，不需要大量登录请求也可能发生连接池耗尽或锁等待。

已确认的是代码路径和查询放大机制；没有取得故障时的 MySQL 线程、锁等待、慢 SQL、连接池指标，因此不能把历史“卡死”直接认定为 MySQL 死锁或 IAM 单点故障。

已完成一个隔离验证：使用 Spring JDBC、HikariCP、H2，连接池大小设为 1；在 RowMapper 内再次执行查询，内层获取连接超时；先完成外层映射再查询则成功。1 个测试通过。该实验验证连接占用机制，不是历史生产故障复现，也不是吞吐量测试。

## 2. 当前 Redis 与 IAM 到底承担什么

| 路径 | 当前行为 | MySQL 压力 |
| --- | --- | --- |
| Gateway 普通受保护 `/api/**` | 本地 JWT 验签，再并行检查 Redis 注销标记与用户版本 | 会话检查本身不查询 MySQL |
| 下游业务服务 JWT 验证 | 根据 JWK 公钥验证 JWT，转换 claims 为角色/权限 | 不逐请求调用 IAM 用户查询 |
| 登录：存在的用户名 | 查用户名 ID，再查用户、角色、权限；BCrypt 校验；签发 JWT、写 Redis 版本 | 正常路径 4 次 SELECT |
| `/api/auth/me`：用户存在 | `IamRepository.findById` | 3 次 SELECT，没有用户信息缓存 |
| `/internal/users/{id}/summary`：用户存在 | 复用完整 `findById`，最后只输出 summary | 3 次 SELECT；多读取了密码散列与 permission 集合 |
| 内部 service token | 校验 bootstrap secret 后签发 JWT | 该签发路径没有 SQL；调用方已缓存 token，临近到期才续签 |
| 注销 | Redis 保存 jti 撤销标记，TTL 为 JWT 剩余有效期 | 无 SQL |
| 用户角色/状态/权限变更 | MySQL 更新 `auth_version`，同步 Redis 版本，写 Outbox | 写库及逐用户版本查询；Redis 写在数据库事务内 |
| Project 权限判定 | 查询项目、成员、有效授权，内存中计算操作权限 | 对合法用户与非空项目集合，通常 3 次 SELECT/批次 |
| Workflow / Material 项目权限 | Feign 调 Project 权限接口 | 压力落在 Project 数据库，不是必然落在 IAM |

Redis key：

- `sic:jwt:version:<userId>`：当前版本，无 TTL；版本缺失时 Gateway 拒绝访问。
- `sic:jwt:revoked:<jti>`：注销标记，有 TTL。
- `sic:gateway:rate:<clientIp>:<second>`：一秒固定窗口计数，首个请求设置 2 秒 TTL。

Gateway 一次受保护请求通常有两次会话 Redis 读取；启用限流时另有一次计数写，窗口首次计数还会设置 TTL。默认限流是每 IP 每秒 40 次，不能限制绕过 Gateway 的内部 Feign 或 RabbitMQ 消费。

限流 Redis 故障时当前代码放行；会话 Redis 故障时当前代码拒绝。会话拒绝被映射成 401，前端收到 401 会清理登录态并跳转登录页；依赖故障可能因此表现为“突然掉线”，应与真实凭证失效区分。

## 3. 普通流转中的高优先级问题

### 3.1 授权详情的 RowMapper 内嵌套查询：N+1 与连接争用

`ProjectAuthorizationService.members()` 在每行 RowMapper 内调用 `users.require(userId)`；`grants()` 每行调用两次，查询受权人与授权人。`ProjectUserDirectory.require()` 即使本地快照命中，也会执行查询；快照缺失还会请求 IAM 并写回快照。

`detail()` 本身没有事务注解。外层 `jdbc.query` 在映射完成前持有连接；在没有外部事务绑定连接的路径中，内层查询会获取另一条连接。如果多个请求把连接池占满后同时进行内层查询，就可能所有请求都等待额外连接，直到获取连接超时。此时 MySQL 本身可能并没有死锁。

修复方向：RowMapper 只读取当前行；先把成员/授权记录映射成 DTO，释放查询连接，收集去重 userId，使用 `IN` 分批加载快照，最后在内存中装配。IAM 缺失补全放在已释放数据库连接的阶段，限制并发并合并同一个 userId 的请求。不要仅通过扩大连接池掩盖该机制。

### 3.2 普通流程推进：持有模块行锁时远程鉴权

`WorkflowRuntimeService.transition()` 有 `@CanOperateModule` 前置检查；进入本地事务后，`lockedTransition()` 再通过 `module(id,true)` 执行 `SELECT ... FOR UPDATE`，然后调用 `operationPolicy.exactCanOperate()`。对符合本地条件的非系统管理员，该调用继续通过 Feign 请求 Project。

需要保留事务内的状态和权限重验；问题在于它目前包含远程 I/O。Project 变慢时，Workflow 等待 RPC 的整个期间都占用数据库连接和模块行锁。同一模块的后续写请求排队，其他模块也可能因连接池满而受影响。

此外，目标节点为 `PROJECT_LEADER` 时，`createTask()` 会调用 `requireProjectContext()`，再次请求 Project 并更新 `project_snapshot`；自动创建后继模块也可能走到这条路径。这是在普通流转事务内部读取负责人上下文。

近期处理：限制 RPC 并发、明确短超时，减少重复上下文查询，单独测量锁内等待时间。中期处理：事务前准备远程数据，事务内只保留本地任务/轮次/序号/版本校验和状态写入；跨服务权限撤销的一致性要通过版本栅栏、同步确认或明确的授权协议保证。未具备这些条件时，保留受控的远程重验，不能直接删除第二次权限校验。

材料预留目前在推进事务之前执行，确认通过 Outbox 发出；文档生成与生命周期动作也已经主要写 Outbox。应保留这些已有的事务解耦设计。

### 3.3 专家评审：锁内请求 IAM / Project，以及锁顺序不一致

`ExpertReviewService.assign()` 先锁 batch、再锁 module，然后进行操作权限检查、请求 IAM 用户摘要、同步 Project 专家授权。`submit()` 先锁 assignment、batch、module，再进行操作权限检查。`remove()` 在持锁期间远程撤销 Project 授权。返回详情时也可能再触发权限检查。

存在值得复现的锁顺序风险：评分/指派路径为 `batch → module`，普通推进路径先锁 `module`，离开专家节点时又更新 batch 的 `access_ended_at`，会取得 batch 写锁。自动评审推进又使用 `batch → module`。并发时有形成锁等待环的可能；这只是源码风险，尚未取得实际 deadlock report。

修复方向：所有相关命令统一锁顺序，例如 `module → batch → assignment`，初始标识解析放在锁前、拿锁后重新验证归属；多个同类行按主键排序锁定。远程身份查询提前进行并检查版本；授权同步使用带幂等键、可补偿的命令/Outbox。若用户需要指派后立即操作，则显式等待授权确认或展示同步状态，不能把异步入队等同于已经授权。

### 3.4 工作流查看的权限回退可能触发写库

`WorkflowProjectAccessPolicy.canAccessModule()` 在 Project 拒绝后，会查询本地专家指派并调用 `syncExpertGrant()` 补授权。Project 端已有 `ALREADY_ACTIVE` 幂等分支，不是每次调用都会写入；首次修复、授权持续不匹配或并发缺失时仍可能在“读取页面”路径中触发检查、写授权、更新项目版本、审计和事件。

应把常规授权建立放到专家指派/恢复命令中，保留可观察的修复流程。历史授权回填和立即可用语义验证完成后，再移出读权限热路径。并发补授权还应使用数据库可证明的唯一约束/幂等机制；当前“先 COUNT 再 INSERT”不是完整的并发幂等保证。

### 3.5 查询已有索引，但不完全匹配热路径

`workflow_task` 已有 assignee/status、role/status 索引，V102 又增加 `(status,candidate_role,module_instance_id)`。这不能直接等同于已覆盖 `WHERE module_instance_id=? AND status='OPEN'` 的更新、模块任务查询和权限 JOIN。

优先对上述 SELECT 的等价查询做 EXPLAIN，结合真实基数评估新增 `(module_instance_id,status,node_id)`。尤其要核对“完成当前模块全部 OPEN 任务”的 UPDATE 扫描范围及锁范围。Project 已有成员 `(user_id,project_id)` 和 grant `(project_id,grantee_user_id,status)` 索引，不能把所有压力简单归因为没有索引；新增索引需看真实执行计划。

## 4. 批量授权的放大机制

### 4.1 Project 批量成员 / 授权：重复整份详情

`upsertMembers()`、`assignGrants()`、`revokeBatch()` 逐个调用单项方法。每个单项方法返回 `detail(projectId,actor)`，调用方没有使用中间结果；批量结束又查一次详情。

详情装配包含所有成员、三类授权、每条记录对应的用户快照、全部 enabled 用户候选集。设本次处理 N 人、原有 M 条成员/授权记录，每次详情都至少重复扫描 M；新增记录时总装配量可增长为 `O(N×M + N²)`。即使每次 SQL 有索引，往返次数、对象装配、重复候选集查询及持锁时间仍会增加。

建议把单项写逻辑拆成不装配响应的内部 command；批量校验一次、去重加载用户一次、批处理写入，审计保留逐项信息，Outbox 合并表达受影响范围；整批只装配一次详情。同一个项目版本可按一次完整命令递增，须核对既有事件消费者的版本语义。

当前批量请求由一个事务承载。近期保留原子的全成功/全失败语义，通过 set-based SQL、批写及限额缩短事务。超大批次如需分块提交，应另设明确返回进度/失败项的任务 API，不能悄悄改变原接口原子性。

### 4.2 IAM 角色权限变更：逐用户更新，随后双消费者回查

`IamAdminService.invalidateUsers()` 对 N 个用户逐个执行 UPDATE 版本、SELECT 版本、同步 Redis SET；都发生在数据库事务内。

提交后，Project 与 Workflow 两个消费者各自逐用户调用 IAM summary。对存在用户，每次 summary 有 3 次 SELECT。仅这条链路便约有：

- N 次 IAM UPDATE + N 次版本 SELECT + N 次 Redis SET。
- 2N 次 summary RPC，产生 6N 次 IAM SELECT。
- 合计约 8N 次 IAM SQL，另有固定权限写入、Outbox、两边快照写入、审计；失败重试还可能重放整批。

这是按源码计算的正常路径操作数量，不是压测结果。建议权限 code 一次查询验证，批量更新版本、一次读取版本映射，Redis 使用 pipeline/批量协议；consumer 使用带版本的分块快照事件或批量 summary API，减少每人一次 RPC。

两个消费者当前在 `guard.begin()` 后进入循环 RPC，整个消费方法是本地事务，连接会在等待远程响应时一直占用。改为先完成受限的远程批量读取，再在短事务内完成 inbox 去重与快照写入，或使用完整的快照事件直接本地写入。inbox 标记和写入必须原子提交，不能提前记“已消费”。快照更新应拒绝较旧 source_version，防止乱序覆盖。

## 5. Redis 缓存方案及一致性要求

### 5.1 缓存层次与范围

| 层次 | 建议 | 约束 |
| --- | --- | --- |
| JWT 与会话安全状态 | 继续本地验签 + Redis 撤销/版本检查 | Redis 不可用时不绕过撤销判断 |
| 请求内 Project 上下文 | 同一个请求去重加载，用于展示与重复读取 | 写入后清理；锁后的状态/权限重验不得盲目复用锁前判定 |
| IAM 用户摘要/展示资料 | 最小投影；按用户与版本缓存，合并并发回源 | 不存密码散列；用于指派资格时需验证当前 enabled、角色与版本 |
| 跨请求业务读权限 | 引入 Redis 版本化授权事实/判定缓存 | 必须覆盖用户、项目、操作、模块、轮次、节点、时间边界 |
| 流程推进与评分等写命令 | 本地任务、当前节点/轮次/seq、评审状态以事务内重验为准 | 不用长 TTL 的 allowed=true 替代写前校验 |

业务判定 key 可采用以下维度，具体编码与长度在实现时确定：

```text
policyEpoch + userId + userAuthVersion + actorRoleDeptContext
+ projectId + projectAuthVersion + operation + moduleType + roundNo + taskNodeId
```

当前单项 Project `decide()` 重建 ActorContext 时 authVersion 为 0；它的接口不能直接承载上述版本 key，需要补充可信上下文并兼容旧接口，不能把 0 当有效授权版本。

Project 的成员/授权命令多数已有 `touch(projectId)` 递增 version，这是可用基础；接入缓存前仍须枚举负责人、成员、grant、生命周期、内部同步和数据导入等全部写入口，确认每次会影响权限的变更都递增相应版本并可靠发布失效。仅依赖异步删除缓存会留下撤权窗口。要实现“撤权 API 成功后旧权限立即不可用”，应使用同步版本栅栏/确认；单靠短 TTL 或 Outbox 最终一致性不能承诺这一点。

示例展示资料 TTL 可从 30–60 秒、业务只读判定从 5–15 秒起测，这些是实验起点，不是已有配置或安全保证。JWT 有效期、授权 effective_to、未来 effective_from 都限制缓存可用区间；当前 grant 查询没有返回时间边界，需补投影再计算 TTL/下次变化时刻。否定判定也不能忽略未来授权激活。流程状态或评审资格参与判定时还需相应状态版本。

普通缓存 miss 可以 single-flight、批量回源并限制并发；安全状态 miss 维持拒绝或使用受控的权威恢复流程，不能拿客户端 JWT 的版本直接填充 Redis，也不能让每个请求无界回查 MySQL。

### 5.2 现有 Redis 版本一致性缺口应先修复

登录和管理员变更都直接 SET 版本，没有单调保护。登录读到旧 auth_version，随后管理员完成更高版本更新，而登录响应最后再写旧版本，可能让版本倒退，重新认可旧 JWT。

另一个问题是 Redis SET 在 MySQL 事务提交之前：Redis 先更新而 MySQL 回滚，会使两边版本不一致；Redis 慢也延长数据库持锁时间。Lua 单调 CAS/pipeline 能改善倒退与往返，但不能提供 Redis/MySQL 的跨存储原子性。

实现建议：明确版本权威在数据库，使用单调发布与 Outbox 修复；登录遇到 Redis 版本高于当前读取版本时重新读取或拒绝重试。权限收紧需有阻断旧权限的栅栏及提交后的确认，API 在撤销确认前不返回“完全生效”；失败保守拒绝并补偿恢复。仅改为异步 afterCommit 刷新会产生安全窗口，需作为有明确一致性语义的设计来实现。

### 5.3 安全状态与可淘汰缓存分开

Compose 已有 Redis 8.2、AOF 和认证，但未声明内存上限/淘汰策略。当前同一实例保存安全状态与限流计数；加入业务缓存后更应控制资源。

建议安全状态 Redis 与业务缓存 Redis 使用独立实例/资源预算。逻辑 database 分库或 key 前缀不能隔离实例内存和淘汰策略。安全实例不得自动淘汰注销标记：`revoked:<jti>` 丢失而用户版本仍有效时，已注销 token 可能重新被接受；volatile 策略也可能淘汰这类有 TTL 的标记。使用禁止淘汰并监控容量/写失败，保留故障拒绝行为；可重建业务缓存另设内存限额与淘汰策略。[Redis 官方说明](https://redis.io/docs/latest/develop/reference/eviction/)

不建议给现有 version key 随意加短 TTL；它不是普通展示缓存，缺失会使所有存量 token 被拒绝。容量回收必须考虑最长有效 token、注销/版本留存和可靠恢复。

## 6. 诊断、保护与验证

### 6.1 先区分 MySQL 卡住与应用等待

随文提供只读 [auth-pressure-diagnostics.sql](sql/auth-pressure-diagnostics.sql)，适用于 Compose 使用的 MySQL 8.4。故障期间定时采样并保留时间戳；语句不执行变更或终止连接。

| 观察结果 | 主要排查方向 |
| --- | --- |
| Hikari active 到顶、pending 增长，但 MySQL 活跃执行少、锁等待少 | RowMapper 嵌套取连接、事务内远程等待、应用连接持有时间；抓线程栈 |
| data_lock_waits 存在稳定 blocking 事务 | 查阻塞者事务年龄、是否等待 Feign/Redis、扫描与锁顺序 |
| InnoDB latest detected deadlock 有 module/batch/assignment | 对照两条事务的加锁顺序及并发节点场景 |
| Threads_running 高、rows_examined 或 SQL 时间突出 | 全表/宽范围扫描、N+1、无分页列表、索引与数据量 |
| Gateway 401 激增，Redis 延迟/错误同步升高 | 会话检查依赖故障；区分真实 token 失效与基础设施失败 |

`performance_schema.data_lock_waits` 能映射请求方与阻塞方；InnoDB 状态用于死锁证据，线程栈用于确认应用正在等连接还是等 RPC。[MySQL 官方说明](https://dev.mysql.com/doc/refman/8.4/en/performance-schema-data-lock-waits-table.html)

各服务采集请求 p95/p99、SQL 次数/请求、事务持续时间、Hikari active/pending/acquire、Feign 目标与耗时、锁等待/死锁、Rabbit backlog/重试、Redis 命中与超时。通过 traceId 把 Workflow → Project → IAM 链路对应起来。

目前仓库未显式声明 Hikari 容量/获取超时、Feign connect/read timeout、Redis connect/command timeout；Gateway 已有 connect=2s、response=60s，但这不等于内部 Feign 同样受限。检查运行环境覆盖配置后，根据整体请求预算显式设置；内部依赖等待预算应短于外部请求预算。

连接预算按所有服务副本总和计算，并为后台消费、Outbox、迁移和运维保留空间，不能让每个服务独立盲目调大池。Hikari 在池满后会等待获取连接直到超时，因此“提高池大小”不是消除嵌套占用与长事务的替代方案。[Hikari 官方说明](https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby)

隔离前台业务与后台批量工作的并发预算；批量写、RPC、consumer 有界并发，队列过载快速返回可识别错误。普通只读权限可合并相同请求，写命令不进行无幂等保障的自动重试。MyBatis 迁移应保留批量 SQL、索引与短事务，它本身不会消除这些问题。

### 6.2 实施顺序

| 优先级 | 具体交付 | 验收依据 |
| --- | --- | --- |
| P0 | 故障采样/指标；移除 Project RowMapper 内查询；批量命令只装配一次详情；统一评审加锁顺序 | 高并发详情不嵌套取连接；批量详情次数为 1；锁等待和死锁有证据可追踪 |
| P1 | 内部依赖超时与并发预算；减少锁内 RPC；确认 workflow_task 热路径索引；IAM 最小摘要/批量查询 | 锁内 RPC 可观测并缩减；SQL 扫描量降低；下游慢时不拖垮全部业务 |
| P1 | 批量版本发布、消费者短事务、版本单调与可靠撤销 | 每批不再 2N 次 summary RPC；乱序不覆盖新快照；旧 JWT 不因竞态恢复有效 |
| P2 | 请求内上下文去重、独立业务 Redis、版本化只读权限缓存 | 缓存降低 SQL/请求；撤权与有效期边界符合明确的一致性语义 |
| P3 | 大规模批处理任务 API、容量与水平扩容 | 队列可恢复、结果可追踪、前台请求不受批任务挤占 |

### 6.3 必须覆盖的压测和并发用例

1. 多用户并发读权限/运行态/材料列表；同一用户热点访问与不同项目访问分开测试。
2. 同一模块并发推进：旧 expectedSeq 返回冲突，同一 seq 只能生成一个 state_record；多模块并行不被单模块热点拖住。
3. 评分、指派、移除与离开专家节点/自动推进并发，核对统一锁顺序、无重复授权和正确结束授权。
4. 批量授权 N=10/100/1000 与普通流转混合；分别观察详情次数、SQL 数量、事务时间、consumer 积压。千人批次是否允许需依据接口限额确定。
5. Project/IAM/Redis 延迟或中断时，错误返回受控，连接可释放，不产生无界重试；真正依赖故障不误清用户登录态。
6. 并发登录与权限收紧、Redis 发布失败/DB 回滚、事件乱序/重复、授权到期与未来生效、负责人变化、专家轮次/节点变化：旧 token 和旧允许判定不能重新生效。

目标指标在基线测试后确定，不预先声称“缓存命中 99%”“吞吐提高十倍”。最低验收包括：授权结果与业务状态不回退、无新增死锁/连接泄漏、批量查询与 RPC 次数符合新预算，普通流转在混合负载下满足约定的 p95/p99。

## 7. 主要源码入口与隔离验证

- [会话版本与注销检查](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/SessionValidityAuthorizationManager.java)、[网关限流](../services/gateway-service/src/main/java/com/amatrix/sic/gateway/GatewayRateLimitWebFilter.java)。
- [登录与版本发布](../services/iam-service/src/main/java/com/amatrix/sic/iam/AuthApplicationService.java)、[用户查询](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamRepository.java)、[IAM 管理与批量失效](../services/iam-service/src/main/java/com/amatrix/sic/iam/IamAdminService.java)。
- [Project 判定](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAccessPolicy.java)、[授权批量与详情](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectAuthorizationService.java)、[用户快照查询](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectUserDirectory.java)。
- [普通流转事务](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowRuntimeService.java)、[操作权限](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowOperationPolicy.java)、[专家评审命令](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/ExpertReviewService.java)、[读权限修复](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowProjectAccessPolicy.java)。
- [Project IAM 消费](../services/project-service/src/main/java/com/amatrix/sic/project/ProjectUserSnapshotConsumer.java)、[Workflow IAM 消费](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowUserSnapshotConsumer.java)。
- [集中配置](../platform/config-server/src/main/resources/config/application.yaml)、[Compose](../docker-compose.yml)、[前端 401 行为](../../SicProjectIS_fronted/src/api/client.ts)。
- 机制参考：[Spring Security JWT 验证](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)、[Spring JDBC 查询回调与连接释放源码](https://github.com/spring-projects/spring-framework/blob/v7.0.5/spring-jdbc/src/main/java/org/springframework/jdbc/core/JdbcTemplate.java)。项目事实以本仓库实现为准。

隔离实验位于 `target/mybatis-compatibility-research/src/test/java/research/JdbcNestedConnectionProbeTest.java`，复用此前独立兼容性研究 POM，未加入业务模块。执行命令：

```powershell
mvn -o -f target/mybatis-compatibility-research/pom.xml -Dtest=JdbcNestedConnectionProbeTest test
```

报告：`target/mybatis-compatibility-research/target/surefire-reports/TEST-research.JdbcNestedConnectionProbeTest.xml`；1 tests、0 failures、0 errors、0 skipped。target 为忽略的临时研究目录，后续可把需要长期保留的回归验证迁入相关测试模块。
