# Eureka 双容器部署与故障验收

主 Compose 默认启动 `discovery-server` 与 `discovery-peer`，两个容器复用 `platform/discovery-server` 应用，启用 `ha` profile，互相注册、同步注册表。两个容器均监听 8761；JAR 部署默认隐藏宿主机入口，`EUREKA_UI_ENABLED=true` 时按 env 发布端口，默认本机 8761、8762。五个业务服务与 Gateway 均使用双地址。统一启动方式见 [JAR + Docker 指南](unified-jar-startup.md)。

本次独立测试环境的实测结果见 [双节点验收记录](eureka-ha-verification-report.md)。

```text
Gateway / IAM / Project / Workflow / Material / Notification
    defaultZone = http://discovery-server:8761/eureka/,http://discovery-peer:8761/eureka/
                       ↓                              ↓
             discovery-server ←── Peer 同步 ──→ discovery-peer
```

默认业务各一个副本时共 13 个容器、9 个 Java 进程；仍只有八个不同的 Java 发布包。五业务服务全部双副本时共 18 个容器。Eureka 节点通过两个明确的服务名管理，每个节点固定一个容器，不使用 `--scale discovery-server=2`。

单独运行 discovery-server JAR 时原 `application.yaml` 仍保留 standalone 配置。HA profile 使用可解析的不同 hostname、唯一 instance-id，开启 register/fetch 与自我保护。项目实际使用的 Spring Cloud Netflix 5.0.2 通过 `eureka.client.timeout` 绑定 HTTP 超时（毫秒），连接/连接池等待 2 秒，读取 5 秒。

业务和 Gateway 没有对单一 Eureka 节点的 Compose `service_healthy` 依赖：注册中心尚未就绪时客户端重试，一个节点故障时可用另一个节点注册。常规启动器的正常验收仍要求两节点健康、两份业务注册表一致，并核对每个业务实例的 IP、端口和容器映射；正常 HA 验收失败不会清理数据库。`status_backend.py` 分别报告节点注册表及 `healthy/degraded/unavailable`；降级时退出码为 1，便于告警。

## 已有环境升级

以下命令在 `SicProjectIS_cloud` 目录执行。保留原 env、JWT 密钥和四个数据卷，更新 override 后用原 Compose 命令升级。

对于本机 JAR 模式，重新打包八个应用，并冻结到一个**新的** release 目录。`--update-override` 读取原 env 中的部署设置，备份并更新已有 override，不改写 env：

```powershell
mvn -B -ntp -DskipTests package
python scripts/snapshot_verification_release.py --release-dir target/releases/eureka-ha-v1 --label eureka-ha-v1
python scripts/prepare_verification_env.py --update-override `
    --env-file target/local-dev.env `
    --release-dir target/releases/eureka-ha-v1 `
    --compose-override target/local-dev.compose.yaml

docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml up -d --no-build --wait --wait-timeout 600
python scripts/wait_verification_ready.py --project-name sic-localdev `
    --env-file target/local-dev.env --compose-file target/local-dev.compose.yaml
```

新 override 包含两个注册中心，它们挂载同一冻结 JAR，使用同一 manifest 条目校验摘要。历史生成器输出可以迁移，缺少 discovery-peer 时会补齐。只切换 UI 时省略 release-dir，保留原包路径；生成新的 override 可用 `--override-only --env-file 原env`。仅更新源码或 target JAR 不会替换旧冻结发布。

开启管理入口：在原 env 设置 `EUREKA_UI_ENABLED=true`，然后用相同 env/override 执行 `--update-override`，再执行 Compose up 和就绪检查。默认绑定 127.0.0.1，两个端口由 `EUREKA_UI_PORT_A/B` 配置；关闭时设 false 并重复更新/启动。界面开关不影响容器网络内的注册与 Peer 同步。

Docker 内构建模式继续使用原 env 与项目名，以 History 启动，省略 `--skip-build` 即重建镜像：

```powershell
python scripts/start_backend.py --mode history --project-name sic-cloud --env-file .env
```

## 独立故障验收

在独立测试项目执行，脚本会暂时停止两个注册中心中的一个。默认会用当前管理员执行业务读请求。可通过 `--admin-username` 和环境变量 `SIC_STARTUP_ADMIN_PASSWORD` 提供当前凭据；省略时使用测试 env 中的引导凭据。请勿将已变更的引导密码当作当前密码。

```powershell
$deployment = @('--project-name', 'sic-ha-test', '--env-file', 'target/ha-test.env',
    '--compose-file', 'target/ha-test.compose.yaml')
python scripts/verify_eureka_ha.py @deployment --report-path verification/runs/eureka-ha.json
```

该脚本包含：

1. 两节点正常健康、注册表收敛和业务实例映射检查。
2. 只配置 A 地址的临时真实 Eureka 客户端注册后，在 B 查询同一实例；反向重复，验证 Peer 同步而非客户端双地址造成的假象。
3. `docker stop --time 0` 终止 A 的进程并保持容器停止，避免重启策略立即掩盖故障。用一个全新客户端将故障地址放在首位、正常地址放在第二位，验证新实例在正常节点注册、初次注册表拉取成功，并从客户端容器实际调用发现的 Project 实例 readiness 地址；反向重复。
4. 故障期间持续采样 `/api/auth/me`、`/api/projects`、`/api/workflow-definitions`，保存状态、traceId、成功率；最后三个样本必须成功。默认至少观察 65 秒，覆盖已有客户端的默认 30 秒拉取与心跳任务。
5. 恢复故障节点，确认故障期间的新实例同步到恢复节点，再验证两份业务注册表重新一致。

临时客户端复用 discovery-server 的镜像/JAR，改用独立应用名 `sic-ha-probe`；其服务端启动通过 `syncUp` 将本次真实 DiscoveryClient 的初次拉取结果放入注册表，因此可以检查冷启动发现。临时端口随机映射到 127.0.0.1，脚本退出时恢复停止的节点并删除自己创建的 probe，保留业务容器及数据卷。报告记录部署文件、JAR SHA-256、两个方向的同步/故障/恢复证据与清理错误。报告 `passed` 不代表全过程零错误，业务成功率和请求明细需单独评估。

仅启动两个注册中心时，可使用 `--registry-only` 验证同步、真实客户端故障切换与恢复；该模式明确将 `businessContinuityChecked` 置为 false，不能作为业务连续性验收。

GitHub Actions 的 `capacity-and-faults` job 已加入完整 HA 验收，并将 `ci-eureka-ha.json` 随其他故障报告上传。CI 环境生成器显式使用 18080，与现有业务验收地址一致；本地生成器默认仍为 8080。

```powershell
docker compose --env-file target/ha-test.env -p sic-ha-test `
    -f docker-compose.yml -f target/ha-test.compose.yaml `
    up -d --no-build --wait discovery-server discovery-peer
python scripts/verify_eureka_ha.py @deployment --registry-only
```

## 范围与运行注意事项

同一 Docker 引擎中的双容器可以容忍单个 Eureka 进程/容器故障；宿主机或 Docker 引擎故障仍会同时影响两个节点。正式部署需要跨宿主机/可用区，改为跨主机可达的 Peer 与客户端地址；Compose 服务名只在当前 Docker 网络内解析。

HA 开启自我保护，异常断线时可能保留旧节点或实例记录。注册表中的旧记录不代表进程健康，故障期间以存活容器、真实注册和调用证据判断；不要为了加快演练而在生产关闭自我保护。注册中心双节点也不会使单节点 MySQL、Redis、RabbitMQ、MinIO 或 Gateway 自动获得高可用。

参考：[Spring Cloud Netflix](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html)、[Netflix Peer 通信与自我保护](https://github.com/Netflix/eureka/wiki/Understanding-Eureka-Peer-to-Peer-Communication)。
