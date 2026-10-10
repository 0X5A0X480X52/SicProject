# 自动化业务与稳定性验收

所有命令从 `SicProjectIS_cloud` 执行。需要 Java 21、Maven、Python、Docker Linux 引擎及 Compose 2.28 或更新版本；Python 依赖在 `scripts/requirements-verification.txt`。
管理员和服务凭据由环境文件提供，测试账号按 `runId` 新建。报告只保留状态、ID、耗时、traceId、断言和指标；环境文件、原始日志和 SQL 备份保存在受限 `target/`，不上传 CI artifact。

## 新建隔离环境

```powershell
docker info
mvn verify
python scripts/assert_verification_gate.py --gate mysql
python scripts/build_full_schema.py --check
pip install -r scripts/requirements-verification.txt
python scripts/prepare_verification_env.py --env-file target/verification.env --compose-override target/verification.compose.yaml
docker compose -p sic-verification --env-file target/verification.env -f docker-compose.yml -f target/verification.compose.yaml up -d --no-build --wait --wait-timeout 240
python scripts/verify_schema.py --project-name sic-verification --env-file target/verification.env --compose-file docker-compose.yml --compose-file target/verification.compose.yaml
python scripts/wait_verification_ready.py --project-name sic-verification --env-file target/verification.env --compose-file docker-compose.yml --compose-file target/verification.compose.yaml --gateway-base-url http://127.0.0.1:18080
```

生成配置拒绝覆盖已有文件。重复执行应换新的环境文件、端口和 Compose 项目名；不要清空已有库。
新 volume 执行五库完整 SQL 和发布种子。已有库先校验最终结构。等待脚本除容器健康外还检查 Eureka 注册、Gateway 路由及 Material 到 Project 的内部访问。

## 完整业务与拒绝分支

```powershell
python scripts/verify_business_lifecycle.py --env-file target/verification.env --gateway-base-url http://127.0.0.1:18080 --negative-cases --report-path verification/runs/business.json
python scripts/verify_business_db.py --business-report verification/runs/business.json --project-name sic-verification --env-file target/verification.env --compose-file docker-compose.yml --compose-file target/verification.compose.yaml --report-path verification/runs/business-db.json
python scripts/verify_business_branches.py --env-file target/verification.env --gateway-base-url http://127.0.0.1:18080 --report-path verification/runs/branches.json
python scripts/verify_business_db.py --business-report verification/runs/branches.json --project-name sic-verification --env-file target/verification.env --compose-file docker-compose.yml --compose-file target/verification.compose.yaml --report-path verification/runs/branches-db.json
```

主用例注册六个账号，经管理员分配角色、真实申请和审批取得两名专家资格，再完成申请、合同和验收的 37 个用户节点、三个双专家评审批次及 17 类必填材料。
每次读取最新运行时 ID 和 `expectedSeq`，提交与前端一致的表单嵌套；第一名专家评分不能推进，第二名评分后等待自动推进。自动后继模块由业务创建。
上传夹具是 `output/pdf/blank-upload.pdf`。各上传版本单独保存 ID、核对下载 SHA-256、业务引用和预留确认。
拒绝分支增加跨部门资格审批、两次退回重提、旧轮次评分和验收失败终态。历史评分批次保留，新轮次使用新批次。

## 混合负载与故障

```powershell
python scripts/verify_mixed_load.py --env-file target/verification.env --gateway-base-url http://127.0.0.1:18080 --workers 4 --extra-members 24 --requests-per-second 25 --minimum-load-seconds 90 --stability-project-name sic-verification --stability-compose-file docker-compose.yml --stability-compose-file target/verification.compose.yaml --report-path verification/runs/load.json
python scripts/verify_business_db.py --business-report verification/runs/load.json --project-name sic-verification --env-file target/verification.env --compose-file docker-compose.yml --compose-file target/verification.compose.yaml --report-path verification/runs/load-db.json
python scripts/verify_dependency_faults.py --project-name sic-verification --env-file target/verification.env --compose-file docker-compose.yml --compose-file target/verification.compose.yaml --gateway-base-url http://127.0.0.1:18080 --business-report verification/runs/load.json --confirm-isolated-faults --require-write-fault --report-path verification/runs/faults.json
```

四个独立项目持续接收整批成员、专家授权和撤销请求，同时主项目跑完整业务流。采样覆盖 SQL 锁等待、事务、死锁计数、Hikari、Feign 熔断、Redis 和消息队列；采样不完整或产生新死锁即失败。
总请求频率受限，以便识别业务和数据库瓶颈。此配置验证一个固定的本地负载，不代表生产峰值容量。

故障脚本只停止指定隔离项目的 Redis 和 Project。Redis 故障返回 503，恢复后同一 JWT 可继续访问。
Project 故障触发熔断，并检查连接释放。开启写故障后，使用负载留下的项目通过公开接口进入审批节点，验证失败流转的状态、任务、表单、材料引用和 Outbox 全部回滚。
依赖恢复后只显式重新提交一次，验证提交一次、材料预留独立确认。脚本不自动重试写请求；最终尝试恢复所有被停止的服务。

## 固定构建比较与回退

`snapshot_verification_release.py --release-dir target/releases/<name> --label <name>` 冻结八个已打包 JAR 和摘要，拒绝覆盖；`prepare_verification_env.py --release-dir` 验证文件后挂载该版本。
性能比较分别使用两个全新 volume，依次启动同一套资源、堆大小、连接池和负载参数；停止另一套服务再测量。用 `record_performance_environment.py` 记录 52 张表的精确初始行数和资源。
`compare_performance_runs.py` 要求环境、初始数据、Schema、负载、完成时长、业务结果和采样可比，再比较 mean、p95。默认仅允许 5 毫秒的本地测量误差，并在报告中明确记录。
请求耗时使用高精度 `perf_counter()`，报告包含时钟与分辨率；比较门禁拒绝缺少测量协议、两端协议不同或精度超过 1 毫秒的报告。本机 Windows 的 `monotonic()` 分辨率为 15.625 毫秒，早期报告保留作历史记录，正式比较需用同一新脚本重新测量两个冻结版本。
JAR 内容摘要排除 ZIP 时间戳和条目顺序，文件完整性仍使用原始 SHA-256。

`verify_application_rollback.py` 使用 `--baseline-release`、`--candidate-release` 和通过的 `--business-report`，在显式隔离确认后回退变更的业务服务并恢复候选版本。
它要求 Schema 摘要相同，检查同一 JWT、项目终态、全部模块序号、所有材料字节和业务表校验和。
开始回退前要求五库 Outbox 与 RabbitMQ 待投递/未确认消息全部排空，并取得连续稳定的业务检查点；180 秒内不稳定则验收失败。异步项目快照仍计入业务校验，避免把演练前尚在处理的写入误算为部署改动。
通知内容、收件人、递送状态和时间逐项进入摘要；后台递送的 `retry_count` 单独记录并校验单调增长。共享配置或注册中心变化需要独立发布顺序，本脚本明确拒绝。
数据库恢复另用 `verify_backup_restore.py`；先停止业务服务，只恢复到新辅助库，保留源库并核对每表行数与校验和。

## CI 与报告

仓库工作流 `.github/workflows/cloud-verification.yml` 分开执行 smoke、真实 MySQL、完整业务与分支、混合负载及依赖故障。
强制测试缺少 Docker、报告含跳过、源码测试缺少新运行结果、业务断言失败或采样不完整都会使门禁失败。
本地测试可通过 `--started-at` 限制 JUnit 报告的新鲜度，避免包迁移后旧报告被重复计入。
CI 上传脱敏 JSON 和 JUnit；不上传凭据或 SQL 备份。工作流配置通过校验与本地执行不能代替远程 CI 实际运行记录。
