# 后端双模式一键启动

本页保留 `start_backend.py` 的兼容入口，适用于 Docker 内构建或显式 Fresh/History 操作。本机冻结 JAR 的推荐入口为 [统一 JAR + Docker 流程](unified-jar-startup.md)，直接 Compose 支持 env 指定业务副本，就绪脚本负责严格验收。

主入口为 [`../scripts/start_backend.py`](../scripts/start_backend.py)，[`../scripts/start-backend.ps1`](../scripts/start-backend.ps1) 是保留原参数与调用方式的兼容包装器。启动器负责 Gateway、IAM、Project、Workflow、Material、Notification、双节点 Eureka、Config Server，以及 MySQL、RabbitMQ、Redis、MinIO，共 13 个服务。成功后后端入口为 `http://127.0.0.1:8080`，现有 `SicProjectIS_fronted` 通过 `/api` 访问，无需修改前端源码或 API 类型。已有单节点 JAR override 的升级方法见 [Eureka HA](eureka-ha.md)。

| 模式        | 适用情况                                    | 数据行为                                                                                     |
| ----------- | ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `Fresh`   | 首次初始化，或明确要清空当前 Cloud 环境重建 | 确认后删除指定 Compose 项目的容器及四个数据卷，用完整 SQL 初始化五库及种子、创建管理员 |
| `History` | 继续使用已经运行过的 Cloud 后端             | 要求四个原数据卷全部存在，保留数据启动；已有库的结构升级需按发布清单单独执行版本化 SQL 补丁 |

`History` 中的“历史”指 Cloud 数据卷。旧单体 MySQL 与本地 uploads 的一次性迁移应使用 [单体到五服务切换手册](cutover-runbook.md)。

## 1. 首次准备

需要 Python 3.11 以上、Docker Desktop 的 Linux 容器模式、Docker Compose v2 和可通过 `PATH` 调用的 OpenSSL。Python 依赖只包含 `requests`，脚本不会自动修改 Python 环境：

```powershell
python -m pip install -r .\scripts\requirements-startup.txt
```

兼容入口还需要 Windows PowerShell 5.1 或 PowerShell 7；它优先使用 `py -3.12`，不可用时回退到 `python`。正常启动由 Docker 构建 Java 应用，主机无需安装 Java 或 Maven。首次构建需要网络下载镜像和 Maven 依赖。

进入 Cloud 工程目录，检查工具：

```powershell
cd C:\UserFiles\CS\Java\SicProject\SicProjectIS_cloud
$PSVersionTable.PSVersion
python --version
docker version
docker compose version
openssl version
```

部署路径可不同；后续示例均以 `SicProjectIS_cloud` 为当前目录。启动脚本自身按所在位置定位工程，从其他目录调用也可工作。

手工准备 `.env`。已有文件应继续使用，先确认其所属环境；不要用模板覆盖历史配置：

```powershell
if (-not (Test-Path -LiteralPath .\.env)) {
    Copy-Item -LiteralPath .\.env.example -Destination .\.env
}
```

首次初始化时生成 RSA 密钥。已有环境应保留原密钥，不要再次在原输出目录生成：

```powershell
.\scripts\generate-jwt-keys.ps1 -OutputDirectory .\secrets
```

用文本编辑器打开 `.env` 并完成配置：

- 替换所有 `CHANGE_ME`，配置 MySQL root、五个 Schema 账号、RabbitMQ、Redis、MinIO 和服务 Token 的密钥。
- 将 `secrets/jwt-private.base64` 的单行内容填入 `JWT_PRIVATE_KEY`，将 `secrets/jwt-public.base64` 的单行内容填入 `JWT_PUBLIC_KEY`。它们分别是 PKCS#8 私钥和 X.509 公钥，必须为同一 RSA 密钥对。
- `Fresh` 使用 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD`、`BOOTSTRAP_ADMIN_REAL_NAME` 创建初始管理员；用户名不能为空，密码至少 10 个字符。
- `JWT_ISSUER`、`JWT_AUDIENCE` 默认分别为 `sic-iam`、`sic-api`；可按环境统一配置，自动验收使用实际生效值。`DLQ_REPLAY_CONFIRM=` 是正常启动时有意留空的可选配置，`LEGACY_UPLOADS_PATH` 仅供迁移使用。
- MinIO 的 `MINIO_ACCESS_KEY` 至少 3 个字符，`MINIO_SECRET_KEY` 至少 8 个字符。当前 MySQL 初始化 SQL 限制五个 `*_DB_PASSWORD` 不得包含单引号或反斜杠。

配置采用 UTF-8、每行一个 `KEY=value`，键名为大写字母、数字和下划线，不能重复，也不允许通过 `COMPOSE_*`、`DOCKER_*` 字段改变启动控制参数。值按单行字面量处理，不展开其他变量或执行表达式。值含 `$` 时用单引号包裹；单引号内部不能再含单引号。双引号形式不支持反斜杠转义或 `$` 插值。建议注释独占一行，避免与密码中的 `#` 混淆。脚本会优先使用指定文件中的配置，避免调用终端中残留的同名环境变量改变目标环境。

脚本不会生成、覆盖或回写 `.env`。`.env` 和 `secrets` 均包含敏感信息，应单独安全保存，不要提交 Git，也不要将完整配置或令牌粘贴到日志和问题报告中。历史环境需要原 `.env`、原 Compose 项目名及原数据卷共同保留。

宿主机需要以下端口：

| 端口      | 用途                   |
| --------- | ---------------------- |
| `8080`  | Gateway，前端 API 入口 |
| `8761`  | Eureka                 |
| `8762`  | Eureka Peer            |
| `8888`  | Config Server          |
| `9000`  | MinIO 对象 API         |
| `9006`  | MinIO 管理页面         |
| `15672` | RabbitMQ 管理页面      |

若旧单体或其他 Compose 项目占用这些端口，先自行确认并停止对应服务。脚本报告冲突并退出，不会自动停止其他项目；例如已有 `sic-codex-e2e` 测试栈时，应复用该项目启动，或先明确停止它再启动另一项目。

## 2. 模式一：初始化并从零开始

```powershell
python .\scripts\start_backend.py --mode fresh
```

或使用兼容入口：

```powershell
.\scripts\start-backend.ps1 -Mode Fresh
```

默认项目名为 `sic-cloud`。脚本校验工具、配置、密钥、端口与目标资源，默认先构建镜像，再执行清理和启动。已有资源会列出，删除前必须输入完整口令：

```text
RESET:sic-cloud
```

确认意味着删除该项目的数据库、RabbitMQ 消息、Redis 会话和 MinIO 文件。对应四个卷为 `sic-cloud_mysql-data`、`sic-cloud_rabbitmq-data`、`sic-cloud_redis-data`、`sic-cloud_minio-data`。清理仅针对指定项目，不删除镜像；脚本不自动备份，数据删除后只能通过事先保存的备份恢复。

自动化环境可显式提供口令；这与手工输入具有相同的删除效果：

```powershell
python .\scripts\start_backend.py --mode fresh `
    --project-name sic-cloud `
    --reset-confirmation 'RESET:sic-cloud'

.\scripts\start-backend.ps1 -Mode Fresh `
    -ProjectName sic-cloud `
    -ResetConfirmation 'RESET:sic-cloud'
```

初始化由 MySQL Docker 入口执行 `infra/mysql/init-databases.sh`，导入五库完整 DDL、IAM 种子及默认流程定义；IAM 管理员初始化器创建初始管理员。它不导入旧项目、历史材料或演示业务数据。启动完成后，脚本使用 `.env` 中的初始管理员完成自动验收；前端首次登录也使用该账号。

## 3. 模式二：基于历史启动

```powershell
python .\scripts\start_backend.py --mode history
```

直接调用 Python 时使用 `getpass` 安全读取当前管理员密码。PowerShell 兼容入口可使用 `PSCredential`；密码只通过子进程的临时环境传递，Python 读取后立即删除，不进入命令行：

```powershell
.\scripts\start-backend.ps1 -Mode History
```

脚本提示输入当前有效的管理员凭据。也可以使用 `PSCredential`，避免把密码写入命令行历史：

```powershell
$adminCredential = Get-Credential -UserName admin
.\scripts\start-backend.ps1 -Mode History -AdminCredential $adminCredential
```

若之前使用 `sic-codex-e2e` 项目名，必须沿用该名字：

```powershell
.\scripts\start-backend.ps1 -Mode History `
    -ProjectName sic-codex-e2e `
    -AdminCredential (Get-Credential -UserName admin)
```

历史模式要求指定项目的四个卷全部存在，并通过 Compose 归属标签检查；卷缺失或只剩一部分时会停止，避免意外创建混合的新旧环境。发现额外项目卷、未知项目容器或被其他容器共用的数据卷时，也会停止供人工处理。现存容器的关键配置会与 `.env` 比对，发现不一致时应恢复原配置或按独立的凭据轮换流程处理。容器已移除时无法进行该比对，仍须使用原配置；仅修改 `.env` 中的数据库密码不会更新卷内既有数据库账号。

历史启动临时禁用管理员初始化，不会按 `.env` 的初始管理员字段另建账号，也不会重置已有管理员密码。自动验收使用当前输入的管理员凭据；密码已变更时，原初始化密码不再适用。

历史模式保留数据卷，不重跑完整初始化 SQL，也不自动应用版本化 SQL 补丁。升级前请按发布清单处理必要补丁，备份数据库与 MinIO 文件，保留原配置和可回滚镜像。脚本失败时保留容器、数据卷与诊断信息，不自动回滚数据库结构。

## 4. 参数与启动结果

Python 与 PowerShell 参数一一对应：

| Python / PowerShell                                | 默认值 / 行为                                                  |
| -------------------------------------------------- | -------------------------------------------------------------- |
| `--mode` / `-Mode`                             | `fresh`/`Fresh` 或 `history`/`History`；省略时交互选择 |
| `--project-name` / `-ProjectName`              | `sic-cloud`；每次维护同一环境必须一致                        |
| `--env-file` / `-EnvFile`                      | Cloud 工程根目录的`.env`                                     |
| `--admin-username` / `-AdminCredential`        | History 自动验收所用当前管理员；密码安全提示输入               |
| `--reset-confirmation` / `-ResetConfirmation`  | Fresh 删除确认口令，必须精确为`RESET:<ProjectName>`          |
| `--skip-build` / `-SkipBuild`                  | 使用已有镜像；清理数据前仍会检查镜像是否齐全                   |
| `--startup-timeout` / `-StartupTimeoutSeconds` | `600` 秒；用于启动就绪和验收等待，不限制镜像构建耗时         |

首次构建可能较慢。正常重启可执行：

```powershell
.\scripts\start-backend.ps1 -Mode History -SkipBuild
```

Python 启动器直接运行八项自动检查：Gateway 就绪、13 个容器健康、Config/双节点 Eureka、未登录响应包装、管理员登录与 JWT、跨服务路由、SSE 查询令牌转发、注销后的令牌失效。成功后输出后端入口及前端启动提示，JSON 报告保存在 `verification/compose-e2e.json`。也可用 `verify_compose_e2e.py` 单独验收；`verify-compose-e2e.ps1` 是其兼容包装器。

每次启动同时写入 `logs/startup-YYYYMMDD-HHMMSS.log` 和 `logs/latest.log`，自动保留最近 20 份时间戳日志。控制台与日志都会显示每个外部命令的脱敏后完整命令行、工作目录、耗时和退出码；镜像构建固定使用 plain progress。名称含 `PASSWORD`、`SECRET`、`KEY`、`TOKEN` 的配置值，以及 Authorization、JWT、SSE token 和登录密码会被脱敏。

失败时启动器保留容器和数据卷，记录失败阶段、是否执行过 Fresh 清理、原命令最后 100 行，并自动采集 `compose ps -a`、异常容器 inspect 摘要和最近 100 行日志。自动诊断自身失败不会覆盖原始异常。向他人提供日志前仍应人工检查业务数据和个人信息。

服务注册后，Gateway 和服务间调用的发现缓存可能尚未更新。验收在超时范围内等待只读 GET 路由的临时 503 消失，不自动重试登录或业务写请求。

验收会登录并注销一个测试会话，不会注销其他会话。自动检查覆盖基础联通和认证，不代替真实材料上传下载、多角色权限和完整业务流程的人工验收。失败以非零退出码结束；即使部分服务已可访问，也应处理失败项后重新启动或单独运行验收。

## 5. 连接现有前端

启动脚本只启动并验证后端。另开一个终端启动现有前端：

```powershell
cd ..\SicProjectIS_fronted
npm install
npm run dev
```

打开 Vite 输出的本地页面地址，使用当前环境的管理员或业务账号登录。前端默认请求相对路径 `/api`，已有 `vite.config.mjs` 将它代理到 `http://127.0.0.1:8080`；SSE 的 `/api/sse/subscribe`、材料上传与下载也经过相同入口。

本地开发无需设置 `VITE_API_BASE_URL`。如果此前自行设置过该变量，请确认它未指向旧后端。Vite 的开发代理仅在 `npm run dev` 中生效；部署构建后的静态文件时，需要由实际 Web 服务器将同源 `/api` 反向代理至 Gateway，并支持 SSE 长连接和文件流。

## 6. 查看状态、停止与排障

以下命令在 Cloud 工程目录执行；使用自定义项目名或 `.env` 路径时请同步替换：

```powershell
docker compose -p sic-cloud --env-file .\.env -f .\docker-compose.yml ps
docker compose -p sic-cloud --env-file .\.env -f .\docker-compose.yml logs --tail 100 gateway-service iam-service
docker compose -p sic-cloud --env-file .\.env -f .\docker-compose.yml stop
```

`stop` 保留容器和数据卷。若需要移除容器及网络而保留数据，使用不带卷删除选项的命令：

```powershell
docker compose -p sic-cloud --env-file .\.env -f .\docker-compose.yml down
```

之后可用 `History` 恢复。日常停止不要添加 `--volumes` 或 `-v`，也不要使用 Docker 全局卷清理命令。

| 现象                                                  | 处理                                                                            |
| ----------------------------------------------------- | ------------------------------------------------------------------------------- |
| Docker 不可用或不是 Linux 容器                        | 启动 Docker Desktop，切到 Linux 容器，确认`docker version` 可连接服务端       |
| `.env` 缺失、重复键、必填项为空或仍有 `CHANGE_ME` | 手工修正对应字段后重试；可选的死信确认字段允许为空                              |
| `.env` 字面量或密码格式不符合要求                   | 按第 1 节的单行规则调整引号；五个 Schema 账号密码避免单引号和反斜杠             |
| RSA 验证失败                                          | 确认复制的是两个`.base64` 文件的完整单行内容，且属于同一密钥对                |
| 端口被其他项目占用                                    | 根据报告定位所属项目，自行安排停机；不要用`Fresh` 试图解决端口冲突            |
| `History` 数据卷缺失                                | 检查 Docker 上下文、原项目名及备份；不要直接改用`Fresh`，除非确实接受从零重建 |
| 关键配置与现存容器不一致                              | 恢复原`.env`，或单独实施服务和存储的凭据轮换；脚本不会修改历史账号密码        |
| 镜像构建失败                                          | 检查镜像仓库网络、Docker 磁盘和 Maven 下载日志；默认构建在清理数据之前进行      |
| 服务启动超时                                          | 查看失败服务日志和 Docker 资源；确属启动较慢时可增加`-StartupTimeoutSeconds`  |
| 管理员登录验收失败                                    | 历史模式使用当前数据库中的管理员密码，确认账号有效且具备`SYSTEM_ADMIN` 角色   |
| 前端请求失败                                          | 确认 Gateway 已在`8080` 就绪、使用 `npm run dev`、没有残留 API 地址覆盖     |

分享日志前检查是否含凭据、JWT 或个人信息。排障后保持相同 `ProjectName` 和 `.env` 重试；需要单体导入、死信恢复或停机回滚时，按 [切换手册](cutover-runbook.md) 的对应步骤操作。

脚本自身的配置解析、卷归属、端口冲突、重置口令和只读重试检查可以独立运行，无需 Docker，也不会读取真实 `.env` 或操作数据卷：

```powershell
python -m unittest discover -s .\scripts\tests -p "test_*.py" -v
```
