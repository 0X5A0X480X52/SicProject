# SicProjectIS_cloud

科研项目管理系统的微服务后端，配合相邻目录的 `SicProjectIS_fronted` 使用。业务主线为**项目申请 → 合同 → 验收**，包含部门审批、财务办理、专家资格申请与评审、材料管理、项目授权及实时通知。

本文以 Windows PowerShell 为例。除前端命令特别标注外，均在 `SicProjectIS_cloud` 目录执行；示例路径可按实际位置替换。

```powershell
cd C:\UserFiles\CS\Java\SicProject\SicProjectIS_cloud
```

如果这台机器已有 `sic-localdev` 环境，先看 [已有环境启动](#4-已有环境启动和日常操作)，不要重新生成凭据或初始化数据库。

需要多实例或验证负载均衡时，参见 [多实例启动、状态与验收](docs/multi-instance-startup.md)。原命令仍默认单实例；五个业务服务支持 `.env`、JSON 和命令行设置副本，新增按项目的状态、停止及 `balance/business/fault` 独立验证入口。

本轮实际运行结果见 [多实例验收记录](docs/multi-instance-verification-report.md)。

## 1. 项目结构

```text
SicProject/
├─ SicProjectIS_cloud/
│  ├─ pom.xml                         Maven 聚合工程：14 个模块
│  ├─ services/                       六个业务服务
│  │  ├─ gateway-service/
│  │  ├─ iam-service/
│  │  ├─ project-service/
│  │  ├─ workflow-service/
│  │  ├─ material-service/
│  │  └─ notification-service/
│  ├─ platform/                       公共模块、平台服务与运维工具
│  │  ├─ cloud-contracts/              DTO、事件及前端 API 契约
│  │  ├─ cloud-security/               JWT、身份上下文与公共鉴权
│  │  ├─ cloud-messaging/              Outbox、Inbox、幂等消费及指标
│  │  ├─ config-server/                集中配置及 resources/config/*.yaml
│  │  ├─ discovery-server/             Eureka 服务注册与发现
│  │  ├─ integration-tests/            跨服务与真实基础设施测试
│  │  ├─ migration-tool/               旧单体数据库、文件迁移工具
│  │  └─ dlq-recovery-tool/            死信恢复工具
│  ├─ infra/mysql/
│  │  ├─ init-databases.sh             新数据卷的五库初始化入口
│  │  ├─ schema/                      五库完整建表 SQL
│  │  ├─ seed/                        IAM 种子、默认工作流资产
│  │  └─ patches/                     已有环境的版本化 SQL 补丁
│  ├─ infra/migration/                历史数据导入及核对 SQL
│  ├─ scripts/                        启动、停止、构建及验收脚本
│  ├─ verification/
│  │  ├─ scenarios/                   完整业务场景 JSON
│  │  └─ runs/                        脱敏验收报告
│  ├─ output/pdf/blank-upload.pdf      自动化流程上传用空白 PDF
│  ├─ docs/                           架构、权限、数据库、测试与切换文档
│  ├─ docker-compose.yml              应用及基础设施编排
│  ├─ Dockerfile                      Docker 内构建并运行 Java 应用
│  ├─ .env.example                    环境变量模板，不含真实凭据
│  └─ target/                         本地配置、冻结包、日志及私有备份
└─ SicProjectIS_fronted/               Vue 3 + TypeScript + Element Plus 前端
```

### 服务职责和端口

| 模块 | 职责 | 容器内端口 |
| --- | --- | --- |
| Gateway | API 路由、JWT 与会话有效性检查、限流、SSE 转发 | 8080 |
| IAM | 注册登录、用户部门、角色权限、专家资格申请与审批 | 9001 |
| Project | 项目主数据、成员与项目授权、申请 Saga、审计 | 9002 |
| Workflow | BPMN 与任务流转、表单落库、专家指派评分、工作台与本人评审历史 | 9003 |
| Material | 文件上传下载、材料版本、引用预留与确认、单据及对象删除 | 9004 |
| Notification | 消息消费、通知持久化及 SSE 实时投递 | 9005 |
| Config Server | 提供公共配置及各服务配置 | 8888 |
| Eureka | 服务注册与发现 | 8761 |

主源码通常位于模块的 `src/main/java/com/amatrix/sic/...`，接口在 `controller`，业务编排与本地事务在 `service`，MyBatis 接口在 `dao`，SQL 在 `src/main/resources/mapper`，消息入口在 `messaging`。各服务的具体目录略有差异，逐类说明见 [类职责清单](docs/class-responsibilities.md)。

业务服务分别使用 `sic_iam`、`sic_project`、`sic_workflow`、`sic_material`、`sic_notification` 五个 Schema。当前完整结构为 52 张表，以 [结构清单](infra/mysql/schema-manifest.json) 为准。服务通过 OpenFeign 调用，通过 RabbitMQ 与 Outbox/Inbox 交换事件；Redis 用于会话、权限版本等状态，MinIO 保存材料文件。已接入 Resilience4j，调用超时和熔断等配置可在 Config Server 的配置目录查看。

普通启动共 **12 个容器**：8 个 Java 应用，加 MySQL、RabbitMQ、Redis、MinIO。`migration-tool` 和 `dlq-recovery-tool` 只在明确启用 `migration`、`operations` profile 时运行，日常启动无需启用。

## 2. 环境要求与配置

| 工具 | 用途与要求 |
| --- | --- |
| Docker Desktop | Windows 上启动 Linux 容器引擎；`docker version` 应能连接服务端 |
| Docker Compose v2 | 使用 `docker compose`；JAR 挂载方式要求支持 `!reset`、`!override` 标签 |
| JDK 21、Maven 3.9.x | 本机编译 Java；选择 Docker 内构建方式时无需主机安装这两项 |
| Python 3.11+ | 本地环境生成、就绪检查、启动器与业务测试 |
| Node.js 22.16+、npm | 前端开发及构建；本项目曾在 Node 22.16 环境验证 |
| PowerShell 5.1+ | 本文命令及 Windows 辅助脚本 |
| OpenSSL | 手动生成 JWT 密钥和使用双模式启动器时需要；自动环境生成方式不依赖它 |

检查工具，并安装 Python 依赖：

```powershell
java -version
mvn -version
python --version
node --version
npm --version
docker version
docker compose version
python -m pip install -r scripts/requirements-verification.txt
```

`requirements-verification.txt` 包含 `requests` 与 `cryptography`，涵盖本文推荐启动及业务验收脚本的依赖。首次运行还需要下载 Maven/npm 依赖及 Docker 镜像。

### 配置文件的区别

| 文件 | 用途 |
| --- | --- |
| `.env.example` | 模板；不直接作为运行环境使用 |
| `.env` | 手动配置、Docker 内构建与双模式启动器使用 |
| `target/local-dev.env` | 推荐本地 JAR 启动方式生成的运行凭据 |
| `target/local-dev.compose.yaml` | 指定冻结 JAR 的挂载路径和宿主机端口 |

两种启动方式各自使用对应配置，不要混用不同环境的密钥。Compose 项目名决定数据卷名称；日常环境本文统一使用 `sic-localdev`，后续启动、停止与检查均沿用这个名字。

主要环境变量如下：

| 配置 | 含义 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL 管理账号密码 |
| `IAM_DB_PASSWORD` 等五个 `*_DB_PASSWORD` | 各 Schema 的独立数据库账号密码 |
| `RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD` | 消息队列账号 |
| `REDIS_PASSWORD` | Redis 认证密码 |
| `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET` | 材料对象存储配置 |
| `SERVICE_TOKEN_BOOTSTRAP_SECRET` | 服务间认证引导密钥 |
| `JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY` | 同一 RSA 密钥对的单行 Base64 DER：PKCS#8 私钥、X.509 公钥 |
| `JWT_ISSUER`、`JWT_AUDIENCE` | JWT 签发者及接收方，默认 `sic-iam`、`sic-api` |
| `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD`、`BOOTSTRAP_ADMIN_REAL_NAME` | 首次启动创建管理员的配置 |

新环境可以自动生成上述凭据。已有环境应保留原配置；修改配置文件中的密码不会同步修改数据卷内的数据库账号或已有管理员密码。凭据、JWT 私钥、SQL 备份不要提交 Git。

## 3. 首次编译与启动：本机 JAR + Docker

这是当前本地环境使用的方式：主机编译 Java，Docker 运行冻结后的 JAR 和基础设施。应用运行时无需启动 IDEA 中的多个 Java 进程。

### 3.1 编译后端

```powershell
mvn -B -DskipTests package
```

此命令生成应用 `target/*-1.0.0-SNAPSHOT-exec.jar`。`-DskipTests` 用于快速打包，**不代表测试通过**；完整验证命令见后文。

冻结本次构建，避免后续编译改变运行中的挂载包：

```powershell
$releaseDir = "target/releases/local-dev-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
python scripts/snapshot_verification_release.py --release-dir $releaseDir --label local-dev
```

冻结脚本复制八个 Java 应用，记录文件摘要，并拒绝覆盖已有发布目录。

### 3.2 仅首次生成配置

确认 `target/local-dev.env` 和 `target/local-dev.compose.yaml` 尚不存在后执行：

```powershell
python scripts/prepare_verification_env.py `
    --env-file target/local-dev.env `
    --compose-override target/local-dev.compose.yaml `
    --gateway-port 8080 `
    --release-dir $releaseDir
```

脚本生成随机密码及 RSA 密钥，不输出秘密值。初始管理员用户名通常为 `admin`，密码来自生成的 `BOOTSTRAP_ADMIN_PASSWORD`，需要登录时使用本地文本编辑器查看该配置字段；不存在统一默认密码。

如果提示 `Refusing to replace existing verification configuration`，说明已有配置，直接进入 [已有环境启动](#4-已有环境启动和日常操作)。不要为消除这个提示而删除原凭据、换用新的密钥。

### 3.3 启动后端

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml config --quiet

docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600
```

首次使用新的数据卷时，MySQL 入口导入完整 DDL、角色权限种子和默认工作流；IAM 根据配置创建初始管理员。已有数据卷不会重跑初始化 SQL。本项目当前不依赖 Flyway 自动迁移，已有库升级使用版本化 SQL 补丁和发布清单。

等待 Eureka 注册、Gateway 与负载均衡缓存传播，并检查实际路由：

```powershell
python scripts/wait_verification_ready.py `
    --project-name sic-localdev `
    --env-file target/local-dev.env `
    --compose-file docker-compose.yml `
    --compose-file target/local-dev.compose.yaml `
    --gateway-base-url http://127.0.0.1:8080 `
    --report-path verification/runs/local-dev-readiness.json
```

就绪脚本默认包含 65 秒的注册稳定等待。该检查使用配置中的管理员凭据；若已修改管理员密码，应使用当前账号验证，不能把原引导密码当作最新密码。

### 3.4 启动前端

另开一个 PowerShell 终端：

```powershell
cd C:\UserFiles\CS\Java\SicProject\SicProjectIS_fronted
npm ci
npm run dev -- --port 5173 --strictPort
```

打开 `http://localhost:5173`。使用 `--strictPort` 后，5173 被占用会明确报错，不会静默换成另一个端口。开发服务器默认将 `/api` 代理到 `http://127.0.0.1:8080`，通常无需设置 `VITE_API_BASE_URL`。

单实例和多实例示例均默认对外使用 **8080**，前端无需额外设置。Gateway 对外端口可在部署 `.env` 中配置 `GATEWAY_PORT`，生成器通过 `--gateway-port` 指定初始值；新生成的 JAR override 读取该变量。历史 override 若固定写了端口，应同步修改。用 History 启动更新已有环境的端口。

若自行改为 18080，在前端目录的 `.env.development.local` 中设置 `SIC_GATEWAY_PROXY_TARGET=http://127.0.0.1:18080`，再重新启动 `npm run dev`。也可在前端 PowerShell 中设置 `$env:SIC_GATEWAY_PROXY_TARGET = 'http://127.0.0.1:18080'`。此设置控制开发服务器代理，浏览器仍使用同源 `/api`；切回默认 8080 时移除旧配置和进程变量。具体配置见 [前端 README](../SicProjectIS_fronted/README.md)。

| 地址 | 用途 |
| --- | --- |
| `http://localhost:5173` | 前端页面 |
| `http://127.0.0.1:8080` | Gateway；业务 API 在 `/api` 下 |

推荐方式只发布 Gateway 端口，Config Server、Eureka、RabbitMQ、MinIO 的宿主机端口由 override 隐藏；它们在容器网络中仍正常访问。MySQL、Redis和 IAM 等业务服务无需直接暴露给宿主机。

### 3.5 另一种方式：Docker 内编译

若不在主机安装 Java/Maven，可以使用 `start_backend.py`，由 Dockerfile 构建应用镜像。此方式使用 `.env`、项目名 `sic-cloud`，**不使用上述 JAR override**。

1. 仅在 `.env` 不存在时复制 `.env.example`，替换全部 `CHANGE_ME`。
2. 使用 `scripts/generate-jwt-keys.ps1 -OutputDirectory ./secrets` 生成同一密钥对，将两个 `.base64` 文件内容填入 `.env`。该脚本会写输出文件，只用于首次生成，已有环境保留原密钥。
3. 初始管理员密码至少 10 个字符；五个 Schema 密码避免单引号和反斜杠。
4. 第一次启动执行以下命令，并核对将要初始化的项目名：

```powershell
python scripts/start_backend.py --mode fresh --project-name sic-cloud --env-file .env
```

`fresh` 要求输入 `RESET:sic-cloud`，会删除该项目已有的容器及四个数据卷。因此只在明确创建或重建环境时使用。已有镜像环境保留数据启动用：

```powershell
python scripts/start_backend.py --mode history --project-name sic-cloud --env-file .env --skip-build
```

`history` 检查原数据卷，并提示输入当前管理员账号密码。升级镜像时省略 `--skip-build`。基础 Compose 还发布 8761、8888、9000、9006、15672；详细参数与 PowerShell 包装入口见 [双模式启动指南](docs/backend-startup-guide.md)。同一台机器不要让两套环境同时占用 8080。

## 4. 已有环境启动和日常操作

如果已使用推荐方式部署，包括当前已有的 `sic-localdev`，**不需要重新编译或重新生成配置才能启动**：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    up -d --no-build --wait --wait-timeout 600
```

前端另开终端运行 `npm run dev -- --port 5173 --strictPort`，依赖没有变化时不用重复 `npm ci`。

查看状态、最近日志：

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml ps

docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml `
    logs --tail 100 gateway-service iam-service project-service workflow-service
```

跟随某个服务日志可加 `-f`，例如 `logs -f --tail 100 workflow-service`；按 `Ctrl+C` 仅退出日志查看。

### 修改代码后更新版本

重新 `mvn -B -DskipTests package` 并冻结到**新的** release 目录；备份现有 `target/local-dev.compose.yaml`，将其中八个应用的 `/application/application.jar` 绑定源更新到新发布目录中的对应 JAR，再执行上述 `up`。只编译源码不会替换已冻结的运行包。

更新保留原 `.env` 文件、Compose 项目名和数据卷。涉及数据库结构时先备份，按补丁发布清单处理；已有库不能通过重跑完整 SQL 升级。旧版本的冻结包应保留到升级验证完成。

父模块 `target` 存放运行配置、冻结包及备份，已排除于父模块 Maven clean；子模块构建产物仍会清理。不要手工删除整个 Cloud 根目录 `target`。Docker 数据卷在 Docker 中，运行凭据和冻结包在宿主机，两者都需要保留。

## 5. 停止项目

### 一键停止本仓库全部项目

```powershell
# 先预览将停止哪些容器和进程
.\scripts\stop-all.ps1 -WhatIf

# 停止本仓库所属容器，以及可识别的本地 Node/Java 进程
.\scripts\stop-all.ps1

# 仅停止容器，保留正在运行的前端开发服务器
.\scripts\stop-all.ps1 -ContainersOnly
```

该脚本根据 Compose 归属和绝对路径识别本仓库资源，范围包括本仓库的多个测试栈，以及相邻前端、旧后端的可识别进程；它不只是停止 `sic-localdev`。容器和数据卷保留，Docker Desktop 不会关闭。命令行不包含仓库绝对路径的进程可能无法识别，需要在原启动终端按 `Ctrl+C`。

### 只停止当前环境

```powershell
docker compose -p sic-localdev --env-file target/local-dev.env `
    -f docker-compose.yml -f target/local-dev.compose.yaml stop
```

前端在运行 `npm run dev` 的终端按 `Ctrl+C`。Edge 测试窗口自行关闭，停止脚本不关闭浏览器。

`stop` 保留容器和数据；`down` 移除容器与网络但默认保留数据卷。日常停止不要使用 `down -v`、`--volumes` 或全局卷清理命令，它们会使数据库、文件或会话数据丢失。

## 6. 可选：打开多个独立 Edge 测试窗口

多角色测试需要分别登录负责人、部门管理员、科研管理员、财务人员和两位专家等账号。使用 [open-edge-test-windows.ps1](scripts/open-edge-test-windows.ps1)，默认一次打开 **7 个可见窗口**，对应你提供的 Guest + 独立 `user-data-dir` 命令：

```powershell
.\scripts\open-edge-test-windows.ps1

# 自定义数量和页面地址
.\scripts\open-edge-test-windows.ps1 -Count 3 -Url 'http://localhost:5173'

# Edge 不在 PATH 或安装路径不标准时
.\scripts\open-edge-test-windows.ps1 -Count 7 `
    -EdgePath 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'

# 仅预览，不打开窗口
.\scripts\open-edge-test-windows.ps1 -WhatIf
```

每个窗口使用 `%TEMP%\edge-test-<随机 GUID>`，因此不会复用个人 Edge 配置，也不会共享本脚本其他窗口的 Cookie、localStorage、登录状态或已有磁盘缓存。窗口打开后分别登录不同账号即可。

这里的“无缓存”指**从全新配置目录开始**；窗口运行期间仍可能产生 HTTP 或浏览器缓存，不等于完全禁用缓存。需要逐次请求禁用缓存时，可在该窗口的开发者工具 Network 中勾选 Disable cache，并保持开发者工具打开。

脚本不会关闭已有浏览器或删除临时目录。关闭测试窗口后，如需释放磁盘空间，可手工核对并清理它输出的 `edge-test-*` 目录；下次运行始终生成新目录。

若 PowerShell 提示执行策略限制，可在当前终端临时执行 `Set-ExecutionPolicy -Scope Process Bypass` 后重试，无需修改整台机器的执行策略。

## 7. 编译、自动化测试与业务使用

### 后端测试

```powershell
# 普通服务测试，不运行 platform/integration-tests
mvn -B -pl '!platform/integration-tests' test
python scripts/assert_verification_gate.py --gate smoke

# 完整验证：先确保 Docker Linux 引擎可用
mvn -B verify
python scripts/assert_verification_gate.py --gate mysql
python scripts/build_full_schema.py --check
```

MySQL、Redis、消息等集成测试使用真实容器；缺少 Docker 时不能把跳过测试当作验收通过。门禁也检查生成的接口契约测试是否实际执行。检查历史结果时，可以用 `--started-at` 限定本次测试的起始时间。

### 前端测试与构建

在 `SicProjectIS_fronted` 执行：

```powershell
npm test
npm run build
```

构建输出在 `dist`。生产部署静态页面时需要 Web 服务器把同源 `/api` 转发至 Gateway，并支持 SSE 长连接及文件上传下载；Vite 开发代理不会自动进入生产构建。

### 完整业务回归

建议使用独立 Compose 项目、独立配置及数据卷执行，它会注册测试账号、分配角色并创建实际业务数据。场景通过 `runId` 隔离，包含 37 个用户节点、3 个双专家评审批次和 17 类必填材料，使用仓库内的空白 PDF 上传：

```powershell
python scripts/verify_business_lifecycle.py `
    --gateway-base-url http://127.0.0.1:8080 `
    --env-file target/local-dev.env `
    --negative-cases --ui-regression-cases --iteration-regression-cases `
    --report-path verification/runs/manual-business.json

python scripts/verify_business_db.py `
    --project-name sic-localdev --env-file target/local-dev.env `
    --compose-file docker-compose.yml --compose-file target/local-dev.compose.yaml `
    --business-report verification/runs/manual-business.json `
    --report-path verification/runs/manual-business-database.json
```

上面参数示范使用日常环境；用于独立测试时需同步替换项目名、配置文件、override 和 Gateway 端口，不能只换一个参数。报告脱敏，失败现场保留。完整步骤及其他分支见 [验证指南](docs/verification-guide.md) 与 [完整业务用例](docs/full-business-e2e-test-case.md)。

手工跑流程时注意：

- 新注册账号先分配启用部门，再发起项目申请。
- 角色或部门变更后重新登录，旧 token 会失效。
- 财务办理需要 `FINANCE_ADMIN` 和有效的项目财务授权，授权范围必须匹配模块、轮次或节点。
- 两位专家通过真实资格申请和审批取得专家角色，每个评审批次分别提交；第一人提交后仍等待第二人。
- 专家在“我的评审”查看待评任务及本人记录；评审结束后项目和材料临时权限收回，个人评审历史保留。
- 流程工作台的“已结束”指模块结束状态，项目生命周期另列展示。历史无部门项目由管理员核对后补充部门。

## 8. 常见问题和进一步阅读

| 现象 | 处理 |
| --- | --- |
| Docker 无法连接或不是 Linux 引擎 | 启动 Docker Desktop，确认 `docker version` 的服务端可用 |
| `FileExistsError: ...local-dev.env` | 使用原配置直接 `up`；不要重新运行生成脚本覆盖凭据 |
| Compose 不认识 `!override`、`!reset` | 使用支持这些标签的 Compose v2，先执行 `config --quiet` 检查 |
| 8080 / 5173 被占用 | 检查是否项目已启动；不要误开第二套同端口环境。调整 Gateway 时同时调整前端代理和验收 URL |
| 启动后暂时 503 | 先检查容器健康和日志，再执行就绪检查等待服务发现传播 |
| 修改源码后运行行为没变 | 当前可能挂载冻结包，需要编译、冻结新版本并更新 override |
| 管理员登录失败 | 核对正在运行的项目与凭据文件；已有账号改密码后原引导密码不再有效 |
| 部门或角色变更后旧页面无权限 | 重新登录并刷新；财务另核对项目授权范围 |
| 前端可打开，API 却连到旧服务 | 核对 `vite.config.mjs` 代理及自行设置的 `VITE_API_BASE_URL`，修改后重启 Vite |
| 停止后数据库或材料不见了 | 核对 Compose 项目名、数据卷是否沿用；`stop` 本身不删除数据 |

更深入的说明：

- [前后端核心代码注释规范](docs/code-commenting-guide.md)、[注释覆盖清单](docs/code-commenting-coverage.md)
- [Controller API 索引：接口用途及源码入口](docs/controller-api-index.md)
- [后端请求与 OpenFeign 契约](docs/backend-request-contracts.md)
- [项目结构概览](docs/project-structure-overview.md)、[逐类职责](docs/class-responsibilities.md)
- [服务分层方案](docs/service-layering-refactor-plan.md)、[技术栈审计](docs/spring-cloud-stack-audit.md)
- [鉴权与性能方案](docs/authentication-authorization-performance-plan.md)
- [本轮部门、财务、流程历史与专家工作台迭代](docs/workbench-permission-iteration.md)
- [数据库迁移清单](docs/database-migration-inventory.md)、[SQL 补丁说明](infra/mysql/patches/README.md)
- [旧单体切换手册](docs/cutover-runbook.md)、[双模式启动指南](docs/backend-startup-guide.md)
- [完整业务用例](docs/full-business-e2e-test-case.md)、[验证指南](docs/verification-guide.md)

调研文档、历史报告和 CI 配置分别记录设计与已有证据，不代表当前任意环境已通过所有性能或故障验收；以对应版本、环境和本次实际执行结果为准。
