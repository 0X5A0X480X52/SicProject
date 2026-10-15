# 统一 JAR + Docker 启动流程

本指南中的命令在 `SicProjectIS_cloud` 目录执行。统一入口为 `prepare_verification_env.py` → `docker compose config/up` → `wait_verification_ready.py`。运行设置在同一 env 中管理，不需要通过 `start_backend.py` 启动 JAR 部署。

## 首次编译、冻结与配置

```powershell
mvn -B -DskipTests package
$releaseDir = "target/releases/local-dev-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
python scripts/snapshot_verification_release.py --release-dir $releaseDir --label local-dev
python scripts/prepare_verification_env.py `
    --env-file target/local-dev.env --compose-override target/local-dev.compose.yaml `
    --gateway-port 8080 --release-dir $releaseDir
```

首次生成随机密码及 RSA 密钥；默认业务各单副本，Eureka 两节点、管理入口关闭。八个不同的 JAR 运行于默认九个 Java 容器，其中两个 Eureka 共用同一包。生成器拒绝覆盖已有 env 或 override；已有配置不能通过删除凭据重建来更新。

在本地编辑器中按需修改 env 的以下同名行，不重复追加：

| 变量 | 默认 | 含义 |
|---|---|---|
| SIC_IAM_REPLICAS | 1 | IAM 容器数 |
| SIC_PROJECT_REPLICAS | 1 | Project 容器数 |
| SIC_WORKFLOW_REPLICAS | 1 | Workflow 容器数 |
| SIC_MATERIAL_REPLICAS | 1 | Material 容器数 |
| SIC_NOTIFICATION_REPLICAS | 1 | Notification 容器数 |
| GATEWAY_PORT | 8080 | Gateway 的宿主机端口 |
| DB_POOL_SIZE | 10 | 每个业务副本的最大数据库连接数 |
| EUREKA_UI_ENABLED | false | 是否发布两个 Eureka HTTP 端口；仅 true/false |
| EUREKA_UI_BIND_IP | 127.0.0.1 | 宿主机 IPv4/IPv6 绑定地址 |
| EUREKA_UI_PORT_A | 8761 | discovery-server 宿主机端口 |
| EUREKA_UI_PORT_B | 8762 | discovery-peer 宿主机端口 |

副本数为正整数；Gateway 和基础设施各一个，Eureka 为两个独立命名节点，不使用单节点 scale 扩容。每个容器独立运行同一个服务的冻结 JAR。数据库连接池预算为五服务 `DB_POOL_SIZE × 副本数` 的总和加 20 个运维连接。

若把 UI 开关设为 true，先执行：

```powershell
python scripts/prepare_verification_env.py --update-override `
    --env-file target/local-dev.env --compose-override target/local-dev.compose.yaml
```

更新会读取原 env、复用原 JAR 路径，旁边生成 `.backup-时间-随机后缀` 备份后原子替换 override，env 字节保持不变。两个 UI 端口与 Gateway 端口必须不同，均为 1024–65535 的固定端口；不开随机端口。`127.0.0.1` 允许本机浏览器访问，`0.0.0.0` 绑定所有宿主机接口；该映射同时发布 Eureka 界面和注册 API。

## 启动与严格就绪检查

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml config --quiet
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600
python scripts/wait_verification_ready.py `
    --project-name sic-localdev --env-file target/local-dev.env `
    --compose-file docker-compose.yml --compose-file target/local-dev.compose.yaml `
    --gateway-base-url http://127.0.0.1:8080 `
    --report-path verification/runs/local-dev-readiness.json
```

Compose 从五个 `scale: ${SIC_*_REPLICAS:-1}` 字段读取业务数量，无需显式 `--scale`。同一 env、项目名、override 必须用于全部命令。不要在终端保留与该 env 冲突的同名部署变量：直接 Compose 的插值优先使用进程环境；就绪检查以指定 env 验证期望值，冲突会导致验收失败。

就绪检查要求目标副本数量精确匹配且全部健康、实例 ID 唯一、注册地址/端口对应实际容器、两份业务注册表一致、两个 Eureka 均为 UP。默认等待上述条件持续 65 秒，再检查实际运行容器的宿主机端口绑定、数据库连接池预算、管理员登录和四个公开读路由。可用 `--timeout` 延长总等待预算；默认 300 秒。报告保存副本来源、冻结包摘要、两节点注册表、UI 绑定和路由状态。失败退出码 1，保留容器和数据卷。

默认用 env 中的引导管理员进行初次验收。账号密码已经变更时，用 `--admin-username` 指定当前账号，密码通过 `SIC_STARTUP_ADMIN_PASSWORD` 环境变量提供；不要把密码放在命令参数或改写数据库凭据。省略 `--gateway-base-url` 时从合并后的 Compose 推断 Gateway 地址。

## 调整副本、界面与发布包

| 操作 | 所需步骤 |
|---|---|
| 日常启动 | 原 Compose up → 就绪检查 |
| 修改五个业务副本数 | 编辑 env → 原 Compose up → 就绪检查 |
| 切换 EUREKA_UI_ENABLED | 编辑 env → 生成器 --update-override → 原 Compose up → 就绪检查 |
| 已启用 UI 时修改端口或 bind IP | 编辑 env → 原 Compose up → 就绪检查；字段使用 env 插值 |
| 更换冻结发布包 | 冻结到新目录 → 生成器 --update-override --release-dir 新目录 → 原 Compose up → 就绪检查 |

更新发布包示例：

```powershell
python scripts/prepare_verification_env.py --update-override `
    --env-file target/local-dev.env --compose-override target/local-dev.compose.yaml `
    --release-dir $releaseDir
```

只切换 UI 时不必再次传 release-dir，不会切换到子模块 target 下的可变 JAR。历史生成器输出也可迁移，前提是能够完整识别八个应用的 JSON 格式只读 JAR 挂载；无法识别时要求显式指定冻结 release，避免猜测包路径。`--override-only` 用于生成一个新的 override，可读取现有 env 的部署设置，但不覆盖 env 和已存在的 override。`--gateway-port` 仅影响首次新建 env，更新环境的端口以原 env 为准。

设置开关后只执行 Compose 而未重新生成 override，或重新生成后未执行 up，就绪检查都会指出 UI 配置/运行绑定不一致。生效需要实际重新部署，不需要重新编译 JAR 或生成密钥。

本机前端继续使用 5173 和同源 `/api`→8080 代理。启用默认 Eureka 端口时访问 `http://127.0.0.1:8761/`、`http://127.0.0.1:8762/`。更换 Gateway 端口时同时调整前端 `SIC_GATEWAY_PROXY_TARGET` 和显式就绪检查 URL。

## 验证与停止

```powershell
python -m unittest discover -s scripts/tests -p 'test_*.py'
# 故障验收使用另建的独立测试项目。
python scripts/verify_eureka_ha.py --project-name sic-ha-test `
    --env-file target/ha-test.env --compose-file target/ha-test.compose.yaml
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml stop
```

普通停止不删除容器或数据卷。Eureka 固定双节点的故障流程见 [HA 指南](eureka-ha.md)。统一启动路径的实测记录见 [验收记录](unified-jar-startup-verification.md)。
