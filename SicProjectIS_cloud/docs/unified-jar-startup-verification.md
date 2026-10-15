# 统一 JAR 启动流程验收记录

日期：2026-10-14（Asia/Shanghai）。使用独立项目 `sic-jar-unified`、专用 env 和四个数据卷，从已有八个已打包应用创建新的冻结发布 `target/releases/jar-unified-20261014`。本轮修改 Python、Compose 和文档，没有修改或重新编译 Java 源码。

## 配置与执行路径

五业务服务副本数分别为 IAM 2、Project 3、Workflow 2、Material 2、Notification 2，Gateway 1、Config 1、Eureka 两个独立节点。共 19 个容器，其中 15 个 Java 进程，复用八个不同的冻结 JAR。

本次通过生成器创建凭据，编辑专用 env 的部署设置，再通过 `--update-override` 更新同一个 override。首次启动直接使用 `docker compose config --quiet`、`up -d --no-build --wait --wait-timeout 600`，未使用 Python Fresh/History 启动器。IAM 两副本在新数据库上同时初始化后均健康，管理员登录通过。

## 结果

| 检查 | 结果 | 证据 |
|---|---|---|
| Python 启动/HA/配置更新回归 | 33 项通过 | `python -m unittest discover -s scripts/tests -p 'test_*.py'` |
| 冻结包与直接 Compose 首次多副本启动 | 通过 | 19 个容器均 healthy，逐实例地址及端口映射通过 |
| 开启 UI 的严格就绪检查 | 6 项通过 | [开启报告](../verification/runs/jar-unified-ui-enabled.json) |
| 关闭 UI 的严格就绪检查 | 6 项通过 | [关闭报告](../verification/runs/jar-unified-ui-disabled.json) |
| 两个 Eureka 页面实际 HTTP 访问 | 开启均 200，关闭均无法连接 | [生命周期报告](../verification/runs/jar-unified-lifecycle.json) |
| Project 期望 4、实际 3 时拒绝验收 | 通过，退出码 1 | [副本缺失报告](../verification/runs/jar-unified-missing-replica.json) |
| UI env 已切换、override 未更新时拒绝验收 | 通过，退出码 1 | [UI 不一致报告](../verification/runs/jar-unified-stale-ui.json) |
| 更新 override 保持 env 原字节和凭据 | 通过 | 生命周期报告及更新回归测试 |
| 无 release-dir 更新保留冻结 JAR | 通过 | 运行阶段复用原发布；单元测试包含旧生成器格式迁移与摘要异常保护 |
| CI YAML 解析和回归步骤接入 | 通过 | business job 已加入 Python 单元测试；远程 Actions 尚未执行 |

两个正向报告均包含全部 19 个容器、精确目标数量、两节点注册表、JAR 摘要、实际宿主机绑定和四个公开读路由。每次正常验收均使用默认 65 秒稳定观察。

连接池预算为 `11 × 10 + 20 = 130`，小于本次 MySQL `max_connections=151`。这只是连接预算检查，不是数据库吞吐量承诺。

## Eureka UI 开关

本次使用 Gateway 宿主机 18082，Eureka 本机 18761、18762，以独立端口验证；正式本地流程默认 8080、8761、8762。

开启时 `EUREKA_UI_ENABLED=true`、`EUREKA_UI_BIND_IP=127.0.0.1`：两个运行容器实际绑定与 env 一致，两个根页面返回 HTTP 200 且含 Eureka 页面内容。随后设为 false，用 `--update-override` 更新同一个文件，再执行原 Compose up：两个实际绑定列表均为空，原宿主机地址均无法连接，但所有业务副本、双节点注册表和业务路由仍通过。

两个负向报告的 `passed=false` 是预期结果，用于证明就绪脚本会拒绝缺失实例和未应用的 UI 配置，并非整体验收失败。修改期望副本数后未运行 Compose，脚本没有自动创建、停止或删除业务容器。

更新过程生成了 override 备份；env 中密码、RSA 密钥及其他凭据值全部保持不变。所有临时部署操作仅限该测试项目。验收完成后通过项目级停止入口停止测试栈，保留容器、数据卷、冻结包及专用配置；其他部署未升级。

未在本轮重复此前 Eureka 故障演练，历史证据见 [HA 验收记录](eureka-ha-verification-report.md)。本轮覆盖本机 UI 绑定及多副本统一入口，未验证公网入口或跨宿主机部署。操作步骤见 [统一启动指南](unified-jar-startup.md)。
