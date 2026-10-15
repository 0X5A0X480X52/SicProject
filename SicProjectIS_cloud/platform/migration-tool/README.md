# migration-tool

旧单体本地 uploads 文件到 MinIO 的一次性迁移和校验工具。读取旧库材料版本、上传文件、保存迁移清单，并在业务数据导入后下载对象校验哈希/大小/数量。它是命令行程序，没有 HTTP 服务、注册中心或 Config Server 依赖。

**源码与命令**

实现位于 [LegacyFileMigrationTool](src/main/java/com/amatrix/sic/migration/LegacyFileMigrationTool.java)，Java 包为 `com.amatrix.sic.migration`。当前为单一工具类，内嵌旧文件行和环境配置 record，使用 JDBC 与 MinIO SDK。

| 命令 | 操作 |
| --- | --- |
| `prepare-files` | 读取 sic_legacy.material_version，校验旧文件，上传到 MinIO，写 sic_legacy.migration_file_manifest。 |
| `verify-files` | 读取 sic_material.material_version 中 legacy 前缀对象，逐一下载校验 SHA-256 和字节数，再与旧库清单数量对比。 |

必须且只能传一个命令，未提供或命令不支持时退出并报错。

**迁移链路与数据范围**

```text
导入旧库至 sic_legacy，提供旧 uploads 目录
    → prepare-files：路径/文件/旧哈希校验，上传对象，写清单
    → infra/migration SQL：将旧业务数据及清单映射进各服务库
    → verify-files：下载新库所引用的 legacy 对象，核对内容及数量
```

prepare-files 会检查 uploads 根目录、解析相对路径、旧 API 文件路径或完整 URL，并拒绝规范化后越出 uploads 根目录的路径。逐文件计算 SHA-256，旧库有哈希时核对一致，再上传到确定性对象 key：

```text
legacy/material-versions/{旧版本ID}/{清理后的文件名}
```

桶不存在时创建；每个文件上传后 upsert 清单，最后核对旧材料版本数与清单数。重复执行会向同一 key 上传并更新清单，发生中途错误时已上传对象和清单不会整体回滚。命令名为 prepare，但实际会写 MinIO 和旧库清单。

verify-files 使用新库保存的 object_key、sha256、size_bytes 校验对象，同时检查结果数量等于旧清单数。prepare-files 不会替代这个下载校验阶段。

本 Java 工具不执行完整数据库拆分导入；相关 SQL 位于 [infra/migration](../../infra/migration)，编排与切换由 [cutover.ps1](../../scripts/cutover.ps1) 和 [切换手册](../../docs/cutover-runbook.md) 负责。

**环境变量**

| 变量 | 默认值/要求 |
| --- | --- |
| `MIGRATION_JDBC_URL` | `jdbc:mysql://mysql:3306/sic_legacy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`。 |
| `MIGRATION_DB_USER` | root。 |
| `MYSQL_ROOT_PASSWORD` | 必填；实际作为 JDBC 密码，即使修改 DB_USER 仍读取此变量。 |
| `MINIO_ENDPOINT` | `http://minio:9000`。 |
| `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` | 必填。 |
| `MINIO_BUCKET` | sic-materials。 |
| `LEGACY_UPLOADS_ROOT` | `/legacy-uploads`，工具进程内可访问的目录。 |

必填凭据为空或以 CHANGE_ME 开头时拒绝执行。查询使用固定 sic_legacy / sic_material Schema，因此 JDBC 用户需要相应读写权限，不能仅通过 URL 改变工具涉及的库名。

Compose 使用 migration profile，`LEGACY_UPLOADS_PATH` 是宿主机源目录，默认 `./migration-input/uploads`，只读挂载至容器内 `/legacy-uploads`。它不同于工具读取的 LEGACY_UPLOADS_ROOT。

**构建、运行与验证**

以下命令在 `SicProjectIS_cloud` 根目录执行，需要 JDK 21 和 Maven：

```powershell
mvn -B -pl platform/migration-tool -am test
mvn -B -pl platform/migration-tool -am -DskipTests package

# 已按切换手册准备相应部署、旧库及文件后执行
docker compose --env-file .env --profile migration run --rm migration-tool prepare-files
docker compose --env-file .env --profile migration run --rm migration-tool verify-files
```

两个运行命令之间需要完成 SQL 导入阶段。使用已有部署时应补齐该部署实际的 project-name、env 和 override 文件；主流程优先按切换手册执行。

可执行包为 `platform/migration-tool/target/migration-tool-1.0.0-SNAPSHOT-exec.jar`，环境就绪时也可 `java -jar ... prepare-files` 或 `verify-files`。工具本身不读取 Spring application.yaml，参数从进程环境读取。

[LegacyFileMigrationToolTest](src/test/java/com/amatrix/sic/migration/LegacyFileMigrationToolTest.java) 验证旧 URL 解析及路径逃逸拒绝；完整文件迁移还需实际执行两阶段命令和切换验证。
