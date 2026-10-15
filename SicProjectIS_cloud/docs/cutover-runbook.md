# 单体到五服务切换手册

本手册用于一次性生产切换。脚本不会删除旧库、旧上传目录、旧镜像或备份，也不会执行 `docker compose down -v`。在完成验收前，不要清理这些回滚点。

## 1. 切换前准备

1. 在旧后端仍可用时完成一次全量演练，并记录耗时。
2. 将 `.env.example` 复制为 `.env`，替换所有 `CHANGE_ME`，使用 `scripts/generate-jwt-keys.ps1` 生成 RSA 密钥。切换脚本会在启动容器前校验私钥、公钥均为合法 Base64 且属于同一密钥对。
3. 确保 Docker Compose v2、Docker Engine、PowerShell、`curl.exe` 可用。
4. 预先构建并测试：

   ```powershell
   mvn verify
   python scripts/build_full_schema.py --check
   python scripts/assert_verification_gate.py --gate mysql
   docker compose --env-file .env config --quiet
   docker compose --env-file .env build
   ```

5. 记录旧后端镜像版本、旧数据库地址和旧 uploads 绝对路径。

旧库转储必须是单库内容，不得带 `CREATE DATABASE` 或 `USE`，并应包含表结构和数据。例如：

```powershell
mysqldump --single-transaction --routines --triggers --no-create-db --default-character-set=utf8mb4 `
  -h <legacy-host> -u <legacy-user> -p research_project_management > sic-legacy.sql
```

## 2. 正式切换

进入维护窗口后：

1. 停止旧后端，确认不再有写流量；前端暂时显示维护页或停止入口流量。
2. 完成停机后的最终数据库转储，并冻结旧 uploads 目录。
3. 从云端工程根目录执行：

   ```powershell
   ./scripts/cutover.ps1 `
     -LegacyDump C:\cutover-input\sic-legacy.sql `
     -LegacyUploadsPath C:\cutover-input\uploads `
     -EnvFile .\.env `
     -BackupDirectory C:\cutover-backups `
     -LegacyBackendStopped
   ```

脚本依次执行以下动作：

- 将最终 SQL 和 uploads 压缩包复制到带时间戳的备份目录，并记录 SHA-256；
- 启动基础设施，把旧库导入隔离的 `sic_legacy`；
- 在空 volume 上由 MySQL 初始化五库完整 SQL；启动业务服务前校验 52 张表的最终结构；
- 校验旧文件哈希并以确定性对象键上传 MinIO；
- 在同一 MySQL 实例内事务导入五个 Schema；
- 比对 35 组行数、19 组孤儿引用以及流程当前序号；
- 重新下载全部迁移对象，逐字节核对大小和 SHA-256；
- 启动五服务和 Gateway，并验证 readiness 与未认证请求的 401 行为。

迁移期间只允许 root 账号运行跨 Schema SQL。服务运行时仍各自使用独立数据库账号，代码中没有跨 Schema Mapper、外键或查询。

## 3. 业务验收

脚本成功后，至少完成以下人工验收再解除维护窗口：

- 使用原账号登录，确认登录响应仍含 `token`、`expiresAt`、`user`；
- 分别以负责人、部门管理员、科技处、专家、财务账号验证项目列表和待办范围；
- 打开申请、合同、验收各一条历史流程，核对状态时间线、当前节点、任务和表单快照；
- 下载至少一个中文文件名材料并核对内容；
- 发起一个新项目申请，完成一次状态迁移，确认同步响应结构未变化；
- 打开两个浏览器验证 `MODULE_STATE_CHANGED` SSE 推送；
- 查看 Actuator/Prometheus，确认没有死信、积压 Outbox 或长期未完成 Saga。

时间戳备份目录中的 `database-verification.txt`、Gateway 响应和备份清单应与验收记录一起归档。

## 4. 失败处理与重试

任一步骤失败时保持旧入口关闭，不要继续手工跳步。SQL 导入在一个本地 MySQL 事务内执行；文件上传和 `sic_legacy` staging 会保留，便于调查。

发起申请时若 Workflow 已提交、但 Project 因网络中断没有收到响应，Project 会按同一 `sagaId` 查询或重复执行幂等补偿。补偿可以安全重复，并只删除目标 Saga 的流程数据。由于 Outbox 可能已在响应丢失前投递，浏览器可能短暂收到一次随后已不存在的 `MODULE_STATE_CHANGED`；前端按原有行为重新加载数据后应以服务端结果为准。排障时使用日志中的 `sagaId` 和 `correlationId` 联查 Project Saga、Workflow 实例与 Outbox/消费记录，不要手工删除消息。

脚本为防止误覆盖，会在以下情况停止：目标业务表已有数据、`sic_legacy` 已有表、文件清单不完整、旧数据唯一键冲突、业务表缺少流程实例，或生成单据缺少文件。修复源数据后，使用新的空五 Schema/Compose volume 重新演练。确需复用环境时，只能在确认时间戳备份可读后，由数据库管理员明确清理 `sic_legacy` 和五个目标业务 Schema；不要删除 Flyway 表后继续拼接执行。

## 5. 回滚

若在开放新系统写流量前验收失败：

1. 停止 Gateway 和五个新服务，但保留 MySQL、MinIO volume 与日志；
2. 将入口切回已记录版本的旧后端；
3. 继续使用从未被修改的旧数据库和冻结 uploads；若旧存储损坏，则用时间戳目录中的 SQL 和 uploads ZIP 恢复；
4. 验证旧系统登录、项目列表、流程详情、材料下载后解除维护窗口。

若新系统已经接受写入，直接回滚会丢失切换后的新数据。本版本没有自动反向同步工具，此时必须重新进入维护窗口，导出五个 Schema/RabbitMQ 未消费状态和 MinIO 新对象，完成差异核对后才能决定人工回迁或修复后前滚。

至少在一个完整验收周期内保留：旧数据库、旧 uploads、旧后端镜像、最终输入备份、`sic_legacy`、五 Schema 备份和 MinIO volume。

## 6. 死信调查与恢复

Prometheus 指标 `sic_events_dead_queued` 大于 0 时，先在 RabbitMQ 管理页检查 `sic.events.dead.q` 的 `x-death`、异常日志和对应业务数据，修复导致消费失败的原因。不要直接清空或移动死信队列。

恢复工具是一次性 Compose profile，默认拒绝执行。确认消费者故障已修复后，按小批次重放：

```powershell
$env:DLQ_REPLAY_CONFIRM = 'REPLAY_DEAD_LETTERS'
$env:DLQ_REPLAY_MAX_MESSAGES = '10'
docker compose --env-file .env --profile operations run --rm dlq-recovery-tool
Remove-Item Env:DLQ_REPLAY_CONFIRM
Remove-Item Env:DLQ_REPLAY_MAX_MESSAGES
```

工具只接受当前系统已知的业务路由，从 `x-death` 恢复原 routing key。每条消息使用 mandatory publish 和 publisher confirm；确认成功且未被退回后才 ACK 原死信。路由缺失、路由不在白名单、消息无法路由或发布确认失败时，当前死信会重新入队并立即停止本批处理。消费者仍以 `eventId` 去重，因此重复重放不会重复提交已成功的业务事务。

## 7. Compose 自动验收

全栈启动完成后，使用管理员凭据执行自动验收，并归档 JSON 报告：

```powershell
$adminCredential = Get-Credential -UserName admin
./scripts/verify-compose-e2e.ps1 `
  -ProjectName sic-cloud `
  -EnvFile .\.env `
  -AdminCredential $adminCredential `
  -ReportPath .\verification\compose-e2e.json
```

脚本以非零退出码报告失败，并依次验证：

- MySQL、RabbitMQ、Redis、MinIO、Eureka、Config Server、Gateway 和五个业务服务均为 `healthy`；
- Config Server 能返回公共配置，六个对外应用均已注册到 Eureka；
- Gateway 生成 `X-Trace-Id`，未登录访问返回原 `{success,data,message}` 401 包装；
- 管理员登录响应仍包含 `token`、`expiresAt`、`user`，JWT 具备 issuer、audience、jti、authVersion 和角色；
- IAM 当前用户、项目列表、Workflow 工作台、Material 404 和管理概览均经 8080 正确路由，其中管理概览同时验证 Project 到 IAM 的服务 Token 调用；
- SSE 查询参数 Token 能转换为下游认证并收到 `CONNECTED`；
- 注销后原 JWT 的 jti 立即被 Gateway 通过 Redis 黑名单拒绝。

该检查是上线前自动门禁，不替代第 3 节的多角色、历史流程、真实文件和完整状态迁移人工验收。


## 8. 完整 SQL 接管与恢复

五库最终结构位于 `infra/mysql/schema/`，IAM 种子和工作流资产位于 `infra/mysql/seed/`。
`schema-manifest.json` 固定文件摘要和表清单，`schema-signature.json` 固定真实 MySQL 的列、索引和约束。
接管前必须通过旧 Flyway 迁移链与完整 SQL 的结构等价测试，业务服务运行时不再执行 Flyway。

只有空 volume 才执行 MySQL 初始化脚本。已有数据库先运行 `verify_schema.py`；结构不同则停止接管，禁止重跑建表、清库或删除历史迁移表。
旧库导入后仅执行 `workflow-missing-assets.sql` 补齐缺失的流程类型，保留已有定义 ID、版本、发布状态和自定义模型。
后续变更按 `infra/mysql/patches/README.md` 使用不可变的版本化 SQL 补丁和发布清单。

数据库恢复与应用版本回退分别演练。恢复前停止 Gateway 和所有业务服务，备份五库、Redis 会话版本与 MinIO；恢复到新的隔离库并核对行数、校验和、文件摘要及消息幂等记录。
以下脚本只允许恢复到新建的隔离辅助库，不覆盖源库。备份含业务数据，保留在受限 `target/` 目录，不上传 CI artifact：

```powershell
python scripts/verify_backup_restore.py --project-name sic-verification --env-file target/verification.env `
  --compose-file docker-compose.yml --compose-file target/verification.compose.yaml
```

数据库恢复可能降低 `auth_version`。禁止降低 Redis 中已发布的权限版本；正式恢复应更换 JWT 签名密钥并让用户重新登录，使用新会话命名空间或经过审计的会话恢复步骤拒绝全部旧 token。
恢复消息时同时保留 Outbox/Inbox 的事件 ID 和处理记录，避免重复创建材料、通知和后继模块。

应用回退使用 `snapshot_verification_release.py` 冻结八个 JAR 及 SHA-256。`prepare_verification_env.py --release-dir` 校验摘要后挂载该版本。
仅在对应 SQL 补丁声明兼容且真实回归通过时允许回退应用；旧 Flyway 应用能否直接接管完整 SQL 库不能靠推断认定。

持续验收区分 smoke、完整业务、真实 MySQL 和混合负载/依赖故障。缺少 Docker、强制测试跳过或采样不完整均判为未验收。
CI 配置存在不代表 CI 已运行；必须归档实际运行报告。
