# 多实例启动、状态与验收

以下命令在 `SicProjectIS_cloud` 根目录执行。推荐使用 [统一 JAR 启动流程](unified-jar-startup.md)：生成配置、直接 Compose up、严格就绪检查。业务服务默认各一个实例，Eureka 默认两个独立容器。状态、停止及验收共用 `scripts/replica_common.py` 的配置解析。

## 支持范围

IAM、Project、Workflow、Material、Notification 可扩容；Gateway、Config Server、MySQL、Redis、RabbitMQ、MinIO 保持一个实例。Eureka 使用 `discovery-server`、`discovery-peer` 两个服务，每个固定一个容器，详见 [双节点配置与故障验收](eureka-ha.md)。五个业务服务共享 Docker MySQL 的五个 Schema、Redis、RabbitMQ、MinIO 和 JWT 密钥。禁止可扩容服务使用固定 `container_name`、宿主机网络或发布端口；数据库采用 Compose 网络中的 `mysql`，本入口不支持宿主机 MySQL。

默认共 13 个容器，五业务服务全双副本共 18 个。应用连接池上限为各服务 `DB_POOL_SIZE × 副本数` 之和，再加 20 个运维连接。直接 Compose 后的就绪检查会拒绝超出 MySQL `max_connections` 的部署并保留现场，不自动停止或回滚副本。默认池为 10，全双副本预计 100 + 20 个连接。此估算不是数据库性能承诺。

## 配置优先级

直接 Compose 从 env 中的五个副本变量读取 `scale`，未设置默认为 1；直接 Compose 不读取 JSON 或 Python 的 `--replicas` 参数。兼容 Python 启动器仍按服务逐项覆盖：`--replicas` → `--replica-config` JSON → 指定 env → 进程环境变量 → Compose 声明 → 1。就绪/状态/独立验收支持相同的 JSON 和参数，用于检查显式扩容的目标。

| 服务                 | 环境变量                  |
| -------------------- | ------------------------- |
| iam-service          | SIC_IAM_REPLICAS          |
| project-service      | SIC_PROJECT_REPLICAS      |
| workflow-service     | SIC_WORKFLOW_REPLICAS     |
| material-service     | SIC_MATERIAL_REPLICAS     |
| notification-service | SIC_NOTIFICATION_REPLICAS |

配置样例见 [`replicas.multi.example.json`](../replicas.multi.example.json)。只允许正整数，拒绝布尔值、0、未知服务或变量、重复 JSON 键和重复命令行服务项。基础设施和 Gateway 指定超过 1 会失败。

`--compose-file` 可重复，基础 `docker-compose.yml` 始终在首位，其后按参数顺序叠加。可同时叠加冻结 JAR、端口、资源约束等 override；所有操作必须使用相同项目名、凭据和 override。不要生成一套新凭据接管旧数据卷。

## 首次创建隔离环境

环境要求与本机/Docker 编译方式见 [README](../README.md)。本机冻结包示例：

```powershell
# 不执行 clean：target 内可能有环境凭据及冻结包。
mvn -B -pl '!platform/integration-tests' '-DskipTests' package
python scripts/snapshot_verification_release.py --release-dir target/releases/multi-demo --label multi-demo
python scripts/prepare_verification_env.py --env-file target/multi-demo.env --compose-override target/multi-demo.compose.yaml --gateway-port 8080 --release-dir target/releases/multi-demo

# 编辑 multi-demo.env 的五个 SIC_*_REPLICAS，同名行替换为目标值。
docker compose -p sic-multi-demo --env-file target/multi-demo.env `
    -f docker-compose.yml -f target/multi-demo.compose.yaml config --quiet
docker compose -p sic-multi-demo --env-file target/multi-demo.env `
    -f docker-compose.yml -f target/multi-demo.compose.yaml up -d --no-build --wait --wait-timeout 600
$deployment = @('--project-name', 'sic-multi-demo', '--env-file', 'target/multi-demo.env', '--compose-file', 'target/multi-demo.compose.yaml')
python scripts/wait_verification_ready.py @deployment
```

生成器首次拒绝覆盖已有配置；重复启动复用文件。部署设置更新用 `--update-override` 备份并更新 override，保留 env 和 JAR。新管理员凭据保存在该 env 的 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD`；不要提交到 Git。首次 IAM 多副本的管理员初始化由数据库角色行锁保护。

示例 Gateway 默认为 **8080**，前端沿用默认 `/api`→`http://127.0.0.1:8080` 代理即可，浏览器访问 5173。对外端口通过 env 中 `GATEWAY_PORT` 配置，生成器的 `--gateway-port` 指定初始值；修改已有 env 后直接 Compose up 更新端口，不重新生成凭据。历史 override 固定端口时用 `--update-override` 更新为 env 插值。

如果自行设置 `GATEWAY_PORT=18080`，前端需另行设置 `SIC_GATEWAY_PROXY_TARGET=http://127.0.0.1:18080`；使用默认 8080 时不要保留旧的 18080 进程变量或本地配置。详情见 [前端 README](../../SicProjectIS_fronted/README.md)。

Docker 构建镜像时移除 JAR override，不传 `--skip-build`：

```powershell
python scripts/start_backend.py --project-name sic-image-demo --env-file target/image-demo.env --replica-config replicas.multi.example.json --mode fresh --reset-confirmation RESET:sic-image-demo
```

须先创建有效的 `target/image-demo.env`，按照 README 环境生成方法填写全部密钥、基础设施凭据和管理员信息。默认宿主机端口来自基础 Compose；需要其他端口时提供自己的端口 override。镜像标识来自合并后的 Compose，冻结包按挂载文件与可用的 SHA-256 发布清单核对。

## 日常启动与调整副本

统一 JAR 入口：修改 env 的五个副本数后，执行原 Compose up，再执行 `python scripts/wait_verification_ready.py @deployment`。启用/关闭 Eureka 界面前执行 `prepare_verification_env.py --update-override --env-file 原env --compose-override 原override`，设置及端口见 [统一指南](unified-jar-startup.md)。

下面保留 Python 启动器的兼容用法，适用于 JSON/命令行副本覆盖或 Docker 内构建：

History 直接收敛至目标数量，不先缩成一个实例，不重建数据库，不更新已有管理员密码或角色。需要当前管理员凭据用于 smoke；Python 入口交互输入，PowerShell 入口可用 `-AdminCredential`，CI 使用 `SIC_STARTUP_ADMIN_USERNAME/PASSWORD`。

```powershell
python scripts/start_backend.py @deployment --mode history --skip-build --dry-run
python scripts/start_backend.py @deployment --mode history --skip-build

# 命令行覆盖 JSON 中的两个服务，其他服务保持 JSON 的双副本。
python scripts/start_backend.py @deployment --mode history --skip-build --replicas project-service=1 --replicas workflow-service=1
```

dry-run 解析 Compose 并只读查询 Docker，输出配置来源、变化、构建/启动命令；不启停容器，不提示重置确认。需要可访问的 Docker 引擎和有效配置。端口冲突按合并后的发布端口检查；Eureka/Config 无宿主机端口时通过容器内 HTTP 检查。

所有目标副本必须精确匹配、全部健康；Eureka 的实例 ID 必须唯一，UP 地址与端口必须对应实际容器。通过后继续等待 65 秒，给注册表响应缓存和默认 LoadBalancer 缓存传播留出时间；改变这些默认缓存周期时应相应核对就绪等待策略。失败不会清卷或自动恢复旧副本数量。

## 状态和停止

```powershell
python scripts/status_backend.py @deployment
python scripts/stop_backend.py @deployment --dry-run
python scripts/stop_backend.py @deployment

# PowerShell 包装，参数名称使用 PowerShell 风格。
.\scripts\status-backend.ps1 -ProjectName sic-multi-demo -EnvFile target/multi-demo.env -ComposeFile target/multi-demo.compose.yaml -ReplicaConfig replicas.multi.example.json
.\scripts\stop-backend.ps1 -ProjectName sic-multi-demo -EnvFile target/multi-demo.env -ComposeFile target/multi-demo.compose.yaml -WhatIf
```

状态显示全部副本及注册映射，缺失/不健康时退出码为 1。停止按项目及本仓库标签筛选所有运行容器，按 Gateway→业务→基础设施顺序停止，保留容器和数据卷；用同配置的 History 重启。本项目已停止容器的宿主机端口尚未释放时，端口预检最多等 20 秒，不绕过端口冲突检查。现有 `stop-all.ps1` 仍按原默认范围停止本仓库项目及相关本机进程，通过容器标签识别副本；只停止一个项目时使用新入口。

## 独立验收

在测试 env 中显式设置 `SIC_LB_EVIDENCE_ENABLED=true`，然后通过原 Compose up 更新应用使该开关生效。默认关闭；仅日志增加规范化 traceId、服务、容器 hostname、HTTP 方法、路径、状态、耗时，不记录请求体、query、Authorization 或凭据，不增加业务 API/响应字段。

```powershell
python scripts/verify_load_balancing.py @deployment --mode balance
python scripts/verify_load_balancing.py @deployment --mode business
python scripts/verify_load_balancing.py @deployment --mode fault
```

PowerShell 包装为 `verify-load-balancing.ps1 -Mode balance|business|fault`，支持相同部署参数及 `-AdminCredential`。Python 默认从原 `.env` 读取管理员凭据；如管理员已变更，提供 `SIC_E2E_ADMIN_USERNAME/PASSWORD`，不要把密码放在命令行。三个模式分别标记结果，启动 smoke 无法替代任何一种验收。

| 模式     | 行为与通过条件                                                                                                                                                                                                                  |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| balance  | 对五服务各最多`20 × 副本数` 次 Gateway 探测；SSE 收到 CONNECTED 后关闭。通过真实业务入口验证 Project→IAM、Workflow→Project 的 Feign 落点。每个目标容器都须有成功请求日志。                                                 |
| business | 在每个 Notification 副本建立 SSE 并验证同一业务事件投递；复用完整业务 runner 和空白 PDF、SQL 断言。自然调用须覆盖 Project→Workflow、Workflow→IAM/Material 的全部目标副本；最多`max(3, 最大副本数)` 个独立业务 runId。       |
| fault    | 依次停止每个多副本业务服务中的一个确定容器，观察错误窗口、Eureka 摘除和剩余实例的只读请求，再启动原容器并确认重新参与分流。默认请求 20 秒，摘除/重注册各 180 秒。写请求不自动重试。退出时恢复本次停止的容器，恢复错误独立报告。 |

完整业务包含注册、角色变更后重新登录、专家资格审批、申请→合同→验收、37 个用户节点、3 个双专家批次、17 类材料，以及既有越权、序号、历史评分和工作台回归。上传下载落点与内部材料调用可在证据中核对；MySQL 断言通过 `verify_business_db.py` 执行。未命中目标副本、依赖缺失、恢复失败均返回失败，不能用跳过获得通过。

启动清单位于 `verification/runs/startup-*.json`；独立验收位于 `verification/runs/lb-*/report.json`，包含配置、实例映射、请求落点、业务报告路径、故障时间线及恢复结果。使用 `--report-path` 可指定主报告，辅助报告仍保存在 runId 目录。日志保存在 `logs/`。测试账号、项目和材料保留，以报告中的 runId/ID 定位；凭据脱敏。需要 Docker/MySQL，H2 和静态检查不能替代。

## 并发保障与边界

- Saga 确认在本地事务中按 Project→Saga 加锁并复核状态；COMPLETED 直接返回。项目激活、Saga 完成及 Outbox 一起提交，失败登记和恢复重试只更新待确认状态。
- 管理员初始化先锁定已有 SYSTEM_ADMIN 角色行再读账号，仅创建缺失账号，不重设已有账户密码/角色。
- MinIO 初始化只容忍明确的 bucket 创建冲突，且重新查询 bucket 确实存在；其他错误继续失败。
- 材料版本写入、专家批次完成、消息幂等与 SSE 仍依赖数据库/Redis/消息机制。本轮以双副本和单副本退出验证实际行为，不承诺任意容量、绝对无死锁或完整依赖故障容错。

检查异常时先保留报告与容器日志，再核对全部副本、注册地址、连接池预算和配置。不要用 Fresh 清空已有业务库排障。
