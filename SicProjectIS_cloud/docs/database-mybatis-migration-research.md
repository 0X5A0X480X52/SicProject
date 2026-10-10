# MySQL 完整建库 SQL 与 MyBatis Mapper 迁移调研

调研日期：2026-10-10。范围是“从 Flyway 改为完整 SQL 初始化、业务数据层改为 MyBatis Mapper、评估额外的数据访问服务”。本文给出可实施方案；尚未切换应用依赖、执行数据库变更或迁移生产数据。完整源码枚举见 [数据库迁移范围清单](database-migration-inventory.md)。

## 1. 建议结论

可以按需求采用 **MySQL 8.4 + 完整 Schema SQL + MyBatis-Spring Mapper**。保留五个服务各自的 Schema 和数据库账号，事务继续由本服务的 Spring Service 管理；MyBatis Mapper 放在服务内部 `dao` 包，SQL 放在 `resources/mapper`。

Flyway 与 MyBatis 解决不同的问题：前者管理结构演进，后者执行数据读写，两个迁移可以分阶段实施。数据库稳定适合固化全量初始化基线，但改用 MyBatis 本身并不要求移除 Flyway。按本次需求，目标是两项都完成，不能以“已经有全量 SQL、运行时仍靠 Flyway”或“部分 Service 使用 Mapper、共享 Outbox 永久保留 JDBC”作为最终状态。

不建议目前增加独立进程的统一数据访问微服务。确有复杂多表写入时，可以在某个业务服务内部增加持久化门面；共享消息表的 Mapper 放在现有 cloud-messaging 库，不把五个领域的全部 Entity/Mapper 集中到新公共模块。

之前的 [服务分层调整方案](service-layering-refactor-plan.md) 中“可以继续用 JdbcTemplate”是此前的可选方案；本次需求明确选择 MyBatis，后续数据层以本文件为准。

## 2. 当前实际状态

| 项目 | 当前证据与含义 |
| --- | --- |
| 运行数据库 | [docker-compose.yml](../docker-compose.yml) 使用 `mysql:8.4`，统一 utf8mb4 / utf8mb4_0900_ai_ci |
| Schema 所有权 | `sic_iam/sic_project/sic_workflow/sic_material/sic_notification` 五库、五个服务账号；[init-databases.sh](../infra/mysql/init-databases.sh) 目前只建库、建账号、授权 |
| 表结构 | 13 个业务版本 SQL + 2 个共享版本 SQL；42 张业务表 + 每库 2 张消息表 = 52 张物理表定义，不含 Flyway 历史表 |
| 自动迁移 | 五个服务引入 Boot Flyway starter / flyway-mysql，集中配置开启 Flyway，并扫描 `db/migration,db/common` |
| 数据读写 | 37 个业务主源码文件直接引用 JdbcTemplate；公共消息库还有 4 个 JDBC 装配/数据/指标入口；SQL 散落在 Service、Controller、Policy、Consumer 和 Metrics |
| MyBatis | 没有业务 Mapper 或 MyBatis-Spring starter；Workflow 的 Flowable 8.0.0 传递引入 MyBatis core 3.5.19，不等于业务层已使用 Mapper |
| 测试 | 13 个测试文件手写 H2 建表；InfrastructureIntegrationTest 用 MySQL Testcontainers + Flyway 初始化，缺 Docker 会跳过 |
| 旧系统导入 | import-legacy.sql 检查五个 Schema 都存在 flyway_schema_history；cutover.ps1 先启动业务服务跑 Flyway，再停服务导入 |

五库完整表数分别为 IAM 9、Project 9、Workflow 22、Material 9、Notification 3。具体表名、来源 SQL 和 37 个 JDBC 文件均在范围清单中，避免只改几个 Repository 就遗漏授权、消费或指标 SQL。

本轮离线 Maven 全 reactor 依赖解析成功，确认 Flyway 11.14.1、MySQL Connector/J 9.7.0，以及 Workflow 的 MyBatis core 3.5.19。这些是当前 POM 的解析结果，不是对数据库已部署结构的实测。

## 3. 完整 SQL 初始化应是什么形态

### 3.1 建议目录与脚本职责

以下是目标目录，脚本尚未生成或接入运行：

```text
infra/mysql/
├── init-databases.sh            保留环境变量注入账号密码；调用全量初始化入口
├── bootstrap.sql               顺序入口：五库结构和种子数据
├── schema/
│   ├── iam.sql                 9 张表的最终 DDL
│   ├── project.sql             9 张表的最终 DDL
│   ├── workflow.sql            22 张表的最终 DDL
│   ├── material.sql            9 张表的最终 DDL
│   └── notification.sql        3 张表的最终 DDL
├── seed/
│   ├── iam.sql                 内置部门、角色、权限及其关联
│   └── workflow-assets.sql     离线生成的内置 BPMN 与节点/迁移配置
├── schema-manifest.json        发布基线、文件校验和、预期结构；不存密码
└── verify-schema.sql           核对表、列、索引、关键约束与种子数据

infra/mysql/patches/             将来确有结构变化时的人工审核升级 SQL
```

`bootstrap.sql` 可以通过 MySQL 客户端 `SOURCE` 执行各脚本，必须定义容器内/命令行下稳定的绝对路径；不能把带 SOURCE 的入口直接交给通用 JDBC SQL 分割器。各 Schema 文件单独带 `USE sic_<领域>`；空库整体入口相当于一套完整建立脚本，也允许发布时组合为单文件。

结构脚本中的两张消息表在五库各自完整展开；生成工具可共享模板，但发布文件不能依赖应用 JAR 内 `db/common`。配置部署和 Testcontainers 应使用同一批发布 SQL，减少测试结构与部署结构分叉。

### 3.2 必须合并到最终 DDL 的内容

不能直接把版本文件按字符串排序拼接，也不能只抽所有 CREATE TABLE。应以每库的数值版本顺序恢复最终结构，再固化为 CREATE TABLE：

| 历史来源 | 全量脚本中必须体现的最终结果 |
| --- | --- |
| 共享 V1 + V100 | outbox_event 的 next_attempt_at、lock_owner、locked_until、idx_outbox_dispatch；consumed_event 复合主键 |
| Workflow V4 | module_instance.started_by_user_id；节点与迁移配置表 |
| Workflow V5 | workflow_definition.status，初始 published 与 ACTIVE/DRAFT 状态一致 |
| Workflow V101 | uk_module_instance_project_code(project_id,module_code) 唯一索引 |
| Workflow V102 | project_snapshot 的 owner_user_id/owner_name_snapshot/dept_id、权限/任务恢复索引、expert_review_batch.access_ended_at |
| Material V3 | object_deletion_job 表及其唯一版本约束、清理调度索引 |
| Material V101 | generated_document 的 command_id 等 9 个附加字段、命令唯一键、材料版本索引 |
| IAM V2 | 部门、角色、权限以及 role_permission 的初始化，不能只保留 DDL |

建表显式指定 InnoDB、utf8mb4 和当前排序规则，保留 BIGINT、DECIMAL 精度、JSON、TIMESTAMP(6)、默认值、ON UPDATE、唯一键与索引。不要因 Mapper 建模顺手改表名、字段类型、字符集或业务约束；当前版本 SQL 未定义 FOREIGN KEY，初次切换也不额外增加跨库外键。

Workflow 的旧 expert_review 表即使当前评审代码主要使用 batch/assignment/score，也应包含在基线中，直到另行确认历史导入与数据保留要求。完整性以现有目标结构为准，不能仅按当前 Java 查询到的表来删减。

### 3.3 种子数据与应用初始化必须区分

目前 IAM 种子数据在 SQL 中，管理员账号由 BootstrapAdminInitializer 根据环境变量创建；Workflow V2 放的是三份简化 BPMN，随后 [WorkflowAssetInitializer](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/WorkflowAssetInitializer.java) 读取真实资源、更新定义并重建节点/迁移配置。

推荐固化正式内置工作流：用同一个 FlowableBpmnDefinitionParser 离线生成 workflow-assets.sql，包含 BPMN、definition_json、节点和迁移模型；按业务 code/version 关联定义，避免写死环境中的自增 ID。空库执行全量 SQL 后就有可用模型，不能只写三行占位定义然后声称业务初始化完整。

生成后的工作流种子与 Java 初始化只保留一个主要写入入口。目标可把常规启动初始化改为验证，显式 bootstrap 模式才安装缺失资产；不得在每次启动时覆盖已管理/发布的定义。离线导出与新入口属于实施工作，需验证新版定义优先规则、code/version、模型数量和状态一致性。

管理员密码仍通过配置与 PasswordEncoder 创建，不在完整 SQL 中硬编码明文或固定默认密码。材料类型目前会按上传/单据用例创建，不要导出所有环境动态数据作为公共种子。

### 3.4 空库与已有数据库使用不同入口

| 场景 | 处理方式 |
| --- | --- |
| 全新 MySQL 数据卷 | entrypoint 创建五库账号，再执行 bootstrap.sql；完成结构/种子校验才启动业务服务 |
| 五库已有完整结构与数据 | 备份并核对最终结构，关闭 Flyway 后直接接管；不重跑全量 DDL，不清空、不导入空库种子覆盖业务数据 |
| 已有库缺某些历史字段/索引 | 先确认实际差异，只执行审核后的增量补齐 SQL，再校验并接管 |
| 正在旧单体切换 | 先用全量 SQL 创建目标结构，保留旧库导入的独立步骤，再按业务/文件校验结果启动服务 |
| 将来少量结构更新 | 维护人工审核的 release patch 和更新后的全量基线；发布记录说明执行顺序、校验与回退条件 |

完整建立脚本建议 **仅用于空目标 Schema，并在非空时拒绝执行**。不使用 DROP/TRUNCATE 做默认“重建”；CREATE TABLE IF NOT EXISTS 也不能证明已有表结构正确。重复初始化应明确拒绝或验证已有结构后跳过，不能静默成功留下旧结构。

官方 MySQL 镜像仅在数据库目录尚未初始化时处理 docker-entrypoint-initdb.d；已有数据卷不会因为新增挂载脚本而自动执行。正常接管要走显式结构核对，而不是删除 volume 来触发初始化。[MySQL 镜像入口源码](https://github.com/docker-library/mysql/blob/master/8.4/docker-entrypoint.sh)

MySQL 的普通 DDL 存在隐式提交，整份建库脚本不能靠包一个事务实现全量回滚。初始化失败须报告已完成项，对隔离新库可重新创建验证；对现有数据必须走备份恢复或独立修复流程。[MySQL 隐式提交规则](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)

### 3.5 移除 Flyway 后要同步改的入口

| 文件/范围 | 后续修改 |
| --- | --- |
| 五个服务 POM | 去掉 spring-boot-starter-flyway、flyway-mysql；保留 datasource、连接池和 MySQL driver |
| 五份集中服务配置 | 删除 spring.flyway 配置；保持数据库 URL/账号；显式 spring.sql.init.mode=never，让部署 SQL 成为结构创建入口 |
| docker-compose.yml / init-databases.sh | 挂载全量 SQL；定义路径与执行顺序，初始化成功后放行业务依赖 |
| [cutover.ps1](../scripts/cutover.ps1) | 去掉“启动业务服务以执行 Flyway，再停服务”的建表步骤；改为直接执行结构 SQL；导入后再启动应用初始化/验证 |
| [import-legacy.sql](../infra/migration/import-legacy.sql) | 替换历史表存在检查：校验实际所需表、字段与约束及发布基线；不能简单删除所有前置条件。保留空目标、重复键、文件清单等检查 |
| verify-legacy-import.sql | 继续做数据一致性检查，增加/复用结构基线校验；结构验证和数据验证各自明确 |
| integration-tests POM/测试 | 移除 Flyway 依赖与程序化 migrate 调用；从部署同源全量 SQL 初始化隔离 MySQL Schema |
| IamSecurityConfigTest | 清理 spring.flyway.enabled=false 等失效测试配置，避免继续表达不存在的依赖 |
| db/migration / db/common | 历史 SQL 归档到非运行资源目录供追溯；全量脚本成为当前结构权威来源 |

已存在的 flyway_schema_history 可以先作为历史审计数据保留，应用无需读取它；删除它不是接管的前置条件。移除 Flyway 后必须另有结构核对和发布记录，但不必为了替代它再开发一整套自动迁移框架。

## 4. MyBatis Mapper 数据层方案

### 4.1 版本与依赖

当前 Boot 为 4.0.8、Java 21。建议先锁定 **mybatis-spring-boot-starter 4.0.1**，与参考 Demo 现有版本一致。官方对应关系为 starter 4.0 / MyBatis-Spring 4.0 / Boot 4.0+ / Java 17+；官方 4.0.1 发布记录属于 Boot 4.0 系列。本次不连带升级 Boot/Cloud。[MyBatis starter 要求](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)、[4.0.1 发布说明](https://github.com/mybatis/spring-boot-starter/releases/tag/mybatis-spring-boot-4.0.1)

在父 POM 的 dependencyManagement 管理 MyBatis starter 版本，五个数据服务显式声明 starter；MyBatis 版本不能假定由 Spring Cloud BOM 自动管理。MySQL driver 继续由现有 Boot 管理。Gateway、Discovery、Config 等无业务数据源应用无需加入 MyBatis。

隔离 PoC 的实际解析结果：starter/autoconfigure 4.0.1、mybatis-spring 4.0.0、mybatis core 3.5.19。与当前 Flowable 的 core 版本一致。仍需在正式 Workflow 构建中检查最终依赖树，保持一个可解析的 core 版本；不能创建第二套“Flowable 运行引擎数据源”来执行本项目业务 SQL。

### 4.2 目录与配置

```text
services/<领域>-service/
├── src/main/java/com/amatrix/sic/<领域>/
│   ├── dao/                    @Mapper 接口，按能力分组
│   ├── entity/                 内部持久化模型；可变插入对象承接主键回填
│   ├── dto/                    HTTP 请求/响应，查询投影可放 dto/query
│   ├── service/                业务用例与事务
│   └── config/MyBatisConfig.java
└── src/main/resources/mapper/<领域>/
    └── <能力>Mapper.xml
```

`Controller/Consumer → Service/Policy → Mapper → 本服务数据库`。SQL 优先使用 XML，与 Demo 的 dao/UserMapper + mapper/userMapper.xml 组织一致；复杂动态查询不塞入超长注解。Mapper 方法体现查询/原子写入动作，不实现授权、远程调用、RabbitMQ 发布或 MinIO 操作。

候选配置：

```yaml
spring:
  sql:
    init:
      mode: never
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    local-cache-scope: STATEMENT
```

业务 MyBatisConfig 用 `@MapperScan` 明确扫描本服务的 dao 包和 `com.amatrix.sic.messaging.dao`；扫描限定 `@Mapper` 接口，避免把其他接口当作 Mapper。XML namespace 与 Mapper 全限定名一致；查询投影字段使用 alias 或显式 resultMap，不依赖 Map 返回键的自动转换。

默认不启用二级缓存；STATEMENT 本地缓存范围用于避免事务内重复查询依赖会话缓存而失去预期的新鲜读取，尤其关注锁查询、权限和恢复扫描。锁查询仍需事务与实际数据库验证，缓存设置不能替代锁或隔离级别。[MyBatis 缓存配置](https://mybatis.org/mybatis-3/configuration.html)

### 4.3 各服务 Mapper 拆分建议

| 服务 | 候选 Mapper | 迁移重点 |
| --- | --- | --- |
| IAM | UserMapper、DepartmentMapper、RolePermissionMapper、ExpertQualificationMapper、IamOverviewMapper | 将 IamRepository 的账号/关联查询迁入 Mapper；内部 Controller 的统计 SQL 也移出。密码哈希只在内部模型，保留认证版本与角色权限加载 |
| Project | ProjectMapper、ProjectMemberMapper、ProjectGrantMapper、ProjectAccessLogMapper、AdminOperationLogMapper、ApplicationSagaMapper、UserSnapshotMapper | 包含权限判定、审计、目录快照、三个 Consumer 与 Metrics 的 SQL；Saga 的多个局部事务仍由服务编排 |
| Workflow | DefinitionMapper（含节点/迁移）、ModuleInstanceMapper、StateRecordMapper、WorkflowTaskMapper、NodeFormMapper、ExpertReviewMapper/ViewMapper、ApplicationMapper、ContractMapper、AcceptanceMapper、AchievementMapper、SettlementMapper、NoticeMapper、User/ProjectSnapshotMapper、MaterialReferenceMapper | 先查询与定义，再表单/结构化业务、评审，最后运行时锁与 Saga；初始化和 Catalog 也必须使用 Mapper |
| Material | MaterialTypeMapper、MaterialMapper、MaterialVersionMapper、GeneratedDocumentMapper、MaterialReservationMapper、ObjectDeletionJobMapper | 保留预留/命令幂等、版本生成、异步删除；材料预留 item 可随聚合 Mapper，不必每表建独立类 |
| Notification | PendingNotificationMapper | store、投递恢复与 Metrics 共用持久化入口；内存 SSE 连接不进入 Mapper |
| cloud-messaging | OutboxEventMapper、ConsumedEventMapper、MessagingStatsMapper（按复用程度可合并只读统计） | 去重、事件写入、租约领取、发布结果、失败重试及指标全部迁入 Mapper；OutboxService 等公共行为入口保持稳定 |

按能力划分 Mapper，不强制为 52 张表各创建一套 CRUD 服务。Workflow 的申请 Saga 主要使用模块等现有表，不存在一个单独的 workflow.application_saga 表；不要机械照搬 Project 的 SagaMapper。

原有 IamRepository / OutboxRepository 可以先作为适配门面包装 Mapper，减少调用方同时修改；其最终实现不再保存 JDBC SQL。业务 DTO 与 Entity 分开，多表读模型使用查询投影；MyBatis-Plus 不属于本次必要依赖，先采用普通 Mapper/XML。

### 4.4 shared cloud-messaging 必须一并设计

目前 [CloudMessagingAutoConfiguration](../platform/cloud-messaging/src/main/java/com/amatrix/sic/messaging/CloudMessagingAutoConfiguration.java) 以 JdbcTemplate Bean 为条件，并在 JDBC 自动配置之后创建 OutboxRepository/ConsumedEventGuard/MessagingMetrics。迁移后不能只替换五个业务 Service 而漏掉这个公共库。

建议公共库依赖匹配的 MyBatis core / MyBatis-Spring，保留普通库形态；五个宿主服务提供 starter 与自身 DataSource。公共 Mapper/XML 放库内，宿主扫描共享 Mapper；公共自动配置在 MyBatis 自动配置之后，以 SqlSessionFactory/必要 Mapper 可用为条件，注入 Mapper 到现有行为组件。

全限定 XML namespace 和包名隔离不同领域；每个进程内公共 Mapper 绑定宿主自己的 SqlSessionFactory，Outbox/Inbox 仍写到宿主 Schema。初期保留 JdbcTemplate 与 Mapper 共存便于分批迁移，终态数据读写全面使用 Mapper；不能将公共消息表迁到单独数据库/进程破坏本地事务。

特别验证 DLQ 工具：它依赖 cloud-messaging，但 [DlqRecoveryToolApplication](../platform/dlq-recovery-tool/src/main/java/com/amatrix/sic/dlq/DlqRecoveryToolApplication.java) 明确排除 DataSource 自动配置。新公共自动配置必须在无数据源/无 SqlSessionFactory 时仍可跳过，保留死信重放能力，不要求工具配置五库连接。自动配置注册文件若改全限定名也须同步。

LegacyFileMigrationTool 是旧库一次性工具，可保留 DriverManager 的受控例外，且与“业务数据层全面 Mapper”明确区分；如果希望连工具也统一，需单独为命令行旧库建 SqlSessionFactory，不能误用业务库连接。SQL 初始化/结构校验和测试夹具仍可通过 MySQL 客户端/JDBC 执行，它们不是在线业务读写。

### 4.5 事务和 SQL 语义必须保持

| 当前语义 | Mapper 迁移要求 |
| --- | --- |
| @Transactional / TransactionTemplate | 继续由 Service/消费入口管理；DataSource、SqlSessionFactory 和事务管理器绑定同一数据源；不手动提交 Spring 托管 SqlSession |
| Outbox 本地原子性 | 同一事务写业务表与 outbox_event，异常时一起回滚 |
| Inbox 消费去重 | consumed_event 标记与消费业务更新同事务；保留重复键处理语义，失败不能留下“已消费”标记 |
| 自增主键 | `useGeneratedKeys=true` / keyProperty 与可写插入参数配合；不可变 record 无法按普通 setter 回填，使用专用可变实体/插入参数 |
| SELECT ... FOR UPDATE | Mapper 显式锁查询；保留原锁范围、调用顺序、expectedSeq 和并发唯一键兜底；不得将锁查询放到无事务的远程 CRUD |
| 租约/CAS | Outbox 的 owner、locked_until、next_attempt_at 条件及更新行数 == 1 的判断保留 |
| INSERT IGNORE / ON DUPLICATE KEY | 保留幂等键、更新范围和返回行数处理；不能替换成先查后插而丢失并发语义 |
| JSON / UUID / 时间 / 金额 | 首轮 JSON 仍可用明确 String 字段，必要时加 TypeHandler；保留 CAST AS JSON、UUID 字符串、TIMESTAMP 微秒/时区、BigDecimal 精度及 NULL 语义 |
| 动态 SQL | 值用 `#{}`；动态排序/表名采用固定白名单/choose，避免把用户输入传入 `${}`；处理空集合、模糊匹配和 LIMIT |
| 查询投影与响应 | 用显式 alias/resultMap 保持字段名称、NULL、数字、时间和布尔语义；N+1 查询及权限分支按现有行为验证 |

MyBatis-Spring 支持现有 Spring 事务，但事务管理器与 SqlSessionFactory 必须使用同一个 DataSource；事务外 Mapper 写入会自动提交。[MyBatis-Spring 事务说明](https://mybatis.org/spring/transactions.html)

主键回填与参数映射依据 [MyBatis Mapper XML 文档](https://mybatis.org/mybatis-3/sqlmap-xml.html)。这些要求在迁移时需要测试，不应通过给 Mapper 每个方法加独立事务来“解决”多表原子性。

材料服务与 MinIO 的操作继续由业务 Service 编排：数据库事务不能覆盖对象存储事务。删除流程保持先落数据库删除任务再异步删对象；生成单据现有对象写入/回滚边界也要原样验证，Mapper 切换不顺便重写存储一致性协议。

## 5. 是否增加“数据访问服务”

| 解释/方案 | 当前建议 | 原因与适用条件 |
| --- | --- | --- |
| 各服务内的 Service → Mapper | **采用** | 已有服务、Schema 和事务所有权匹配，路径最直接 |
| 同一进程内的 XxxPersistenceService / Repository 门面 | **按需采用** | 复杂多表持久化、多处复用查询或屏蔽 Mapper 细节时有价值；业务权限与远程协作仍留应用 Service |
| 新建 cloud-persistence 公共库 | **当前无必要** | 通用基础功能可以复用，但不集中所有业务模型与 Mapper；user_snapshot 在不同服务结构已不同 |
| 一个独立 data-access-service 代理五库 | **当前不建议** | 新增网络延迟/不可用依赖、细粒度 RPC；原业务写入与 Outbox、去重与业务更新失去本地事务；集中掌握五库权限，领域变化互相耦合 |
| 独立只读报表/查询服务 | **将来有真实需求再评估** | 若跨域统计、复杂只读检索或负载隔离成为独立能力，可消费事件建立读模型，避免接管事务业务的原始表 |

若确实选择独立写数据服务，接口不能简单暴露通用 CRUD，而应承接完整原子写用例、幂等、Outbox/Inbox 和并发锁；现有业务服务将远程调用这些命令，权限、Saga、恢复和发布协议都要重新设计。这属于一次更大的服务边界重划分，当前源码未体现足以抵消成本的独立需求。

可选持久化门面不等于必须在 Service 与 Mapper 中间再加一层只转发参数的 DataAccessService。例如 Workflow 定义写入可以把定义/节点/迁移三组 Mapper 调用组织成持久化单元；简单 PendingNotification 查询直接使用 Mapper 即可。

## 6. 建议实施阶段与验收门槛

| 阶段 | 工作 | 退出条件 |
| --- | --- | --- |
| A：固定结构基线 | 从版本 SQL 与实库 SHOW CREATE TABLE 对照得到最终 DDL、种子和发布清单；按两套隔离 MySQL Schema 比对旧 Flyway 链和新全量 SQL | 列/类型/默认值/索引/唯一约束/种子等价，52 张目标表齐全；已有数据结构漂移有明确处理 |
| B：完整 SQL 接管 | 接入 Compose/命令行初始化、改 cutover/import guard/集成测试；对已有库验证后关闭并移除 Flyway | 新空卷可启动、旧数据可直接接管；不靠历史表判定完成，不靠清库实现切换 |
| C：Mapper 基础试点 | 锁定 starter 版本，Entity/XML/扫描/事务配置；从 Notification 或一个简单只读用例开始 | 宿主与 XML 装配正常；键回填、NULL/JSON 与事务回滚有验证；接口契约保持 |
| D：公共消息 Mapper | 迁移 cloud-messaging Outbox/Inbox/指标；保持行为入口兼容，再逐服务接入 | 消费去重与业务事务一致；租约并发、重试/确认有效；DLQ 无数据源仍可启动 |
| E：五个服务分批完成 | Notification → IAM → Project → Material → Workflow；也可先完成独立库试点，但每个库都有终态清单 | 清单内 37 个业务 JDBC 入口、共享库读写和初始化/权限/指标全部改用 Mapper；允许的工具/测试例外列明 |
| F：全量验收 | 源码依赖规则、契约/现有用例、MySQL 并发与切换演练、部署回退 | 符合目标结构，数据保持；不是只验证主源码编译或少量 Mock Controller 测试 |

阶段 A 可临时使用 Flyway 在隔离测试库中还原旧结构用于差异对照；目标部署与常规测试最终依赖完整 SQL，不能留下两个长期并行的结构权威。若测试暂保留旧链作为对照，明确它的仅研究用途与删除时间。

关键验收用例：

1. 空库执行全量脚本；重复执行符合拒绝/验证策略，部分失败有可定位报告；容器重启不重写数据。
2. 已有数据停止写入、备份、结构核对后接管；用户、项目、模块、材料 ID/关联保持，Flyway 历史不再是运行依赖。
3. 同一 Service 事务中多个 Mapper 写入及 Outbox 插入，故意失败后全部回滚；消费失败不会永久记为已消费。
4. MySQL 8.4 上验证模块 expectedSeq 并发、后继模块唯一键、材料版本冲突、预留幂等、生成命令唯一键、删除任务恢复与 Outbox 多实例 CAS。
5. IAM 登录/权限/JWT、Project Saga 查询/补偿、Workflow 评审与表单、Material 单据/上传删除、Notification SSE 与待投递、前端 API 契约均保持。
6. 替换 H2 手写装配为真实 Mapper/XML 的测试；H2 只适合快速辅助验证，MySQL JSON、upsert、索引与锁行为需真实 MySQL 覆盖。
7. 测试没有 Docker 而跳过时，不把 Maven 绿灯当作数据库验收通过；发布前需有实际 MySQL 对照报告与切换演练结果。

首次实施同时保持表结构、API 与事件协议稳定，可以逐服务回退到此前应用版本；若将来另加 DDL patch/修改种子，应用回退不自动恢复数据库。备份恢复和补偿由具体变更制定。

## 7. 本次已经验证的内容与边界

- 已读取 15 个版本 SQL，枚举 52 张表定义、37 个服务 JDBC 文件、4 个公共库 JDBC 文件，以及 13 个手写 H2 建表测试；清单与当前文件核对一致。
- 已检查集中 Flyway 配置、Compose 初始化、旧库导入门槛、cutover 启动建表步骤、公共消息自动配置、DLQ 的无数据源约定，以及工作流资产初始化。
- 全 14 个模块及父工程的离线依赖树解析成功。原始输出在 `target/database-stack-dependency-tree.txt`。
- 在 `target/mybatis-compatibility-research` 做了独立 PoC，POM 使用 Boot 4.0.8、MyBatis starter 4.0.1、Flowable 8.0.0 和 Java 21。**1 个 JUnit 测试通过，无失败/错误/跳过**：验证上下文自动配置、Mapper XML 加载、查询/主键回填，以及同 DataSource 下 Mapper/JDBC 共同提交与共同回滚。Surefire 使用当前工程已有的 3.5.5，未改变主工程插件。
- PoC 使用 H2 MySQL mode，仅证明基础集成与本地事务共存；没有执行完整 MySQL DDL、MySQL JSON/upsert/锁并发，未验证正式 Flowable 解析业务或所有服务上下文。当前终端未发现 docker/mysql 命令，本轮也没有访问已部署数据库，不能证明实库无漂移。

PoC 与原始日志在 target 中，属于可清理研究产物，关键结果已记录于本文。本次交付为调研方案和源码范围清单；上述阶段、全量脚本、正式 Mapper 以及部署验收属于后续实施工作，尚未执行。
