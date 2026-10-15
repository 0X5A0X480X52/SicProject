# Eureka 双容器高可用验收记录

验收日期：2026-10-14（Asia/Shanghai）。本次使用独立 Compose 项目 `sic-eureka-ha-accept`，业务各单副本，共 13 个容器。使用本地已打包的业务 JAR 与本次重新打包的 discovery/config JAR，两个 Eureka 容器挂载同一 discovery-server JAR；具体摘要、部署路径与实例信息保存在 JSON 报告中。

## 结果

| 检查 | 结果 | 证据 |
|---|---|---|
| discovery/config 模块 Maven 打包 | 通过 | `mvn -B -ntp -pl platform/discovery-server,platform/config-server -am -DskipTests package` |
| 启动与 HA Python 单元测试 | 26 项通过 | `python -m unittest discover -s scripts/tests -p 'test_*.py'` |
| Compose 合并配置与 History 启动预检 | 通过 | `config --quiet`、启动器 `--dry-run` |
| 正常端到端检查 | 8 项通过 | [E2E 报告](../verification/runs/eureka-ha-compose-e2e.json) |
| 双注册表稳定与业务读路由 | 通过 | [Readiness 报告](../verification/runs/eureka-ha-readiness.json) |
| A→B、B→A 单地址客户端注册同步 | 通过 | [HA 故障报告](../verification/runs/eureka-ha-accept.json) |
| A、B 分别停机后的新注册、首次发现与真实实例调用 | 通过 | 同上，`faults[].discoveredInstanceCall.health=UP` |
| 故障期间状态识别 | 通过 | `status_backend.py` 返回 `discoveryStatus=degraded`、退出码 1 |
| 两节点恢复、注册表一致 | 通过 | [最终状态](../verification/runs/eureka-ha-final-status.json) |
| GitHub Actions YAML 解析 | 通过 | 已加入 HA 验收步骤；远程 Actions 尚未执行 |

## 故障测量

故障使用 `docker stop --time 0`：进程被立即终止，节点保持停止；每个方向至少观察 65 秒。全新客户端的双地址列表将故障节点放在第一项。冷客户端用真实 Eureka DiscoveryClient 注册与首次拉取，在其容器内按新发现的 Project IP/端口调用 readiness。已有业务请求经过 Gateway，采样 IAM、Project、Workflow 三条读路由。

| 停止节点 | 新客户端就绪并调用发现实例 | 故障开始至恢复收敛 | 故障期间读请求 | 成功率 |
|---|---:|---:|---:|---:|
| discovery-server | 26.140 秒 | 82.640 秒 | 64 次 | 100% |
| discovery-peer | 26.375 秒 | 82.937 秒 | 64 次 | 100% |

新客户端时间包含 Java 进程启动和注册表可见性等待；收敛时间从故障开始计量，包含 65 秒停机观察，不能直接当作恢复节点启动耗时。上述数字是本机本次测量，不是生产 RTO 或业务 SLA。A→B 同步场景总时间 42.594 秒，B→A 26.265 秒，均包含临时客户端启动。

HA 脚本完成后两节点及全部业务容器均健康，两份业务注册表一致；`cleanupErrors` 为空，临时客户端已删除。本次测试栈随后通过项目级停止入口停止，保留容器、四个数据卷及专用配置；未升级其他部署。

本次未验证跨宿主机故障、网络分区、长时间全注册中心失联或生产负载下的切换；同宿主机双容器仍共享 Docker 引擎故障域。部署和已有 JAR 环境升级步骤见 [Eureka HA 指南](eureka-ha.md)。
