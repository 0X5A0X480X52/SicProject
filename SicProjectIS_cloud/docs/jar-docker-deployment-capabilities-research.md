# 本机 JAR + Docker 启动流程能力调研

> 以下保留实现前的调研结论。业务副本 env、Eureka 界面开关、保留凭据更新 override 和严格就绪验收现已实现；当前用法以 [统一启动指南](unified-jar-startup.md) 为准。

调研日期：2026-10-14。对象为 README 第 3 节的冻结 JAR、生成 env/Compose override、直接执行 `docker compose up` 流程，以及本次增加的 Eureka 双节点配置。此次仅读取代码、核对官方文档和解析 Compose 配置，没有启停容器或修改运行配置。

## 结论

| 需求 | 当前项目能力 | README 第 3 节命令的实际行为 |
|---|---|---|
| 五个业务 service 分别指定多个副本 | 启动器支持 env、JSON、命令行；直接 Compose 支持显式 `--scale` | 当前 Compose 未引用副本 env，直接 `up` 不会读取 `SIC_*_REPLICAS`，默认各一个 |
| 多个 Eureka 实例 | 已有两个 Peer 节点，复用一个 discovery-server 发布包 | 默认启动 discovery-server 与 discovery-peer，各一个；不支持单服务 N 副本或任意节点数 |
| env 开关控制浏览器访问 Eureka | 尚未实现 | JAR override 清空两节点宿主机端口；任意 env 开关均不会自动恢复绑定 |

Gateway 不在可扩容的五个业务服务之列，目前固定单实例。冻结 JAR 可以被多个容器只读挂载，本机编译和冻结包机制本身不限制业务副本数；每个容器有自己的 Java 进程。

## 1. 业务副本：已有启动器支持，直接 Compose 尚未连接 env

`scripts/replica_common.py` 的 `resolve_replicas()` 支持：

```dotenv
SIC_IAM_REPLICAS=2
SIC_PROJECT_REPLICAS=3
SIC_WORKFLOW_REPLICAS=2
SIC_MATERIAL_REPLICAS=2
SIC_NOTIFICATION_REPLICAS=2
```

解析优先级为命令行 `--replicas`、`--replica-config`、指定 env、进程环境变量、Compose 声明、默认 1。`start_backend.py` 再将解析出的数量转成 Docker Compose `--scale` 参数，并检查连接池预算、所有容器健康以及两份注册表中的逐实例映射。

而 README 第 3 节直接执行 Compose，没有调用此解析器；当前 `docker-compose.yml` 和生成的 JAR override 均没有 `scale: ${SIC_...}` 或 `deploy.replicas` 声明。仅向 `target/local-dev.env` 添加这些变量不会改变直接 `up` 的默认副本数。

已有环境可以立即采用以下路径，不重新生成凭据或清理数据卷：

```powershell
# 在原 target/local-dev.env 中设置五个副本变量后执行。
python scripts/start_backend.py --mode history --skip-build `
    --project-name sic-localdev --env-file target/local-dev.env `
    --compose-file target/local-dev.compose.yaml
```

启动器要求当前管理员账号密码。对于首次创建的环境，可以先按 README 初始化单副本，再用 History 扩容。若直接给 `docker compose up` 添加五个 `--scale` 参数，则应把相同目标数量传给后续项目级状态/验收入口，避免目标副本配置不一致。

若要保留原直接 Compose 流程并实现 env 驱动，建议在五个业务服务中分别补充：

```yaml
services:
  iam-service:
    scale: ${SIC_IAM_REPLICAS:-1}
  project-service:
    scale: ${SIC_PROJECT_REPLICAS:-1}
  workflow-service:
    scale: ${SIC_WORKFLOW_REPLICAS:-1}
  material-service:
    scale: ${SIC_MATERIAL_REPLICAS:-1}
  notification-service:
    scale: ${SIC_NOTIFICATION_REPLICAS:-1}
```

这是待实施方案。Compose 官方说明 `scale` 定义服务的默认容器数量；env 必须被 Compose 字段引用才会参与配置插值。[Docker scale 文档](https://docs.docker.com/reference/compose-file/services/#scale)、[env 插值文档](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)。

README 使用的 `wait_verification_ready.py` 当前只要求每种应用至少一个 UP 实例，并要求两节点注册表一致；它不会验证 env 指定的精确副本数。它可以辅助验证路由就绪，但需要配合启动器或 `verify_compose_e2e.py` 的逐实例验收。直接 Compose 也不会执行项目启动器的数据库连接池预算检查。

## 2. Eureka：支持固定双节点，尚不支持按数量自动扩容

当前是两个 Compose 服务：

| 服务 | hostname / instance-id | Peer |
|---|---|---|
| discovery-server | discovery-server / discovery-server:8761 | discovery-peer:8761 |
| discovery-peer | discovery-peer / discovery-peer:8761 | discovery-server:8761 |

两个节点均启用 HA profile，Gateway 和业务服务配置双注册中心地址。使用新生成的 JAR override 时，两节点挂载同一 discovery-server JAR。已有历史 override 缺少 discovery-peer 时，需要按 [HA 指南](eureka-ha.md) 生成包含新节点的 override。

项目启动器明确拒绝 `--replicas discovery-server=2`。即使直接绕过启动器执行 Compose `--scale discovery-server=2`，也会复制固定 hostname、instance-id 和 Peer 配置；基础 Compose 还会有固定宿主机端口冲突。JAR override 隐藏端口后仍然存在 Eureka 身份重复问题，不能当作有效集群。

如果要求“多个”指两个 Eureka 进程，当前已满足；如果要求通过 env 将节点数设为 1、2、3、N，则尚不支持。扩展到三节点需要明确独立 hostname/instance-id、完整 Peer 地址和客户端地址，同时修改固定服务列表、发布包映射、健康/注册表检查及故障脚本。Eureka 官方要求多个节点配置 Peer 并同步注册信息。[Spring Peer Awareness](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html#_peer_awareness)。

建议当前保留两个明确命名节点；若确实需要可配置节点数，再实现拓扑生成器，并限定节点数范围和对应验收，不只添加一个 `SIC_DISCOVERY_REPLICAS` 变量。

## 3. Eureka 浏览器访问：需要控制宿主机端口绑定

基础 Compose 固定发布 8761 和 8762，没有显式限制 host IP。JAR 生成器却为 discovery-server、discovery-peer 写入 `ports: !reset []`，因此 README 的组合配置会移除两节点全部宿主机端口绑定。容器网络内的注册、查询、Peer 同步不受影响。

当前 `.env.example` 没有 Eureka UI 开关，代码也不读取此类变量。因此设置 `EUREKA_UI_ENABLED=true` 或修改 `EUREKA_UI_PORT_A` 均不会改变当前部署。

可立即采用的手动配置方式，是在 JAR override **之后**叠加单独的端口文件：

```yaml
services:
  discovery-server:
    ports: !override
      - "${EUREKA_UI_BIND_IP:-127.0.0.1}:${EUREKA_UI_PORT_A:-8761}:8761"
  discovery-peer:
    ports: !override
      - "${EUREKA_UI_BIND_IP:-127.0.0.1}:${EUREKA_UI_PORT_B:-8762}:8761"
```

叠加后，本机浏览器访问 `http://127.0.0.1:8761/` 和 `http://127.0.0.1:8762/`。移除该端口 override 并重新应用部署后关闭宿主机入口；添加或删除 env 值本身不会重新配置已运行容器。

`127.0.0.1` 允许本机访问；`0.0.0.0` 绑定所有宿主机网络接口。`ports` 映射的是整个 Eureka HTTP 服务，包含管理界面与注册 API，不能只靠该绑定将浏览器界面和注册 API 分开。[Docker ports 文档](https://docs.docker.com/reference/compose-file/services/#ports)。

`!reset []` 会删除继承的端口，`!override` 可以完整替换端口列表；该端口文件必须排在 JAR override 后面，否则会再次被清空。`!override` 要求 Compose 2.24.4+，本机版本为 2.28.1-desktop.1。[Docker 合并文档](https://docs.docker.com/reference/compose-file/merge/)。

建议实现如下 env 接口，默认关闭：

```dotenv
EUREKA_UI_ENABLED=false
EUREKA_UI_BIND_IP=127.0.0.1
EUREKA_UI_PORT_A=8761
EUREKA_UI_PORT_B=8762
```

启动层需要明确解析布尔值，再选择或生成端口 override：false 时清空端口，true 时按 bind IP 和两个端口绑定。启动、状态、停止、预检和验收应共享同一解析结果；修改 env 后重新执行启动即可更新绑定，无需重新编译、冻结 JAR 或生成凭据。

不能把字符串 `false` 当作“变量为空”，也不能将空宿主机端口当作关闭绑定：Compose 字符串替换不提供通用 YAML 条件结构，只写容器端口可能变成随机宿主机端口。若保留原直接 Compose 命令，必须额外生成/选择这个运行配置文件；仅增加布尔 env 不会自动选择 `-f` 文件。管理界面可否显示与 Docker 是否发布端口是两个不同层面的设置。

## 配置解析验证

使用现有独立测试 env 和 JAR override，仅执行 `docker compose config --format json`，未启动测试栈：

| 验证 | 结果 |
|---|---|
| 设置五个 SIC 副本变量，直接解析当前配置 | 五业务仍默认各一个，无 scale/deploy.replicas 声明 |
| 将相同变量交给项目解析器 | 得到 2、3、2、2、2 个目标副本 |
| 为当前配置设置 EUREKA_UI_ENABLED=true | 两节点 ports 仍为空 |
| 解析基础 Compose、不叠加 JAR override | 两节点分别发布 8761、8762 |
| 在研究用 override 中增加五个 scale 字段 | Compose 得到 2、3、2、2、2 个副本 |
| 最后叠加研究用 Eureka UI 端口文件 | 两节点仅绑定 127.0.0.1:8761、127.0.0.1:8762 |

研究样例保存在忽略的 `target/deployment-research/`，未接入主启动流程。此次未重复执行容器运行与故障测试；此前固定双节点的实际故障验收见 [验收记录](eureka-ha-verification-report.md)。
