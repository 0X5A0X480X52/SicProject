# 权限乱码、部门管理及流程展示修复

2026-10-11 完成原因核查、代码修复、存量修复及本地验收。历史参照为 `fc8c604` 的 IAM 中文种子和既有前端请求契约；未回退整个服务或覆盖用户自定义名称。

## 原因与改动

- 完整 SQL 初始化使用新的 MySQL CLI 连接导入 IAM 种子，客户端默认 latin1；建表连接中的 `SET NAMES` 不会延续到另一个连接。初始化命令显式指定 utf8mb4，种子自身也设置 utf8mb4。真实 Docker 入口测试校验全部 36 个中文名称/描述字段。
- 全局管理员首次访问 `/api/admin/departments/members` 不指定 `deptId`，原后端返回 400，前端无法取得部门列表和管理按钮标志。现在返回全部可管理成员及部门；有部门参数则筛选。无部门的部门管理员返回空列表及禁用原因，跨部门查询仍拒绝。
- 前端部门编码可选，但 IAM 表要求非空唯一编码。空编码创建时生成 `DEPT_<UUID>`；空编码编辑保留已有编码，不改变表结构。
- 前端只分配部门时发送 `{}`。缺失 `roleCodes` 现在保留角色；明确列表仍按既有可编辑角色规则更新，角色专用更新接口缺少列表时返回 400。分配部门继续增加会话版本、撤销旧 token，并发布用户变更。
- BPMN、节点、转移查询原来统一要求定义管理查看权限，负责人默认没有该权限。业务展示现在允许读取确实被可访问项目的模块使用的定义；项目 ID 分页、每批最多 100 个，由 Project 校验 `VIEW_WORKFLOW`，匹配决定 ID 和项目 ID。无关用户或未关联定义拒绝；定义列表、上传、发布等管理入口保持原权限。

## 存量恢复

版本化补丁 `infra/mysql/patches/20261011_01_repair_iam_seed_text.sql` 按代码及精确错误字节匹配更新，不做全表重新解码。正常、自定义文本保留；不改变 ID、代码、账号或授权关系。补丁重复执行无新增改动，真实 MySQL 测试覆盖自定义名称保留和授权一致性。

日常环境 `sic-localdev` 已执行带备份恢复，36 个匹配字段全部修复，账号及授权状态校验一致。私有 SQL 备份位于 `target/iam-text-repair/`，报告只记录路径和摘要：[恢复报告](../verification/runs/localdev-iam-text-repair.json)。默认部门修改时间随名称更新，其他业务数据保留。

只更新日常环境 IAM、Workflow 的 JAR 挂载；`target/local-dev.compose.before-ui-fix.yaml` 保留原配置，原冻结版本保留。两者使用 `target/releases/ui-regression-candidate/`；另外六个应用内容与旧冻结版本相同，继续使用原挂载。数据库结构和已发布工作流不重建。

## 验收证据

| 范围 | 结果 |
| --- | --- |
| 全量 Maven 与真实基础设施 | [138 项测试，0 跳过，门禁通过](../verification/runs/ui-regression-test-gate.json) |
| 新库结构 | [五库 52 表通过](../verification/runs/ui-regression-schema.json) |
| Gateway 完整业务及新增场景 | [43 项检查、537 个 HTTP 请求通过](../verification/runs/ui-regression-business.json)，包含完整申请→合同→验收、两名真实资格审批专家、37 用户节点、3 批次、17 类材料 |
| 业务持久化 | [29 项数据库断言通过](../verification/runs/ui-regression-business-database.json) |
| 日常环境公开接口 | [中文、无部门参数查询、BPMN/节点读取通过](../verification/runs/localdev-ui-fix-public-reads.json) |
| 日常环境服务就绪 | [Gateway 8080、六业务服务及内部访问通过](../verification/runs/localdev-ui-fix-readiness.json) |

新增 Gateway 场景通过 `verify_business_lifecycle.py --ui-regression-cases` 启用：无部门注册、首次部门页面数据、空编码创建、`{}` 分配保留负责人/财务角色、真实审批专家角色保留、旧 token 失效、无部门/有部门负责人流程图与节点读取、无关用户和未关联定义拒绝。无部门展示测试项目独立记录为 `fixtures.departmentlessProjectId`；完整主流程使用随后创建的有部门项目。

CI 的 business 作业已启用该开关，真实 MySQL 门禁加入实际初始化测试。远程 CI 尚未执行。这里的前端契约验证通过 Gateway 发送请求，未声称完成浏览器点击或视觉验收。首轮 Maven 的失败来自 Mockito 将未设定的 Long 返回成 0，测试夹具改为明确的 null 后全量构建通过；原始日志保留在 `target/`。

临时隔离栈 `sic-ui-regression-20261011` 验收后停止，容器和数据保留；日常 `sic-localdev` 保持运行。前端无需重新编译，刷新页面即可取得新响应。

后续的部门创建限制、财务后补授权、已结束流程及专家专用界面见 [工作台权限迭代](workbench-permission-iteration.md)。本页描述的是前一轮证据；新的无部门回归改为断言申请被拒绝，不再创建无部门测试项目。本轮清理事故导致部分 `target` 历史备份和冻结包丢失，恢复范围与新备份位置也记录在上述说明中。
