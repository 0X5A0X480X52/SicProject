# 多实例实施与验收记录

验收日期：2026-10-12。使用隔离 Compose 项目 `sic-replica-accept`，独立四个数据卷，Gateway `127.0.0.1:18080`。Config/Eureka 不发布宿主机端口。Docker 27.0.3 Linux 引擎，Compose 2.28.1，16 CPU、约 7.75 GiB Docker 内存；本机 Python 3.12.12、Maven 3.9.8、JDK 21.0.8。Java 应用使用 384 MiB 最大堆。这是功能与单副本恢复验收，不是容量基准测试。

最终汇总：[multi-instance-acceptance.json](../verification/runs/multi-instance-acceptance.json)，全部验收通过，17 个容器已停止、四个数据卷保留，报告脱敏扫描通过。

## 交付范围

1. 共享副本配置、扩展启动器及 PowerShell 包装；新增按项目状态、停止和 dry-run。五个业务服务可扩容，其余服务固定一个实例，保留 Fresh/History、凭据和卷归属保护。
2. Saga 按 Project→Saga 加锁并复核完成状态，失败/重试条件更新；管理员初始化角色行锁；MinIO 创建冲突复查；默认关闭的脱敏请求证据日志。
3. `verify_load_balancing.py` 的分流、完整业务、单副本退出恢复三个独立模式；配置样例、[操作指南](multi-instance-startup.md)和实际验收报告。

未变更业务 API、响应字段或数据库结构，没有新增集中数据访问服务。并发材料上传的真实 MySQL 测试通过，本轮未额外重写上传、评审或消息业务实现。

## 自动化测试

| 检查 | 实际结果 |
| --- | --- |
| 普通后端单测 | 188 条通过，0 失败、0 错误、0 跳过 |
| 真实 MySQL 集成 | 22 条通过，0 失败、0 错误、0 跳过 |
| Python 脚本测试 | 20 条通过 |
| Maven 打包 | 通过，本机冻结八个可执行 JAR 并生成 SHA-256 清单 |
| Dockerfile 构建 | 八个应用镜像构建成功，运行清单保存实际镜像 ID |
| PowerShell / Python | PowerShell AST、包装 dry-run、Python 编译检查通过 |
| diff / 脱敏 | diff 格式检查、报告中的环境密钥/密码/JWT 扫描通过 |

MySQL 测试包含两个恢复工作者竞争确认只激活一次/发布一次、确认失败事务回滚后恢复、迟到失败不能回退 COMPLETED、两个初始化器只创建一个管理员且不重设密码、并发上传版本唯一；同时复用专家并发评分、同序号迁移竞争、材料租约隔离、Outbox/Inbox 事务及发布租约等既有测试。

## 运行验收

| 部署方式 | 启动 / smoke | Gateway / Feign 分流 | 完整业务 / SQL | 五服务单副本退出恢复 |
| --- | --- | --- | --- | --- |
| 冻结 JAR 全双副本 | 通过，17 个容器、8 项 smoke | [通过](../verification/runs/lb-20261012101723-96d2c9/report.json) | [通过](../verification/runs/lb-20261012101852-bd75be/report.json) | [通过](../verification/runs/lb-20261012102051-218889/report.json) |
| 源码镜像全双副本 | 通过，17 个容器、8 项 smoke | [通过](../verification/runs/lb-20261012105817-ec5745/report.json) | [通过](../verification/runs/lb-20261012105818-a84b86/report.json) | [通过](../verification/runs/lb-20261012110241-d5984c/report.json) |

镜像完整业务额外核对两个 Notification 副本收到内容完全相同的指定项目流转事件；17 个材料版本都通过其他 Material 副本下载并核对 PDF SHA-256；五个业务服务均有成功请求证据，三个业务 Feign 方向均触达目标副本。

两种部署方式均记录了注册/缓存摘除期间的失败请求；“故障恢复通过”表示摘除后剩余实例持续成功、原容器恢复后重新参与分流，并不表示故障窗口零失败。完整时间线、失败数量与 traceId 以各 fault 报告为准。

配置和生命周期矩阵见 [实际报告](../verification/runs/multi-instance-matrix.json)：默认单实例、Project/Workflow 双副本、五服务全双副本、缩回单实例、按项目停止、停止后重启、镜像切换均通过。逐阶段比较四个卷的创建标识以及用户、项目、模块、材料版本数量，重新执行此前完整业务的数据库断言。

连接预算实际为 100 + 20，MySQL 上限为 151。负向测试请求 Project 12 个副本、其他业务各 2 个，需要 200 + 20 个连接，启动器在扩容前拒绝；17 个既有运行容器及数据保留。该负向测试退出码 1 是预期拒绝，报告明确记录，未把失败的启动当作正常启动通过。

首次停止后立即重启曾触发 Windows 端口释放延迟，失败启动清单和矩阵 `attempts` 已保留。修复为按 Gateway→业务→基础设施顺序停止，并仅对本项目已停止的端口做最多 20 秒等待；再次连续停止/重启与原业务断言通过。无数据清理或重新初始化。

PowerShell 收尾检查还发现本机多个 Python 安装使 `Get-Command` 返回多个路径，以及薄包装可能掩盖失败退出码。已明确选择第一个匹配的解释器，并使状态/停止包装异常返回 1；真实状态查询和 [PowerShell 分流包装](../verification/runs/lb-20261012112316-1e4b43/report.json)通过，缺失环境文件的负向检查实际返回 1。最终停止另行通过 Docker 查询确认，未以包装退出码单独作为停止证据。

## 报告与复现

详细命令见 [操作指南](multi-instance-startup.md)。验收凭据仅在未提交的 `target/multi-replica.env`；冻结 JAR override 为 `target/multi-replica.compose.yaml`，源码镜像 override 为 `target/multi-image.compose.yaml`。启动、停止和验证须提供相同项目与配置。已有管理员凭据未重置。

故障恢复结束后，[已有完整业务 SQL 再验收](../verification/runs/multi-final-existing-business.json)通过。验收项目的全部 17 个容器已按新 PowerShell 入口停止，四个数据卷和测试数据保留。需要重启时：

```powershell
python scripts/start_backend.py --mode history --project-name sic-replica-accept --env-file target/multi-replica.env --compose-file target/multi-image.compose.yaml --replica-config replicas.multi.example.json --skip-build
```

Python 入口提示输入当前管理员凭据，或使用 PowerShell `-AdminCredential`。测试凭据在上述本地 env 文件中，不放入命令行或报告。

报告保存 runId、配置来源、容器/注册映射、请求落点、业务/SQL 断言及故障窗口；测试账号、项目、文件和数据卷保留。后续增加副本数量、混合负载或数据库/Redis/RabbitMQ/MinIO 的依赖故障矩阵应独立验收；本报告不承诺绝对无死锁或任意容量。
