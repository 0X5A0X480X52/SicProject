# 版本化 SQL 补丁

`1.0.0-baseline` 的完整结构来自 `../schema/`，本次不改变业务表结构。
现有库通过 `verify_schema.py` 校验后接管，不重放完整建表脚本。

后续结构变更新增不可修改的版本化 SQL 文件与发布清单。清单至少记录：版本、文件 SHA-256、执行顺序、前置签名、目标签名、数据前置检查、停写要求，以及应用回退和数据库恢复步骤。发布前在真实 MySQL 上执行新库初始化、已有数据升级和恢复演练。

MySQL DDL 可能隐式提交，不能用 `START TRANSACTION` 宣称整组结构变更可以回滚。先恢复到辅助库并核对，再通过发布操作切换；不提供自动清库或自动降级结构脚本。

## 20261011_01：IAM 内置中文文本修复

`20261011_01_repair_iam_seed_text.sql` 只用于 UTF-8 种子被 MySQL latin1 客户端误解码后的已有 IAM 库。参照 `fc8c604` 的原始中文，按代码和精确错误字节匹配修复 23 个权限名称、6 个角色名称及描述、默认部门名称；正常或用户自定义文本保留。不改表结构、ID、代码、账号、角色分配或角色权限。补丁可以重复执行；默认部门名称修复会更新该行修改时间。

先在 Cloud 目录预览，再执行带备份的应用：

```powershell
python scripts/repair_iam_seed_text.py --project-name sic-localdev --env-file target/local-dev.env --compose-file docker-compose.yml --compose-file target/local-dev.compose.yaml
python scripts/repair_iam_seed_text.py --project-name sic-localdev --env-file target/local-dev.env --compose-file docker-compose.yml --compose-file target/local-dev.compose.yaml --apply
```

备份写入忽略提交的 `target/iam-text-repair/`，报告记录摘要、匹配字段数和账号/授权状态校验，不包含凭据。应用失败或状态校验失败应检查报告，不自动覆盖或清库。后续新库通过初始化脚本的显式 UTF-8 客户端和种子自身的 `SET NAMES utf8mb4` 防止复发；`build_iam_text_repair.py --check` 校验已发布补丁与参照文本一致。
