# 迭代实施与验收记录

本记录对应本会话确认的六阶段方案。源码、脚本和本地真实运行结果分别列出；研究文档与用例 JSON 的设计状态不作为执行证据。运行指南见 [verification-guide.md](verification-guide.md)，数据库接管见 [cutover-runbook.md](cutover-runbook.md)。

## 已实现内容

| 阶段 | 实现 | 验收范围 |
| --- | --- | --- |
| 0 | 隔离 Compose、凭据生成、不可覆盖的八服务构建快照；SQL traceId、连接获取/持有/执行计时；Hikari、熔断器、Redis、MySQL 锁/死锁及消息队列采样 | 本地固定资源与固定负载。基线是本次已完成 Mapper 迁移的冻结版本，不能据此声称较原始生产版本提升 |
| 1 | Gateway 完整业务 runner、独立分支 runner、数据库断言；规范化表单标识与历史读取；前端嵌套表单同步写入业务表 | 六账号、两名真实审批专家、申请→合同→验收、37 用户节点、3 批次、17 类材料；另测退回重提与验收失败 |
| 2 | 消除 RowMapper 内查询；批量命令只生成一次详情；批量用户摘要；模块→批次→assignment 锁顺序；会话版本单调发布、事务栅栏与恢复；依赖超时、Resilience4j 和可辨识的 503 | MySQL/Redis 并发、回滚、发布失败、乱序、专家并发评分、材料预留/删除与混合流转测试 |
| 3 | 五库完整 DDL、种子、52 表结构清单和校验；Compose 空库初始化；旧库非空导入保护；工作流离线种子；运行服务移除 Flyway，保留历史链用于比对和工具 | 旧迁移链与最终 SQL 的真实 MySQL 等价检查；非空旧数据导入及运行视图；五库备份恢复演练 |
| 4 | 五业务服务按 controller/service/policy/client/dao/entity 分层；29 个 Mapper XML；公共 Outbox/Inbox 一并迁移 | 五服务主源码零 JdbcTemplate；真实 MySQL 单连接消息租约、幂等与事务并发测试；工具、指标代理及测试夹具保留 JDBC 例外 |
| 5 | 自动混合负载与完整采样、依赖故障和写回滚、应用回退、五库恢复、四类 CI 门禁；运行视图请求内批量判权；用户选择器独立短期缓存 | 本地执行与报告。远程 CI、生产峰值容量及生产部署另行验收 |

运行视图批量判权分别提交 `VIEW_WORKFLOW` 和当前任务操作；普通请求共享一次 Project 查询。管理员仍需读取项目权限，专家授权未同步时保留既有补齐逻辑。写流转持模块锁后重新判权，不复用运行视图结果。用户选择器缓存不参与授权校验；事务内未提交快照不发布，提交后失效，回滚保持已提交缓存。

## 运行中发现并修复的问题

1. **不同项目批量成员授权死锁。** 实际混合负载失败后，InnoDB 图显示两个事务在成员表主键尾部的间隙锁与插入意向之间形成循环。授权事务改为 READ COMMITTED，并首先锁定项目；专家授权结束消费者采用相同顺序。确定性 MySQL 测试覆盖不同项目同时插入及同项目并发授权，保留失败负载报告与原始诊断。
2. **两位专家并发评分无法完成批次。** 原 REPEATABLE READ 快照可能使后提交事务仍只聚合到一条评分。统一模块、批次、assignment 锁顺序，并为评分写事务使用 READ COMMITTED；真实双事务测试证明第二次提交完成批次。
3. **并发流转共享材料预留。** 仅以模块、序号和事件生成 commandId，会让失败竞争者释放胜者预留，也会让回滚后的重提复用 RELEASED 预留。每次外部流转尝试使用独立 commandId；同一次材料调用仍保持幂等，模块序号保证只有一个提交者。真实双库测试验证竞争与回滚重提。
4. **同秒授权时间、表单读写标识和节点数据丢失。** 授权时间使用微秒；SINGLE 表单按稳定代码持久化，HISTORY 保持版本标识并兼容旧数据；申请、合同和验收业务草稿与流转在同一本地事务提交。
5. **依赖故障误判登录失效和旧 token 回生。** Redis 栅栏及版本原子判定区分失效 401 和基础设施 503；版本保持单调，发布失败继续拒绝访问并可靠恢复。前端已有的仅 401 清登录行为继续适用。

没有为失败写请求增加自动重试或伪造成功。故障脚本在依赖恢复后显式重提一次，并检查状态、任务、表单、材料引用、Outbox 与预留确认。

## 最终构建验收

最终应用构建冻结于 `target/releases/runtime-batch-candidate/`，八个 JAR 的原始摘要与内容摘要均入清单。2026-10-10 至 2026-10-11 的本地验收如下。

| 检查 | 结果与证据 |
| --- | --- |
| 全量 Maven / 真实 MySQL、Redis、RabbitMQ、MinIO | [128 项测试，0 跳过，47 源码测试套件](../verification/runs/runtime-batch-real-mysql-test-gate.json)；全 reactor BUILD SUCCESS |
| 新库最终结构 | [52 表结构校验通过](../verification/runs/runtime-batch-schema.json)；旧 Flyway 链与完整 SQL 等价测试、非空旧数据导入及运行视图测试均包含在上述 MySQL 门禁 |
| 主流程与拒绝用例 | [40 项业务检查通过](../verification/runs/runtime-batch-business-negative.json)，[29 项数据库断言通过](../verification/runs/runtime-batch-business-negative-database.json) |
| 退回、旧评分、越权与验收失败 | [49 项分支检查通过](../verification/runs/runtime-batch-return-reject.json)，[32 项数据库断言通过](../verification/runs/runtime-batch-return-reject-database.json) |
| 混合业务与授权压力 | [90 秒窗口业务通过](../verification/runs/performance-runtime-batch-load.json)，[持续采样无新增死锁](../verification/runs/performance-runtime-batch-load-stability.json)，[29 项数据库断言通过](../verification/runs/performance-runtime-batch-database.json)；高精度性能比较另列 |
| 高精度阶段 5 性能比较 | [比较门禁通过](../verification/runs/precision-performance-comparison.json)；[稳定基线](../verification/runs/precision-stable-baseline-load.json)与[最终候选](../verification/runs/precision-candidate-load.json)均业务通过，[候选数据库断言](../verification/runs/precision-candidate-database.json)通过；资源、52 表初始行数、Schema、负载与计时协议相同，均无新增死锁 |
| Redis、Project 故障及写回滚 | [7 项检查通过](../verification/runs/runtime-batch-write-faults.json)；同 JWT 恢复、熔断、有界返回、连接释放、写原子回滚、显式重提和独立材料预留 |
| Project、Workflow 应用回退与恢复 | [回退/恢复通过](../verification/runs/runtime-batch-application-rollback-quiescent.json)，42 张业务表校验、模块状态、同 JWT 和全部 PDF 字节一致 |
| 五库备份恢复 | [52 表全部一致](../verification/runs/runtime-batch-fullsql-backup-restore.json)，仅恢复到新辅助 Schema；源库保留 |
| CI 配置与脚本 | 四作业 YAML 和 20 个 Python 脚本解析通过；远程 CI 未执行 |

首轮回退的公共状态和材料通过，但 `project_snapshot` 校验和变化，见[失败记录](../verification/runs/runtime-batch-application-rollback.json)。回退脚本增加五库 Outbox、RabbitMQ 待投递/未确认消息排空与连续稳定业务检查点；项目快照继续校验，重新演练通过。Notification 的 payload、接收人、状态和时间均纳入摘要，持续增长的投递重试计数单独记录并检查单调性。

旧时钟比较出现[性能门禁失败](../verification/runs/performance-runtime-batch-comparison.json)，报告保留。本机 Windows/Python 的 `monotonic()` 分辨率为 15.625 毫秒，大于门禁 5 毫秒误差限。请求计时已改为 `perf_counter()`，报告注明测量协议和精度，比较拒绝旧协议；两个冻结版本以同一新脚本、全新数据和相同资源重新测量。此修正不放宽验收阈值，不作为应用性能改善的证据。

高精度重测的早期 Mapper 基线[再次因授权 HTTP 500 失败](../verification/runs/precision-baseline-load.json)，[InnoDB 证据](../verification/runs/precision-baseline-deadlock-evidence.json)关联失败 traceId `80f72e2b-b5da-4f6b-b180-9c3caabfa264` 与竞争事务 `95c17d82-a0e4-4e62-b53a-c64813d46437`。这再次复现成员表尾部间隙锁死锁；该版本不满足比较所需的“两端业务通过”，不能用它宣称性能验收通过。阶段 5 改用上一阶段已验收的 `project-lock-candidate` 冻结构建为对照，验证后续运行视图优化；比较范围在报告中注明。

两个最终对照版本只有 Workflow 内容不同。高精度比较 mean 为 **53.84→51.99 毫秒**，p95 为 **104.131→97.306 毫秒**，按原 5 毫秒误差限通过。该结果只验证当前固定本地负载相对上一阶段冻结版本未退化，不证明原始生产版本的性能差异或生产容量。稳定基线和候选均完成至少 90 秒混合负载及完整业务，候选和基线数据库检查各 29 项。

已有通过与失败报告均保留在 `verification/runs/`；原始日志、环境文件、构建快照和 SQL 备份位于忽略提交的 `target/`。
本次创建的隔离栈验收后均停止，容器、卷和数据保留。最终证据与摘要索引见 [iteration-final-evidence.json](../verification/runs/iteration-final-evidence.json)。

## 发布边界与后续验收

- 生产数据库未切换，既有库不得重跑初始化或自动清库。先按接管手册完成备份、结构/数据核对和恢复，再批准发布；后续结构变化使用版本化 SQL 补丁。
- 保留必须保证即时授权及一致性的有界事务内调用，包括锁后权限复查和专家授权同步。生成单据仍存在事务内对象存储工作；进一步缩短需先设计持久化任务、幂等与孤儿对象补偿，不能通过移动方法破坏业务原子性。
- 当前本地负载为四个独立授权项目、24 成员整批、25 请求/秒、至少 90 秒，完整业务同步推进。生产数据规模、更长时间、更多并发及更广故障组合仍需按部署资源验收。
- CI 已配置 smoke、完整业务、MySQL 和容量故障门禁；缺 Docker、跳过或缺少新测试报告均失败。远程工作流尚未执行，需推送后取得真实记录。
- 本次改动尚未拆分或创建阶段提交。审查和发布应按目录分层、持久化、业务修复及验证脚本分批形成提交与发布清单；不能将一次本地通过当作生产接管完成。
