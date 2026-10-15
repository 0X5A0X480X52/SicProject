# 完整业务端到端测试需求与用例

调研日期：2026-10-10。用例编号：`E2E-BIZ-001`。

## 1. 交付范围与当前状态

设计一条从账号注册开始，经角色配置、两名专家资格申请及两级审批，完成 **限项项目申请 → 纵向合同 → 校外结题验收 → 项目结束** 的自动化用例。

本次已核对前端请求、后端约束和三个内置 BPMN，提供逐节点步骤、请求模板、断言、异步等待规则、机器可读用例与空白上传 PDF。**尚未实现或运行完整 HTTP runner，也没有生成业务全流程通过报告。**

现有 [verify_compose_e2e.py](../scripts/verify_compose_e2e.py) 的 8 项检查覆盖 readiness、容器健康、Config/Eureka、未登录响应、管理员 JWT、服务路由、SSE 和注销；[compose-e2e.json](../verification/compose-e2e.json) 是这些检查的历史结果，不代表本用例已跑通。现有 verifier 只有一个管理员 session/token，request 方法主要发送 JSON，没有多角色、multipart、业务状态驱动和专家评分编排。

完整业务用例应作为独立阶段接在现有基础验证后。失败区分环境未就绪、账号初始化失败、流程编排失败、业务断言失败，不能把“HTTP 都是 200”当作完成。

## 2. 请求模型与测试边界

- 所有业务请求经过 Gateway 的 `/api/**`，使用前端相同的路径、方法、JSON 字段和 multipart 字段；不调用 `/internal/**`，不写 SQL 创建角色、批次或流程状态。
- `Accept: application/json`；受保护请求为 `Authorization: Bearer <当前操作者 token>`；JSON 请求为 `Content-Type: application/json`。
- 上传使用 multipart/form-data 的 `file` 字段，HTTP 库自动生成 boundary，不手动设 JSON Content-Type。
- 每名操作者独立 token/session。管理员凭据由环境注入，注册用户密码运行时生成或由测试环境注入；日志、报告不保存密码和完整 token。
- 前端 `apiRequest` 解包的是 `ApiResponse.data`。Python HTTP runner 应同时检查 HTTP 状态和顶层 `success`，自行取 `data`，不要把两种响应层级混用。
- “模仿前端请求”是 API 端到端测试，不自动等于浏览器 UI 自动化；按钮可见性、路由切换、页面校验、SSE 刷新需要另加浏览器用例。

## 3. 环境与角色数据

前提：现有启动验证通过；数据库迁移、初始管理员、公私钥、Redis、RabbitMQ、MinIO、服务发现、三个流程定义均可用。预检读取当前 active definition 的节点与 transitions，核对用例对应版本；若已发布新版流程，明确报告版本不匹配，不能默默套用旧节点。

每次运行生成 `runId`，账号为 `e2e_<runId>_<alias>`，部门 code 和项目 code 为 `E2E_<runId>`。全部 ID 从响应保存，不硬编码部门、用户、项目、模块、节点配置、批次或材料版本 ID。

| 别名 | 来源 / 初始角色 | 用途 |
| --- | --- | --- |
| administrator | 已有 `SYSTEM_ADMIN`；凭据注入 | 建立测试部门、分配角色、分配项目财务授权 |
| leader | 注册；配置 `PROJECT_LEADER` | 自助发起项目，填写申请/合同/验收，签字与提交 |
| department | 注册；配置 `DEPT_ADMIN` | 同部门专家资格初审、项目部门审核、院级专家指派 |
| science | 注册；配置 `SCIENCE_ADMIN` | 资格终审、科技处审核、主管部门结果录入、专家指派/汇总 |
| finance | 注册；配置 `FINANCE_ADMIN` | 验收财务决算；还需该项目的财务授权 |
| expert1 | 注册；初始无角色，审批后获得 `EXPERT` | 三个批次中的第一名专家 |
| expert2 | 注册；初始无角色，审批后获得 `EXPERT` | 三个批次中的第二名专家 |

共 6 个新账号与 1 个已有管理员。另一个部门的普通账号用于负向用例，可单独注册。department、leader、两位申请专家使用同一测试部门，满足资格部门审核与项目部门权限约束。science、finance 也可使用该部门以简化夹具。

当前只有 6 种内置角色，没有独立主管部门角色。BPMN 中 `Authority*Task` 的 candidateRole 为 `SCIENCE_ADMIN`，应由 science 录入；不虚构 `AUTHORITY_ADMIN`。主业务办理不用 SYSTEM_ADMIN 替代各角色，避免掩盖权限错误。

## 4. 初始化请求：注册、角色、资格与项目

### 4.1 管理员登录与部门建立

`POST /api/auth/login`：`{"username":"<admin username>","password":"<admin password>"}`。保存 `data.token`，检查 `/api/auth/me` 返回 SYSTEM_ADMIN。

`POST /api/admin/departments`：

```json
{"deptCode":"E2E_<runId>","deptName":"端到端测试部门","parentDeptId":null,"enabled":true}
```

保存响应的 `deptId`。这一步是账号注册所需部门的准备工作。

### 4.2 批量注册与角色设置

对 6 个别名调用 `POST /api/auth/register`：

```json
{"username":"e2e_<runId>_leader","password":"<generated password>","realName":"测试负责人","deptId":123,"phone":"","email":"e2e-leader@example.invalid"}
```

用响应 `data.user.userId` 保存各用户 ID。当前注册只创建用户，不自动分配 PROJECT_LEADER，不能把注册成功当作已有业务角色。

管理员逐用户 `PUT /api/admin/users/{userId}/roles`，body 为 `{"roleCodes":["PROJECT_LEADER"]}` 等；expert1、expert2 保持 `[]`，不得直接授予 EXPERT 绕过资格审批。

**每次角色变更后重新登录受影响账号。** 当前角色变更提升 auth_version，使注册阶段旧 token 无效；旧 token 请求 `/api/auth/me` 应为 401，新 token 应得到配置后的角色。管理员 token 不应因修改其他用户角色而失效。

### 4.3 两名专家分别申请与审批

expert1、expert2 各自 `POST /api/expert-qualification/applications`：

```json
{"specialty":"科研项目评审","professionalTitle":"副教授","applicationReason":"端到端测试专家资格申请"}
```

保存各自 `data.applicationId`，状态应为 `PENDING_DEPT_REVIEW`。当前资格 API 没有附件字段，这一步无需上传 PDF。

department 对两份申请分别调用 `POST /api/admin/expert-qualification/applications/{applicationId}/dept-review`：

```json
{"approved":true,"opinion":"同意推荐","remark":"E2E 部门初审"}
```

状态应为 `PENDING_SCIENCE_REVIEW`。science 调用对应 `/science-review`，相同字段结构，状态应为 `APPROVED`。

审批后重新登录两位专家，断言 `/api/auth/me` 包含 EXPERT，`GET /api/expert-qualification/my` 返回 `expert=true`、`hasActiveApplication=false`，历史申请为 APPROVED。后续 Project 用户快照中也必须可见 EXPERT，必要时有界轮询，不能立即用旧快照指派。

### 4.4 负责人发起限项申请

leader `POST /api/project-applications/start`：

```json
{"projectCode":"E2E_<runId>","projectName":"E2E 完整科研项目","projectType":"VERTICAL","projectLevel":"PROVINCIAL","approvedAmount":1000,"startDate":"2026-01-01","endDate":"2027-12-31","applicationTitle":"E2E 限项申请","isLimitedProject":true,"applicationSummary":"完整业务链路测试"}
```

保存 `data.projectId`、`data.moduleInstanceId`。初始 runtime 应为 `SubmitApplicationTask / APPLICATION_DRAFT`，seq=1，leader 可操作。当前 BPMN 实际起始边是 `StartEvent → SubmitApplicationTask`；发布申报通知节点未接入这条起始主路径，不能仅按 XML 节点排列加入必跑步骤。

`isLimitedProject=true` 保证部门审核后进入院级专家评审；false 会跳过该阶段。应在提交前核对初始化的申请数据，主用例不在节点表单中改成非限项。

### 4.5 项目财务授权与快照就绪

administrator `POST /api/projects/{projectId}/finance-grants`：

```json
{"userId":456,"moduleType":"ACCEPTANCE","roundNo":null,"taskNodeId":null,"reason":"E2E 验收财务办理"}
```

仅有 FINANCE_ADMIN 全局角色不够，需要该项目财务 grant。用 science 读取 `GET /api/projects/{projectId}/authorization`，确认负责人、财务 grant，以及候选用户中两位专家的资格角色。

## 5. 通用办理、材料与前端 payload

每个节点先 `GET /api/module-instances/{moduleId}/runtime-view`，核对当前节点/状态、`canOperate`、`currentCandidateRoleCode`、openTasks、当前 seq/round。eventType 必须在 `availableTransitions` 中，formCode 与材料类型取当前 runtime；下表为当前内置版本的基线。

普通推进为 `POST /api/module-instances/{moduleId}/transitions`：

```json
{"eventType":"DEPT_REVIEW_FINISHED","expectedSeq":2,"result":"APPROVED","remark":"E2E 审核通过","materialVersionIds":[],"formCode":"DEPT_APPLICATION_REVIEW_FORM","nodeFormData":{"projectId":123,"moduleInstanceId":789,"runtimeRecord":{"checkItem":{"itemCode":"DEPT_APPLICATION_REVIEW_FORM","itemName":"Department review","itemType":"REVIEW_RESULT","itemValue":"true","itemResult":"APPROVED","required":true,"passed":true,"remark":"E2E 审核通过","sortNo":1,"moduleInstanceId":789}}}}
```

数字仅为结构示例，实际值由响应绑定。`expectedSeq` 每次从最新 runtime 读取；不能全程递增本地常量，因为评分完成会自动推进。审核时使用前端实际 `result=APPROVED`，不能照抄边上的 `REVIEW_FINISHED` 作为唯一通过条件，否则可能走退回分支。expert summary 的通过条件还读取已完成批次的 final_result，不用额外伪造条件字段。

节点表单严格复用前端 [useNodeFormHelpers.ts](../../SicProjectIS_fronted/src/composables/useNodeFormHelpers.ts) 的 `buildRequestFromFlatForm` 嵌套结构：

| dataKind | nodeFormData 内业务部分 | 样例内容 |
| --- | --- | --- |
| APPLICATION_DRAFT | `applicationDraft.application/extension/detail` | 标题、限项=true、摘要、预算和研究内容 |
| CONTRACT_DRAFT | `contractDraft.contract/extension` | 合同编号/名称、金额1000、甲乙方 |
| ACCEPTANCE_DRAFT | `acceptanceDraft.acceptance/extension` | 证书号、结论、验收类型、完成率100、批次号 |
| CHECK_ITEM | `runtimeRecord.checkItem` | passed=true、itemResult=APPROVED |
| EXTERNAL_RESULT | `runtimeRecord.externalResult` | 主管部门、批准文号、externalResult=APPROVED |
| SEAL | `runtimeRecord.sealRecord` | 用印事项、签字/盖章状态、份数 |
| SUBMISSION / ARCHIVE | `runtimeRecord.submissionRecord/archiveRecord` | 报送/回执号，归档号及位置 |
| PUBLICITY | `projectRecord.publicity` | 公示标题、范围、无异议、推荐信息 |
| FINANCIAL_SETTLEMENT | `projectRecord.financialSettlement` | receivedAmount=1000、spentAmount=800、结算通过 |
| EXPERT_REVIEW / DOCUMENT | 主办理面板只输出 remark | 专家业务用专门批次/评分 API；只读文档不伪造可写表单 |

所有表单保留前端公共 `projectId/moduleInstanceId` 上下文。前端财务表单要求到账和支出字段，两者均填写。完整 payload 的保存与读回作为独立断言；目前存在已知实现缺口，见第 9 节。

### 5.1 空白 PDF 与上传

夹具为 cloud 根目录下 `output/pdf/blank-upload.pdf`：一页 A4、无文字、535 字节、有效 PDF，不是零字节文件。零字节上传会被后端 `file.isEmpty()` 拒绝。

```python
with open(fixture_path, "rb") as file:
    response = session.post(
        f"{gateway}/api/projects/{project_id}/materials/{material_type}/versions",
        headers={"Accept": "application/json", "Authorization": f"Bearer {actor_token}"},
        files={"file": ("blank-upload.pdf", file, "application/pdf")},
        timeout=request_timeout,
    )
```

前端选取的版本 ID 是 `data.context.materialVersionId`，响应还包含 `data.version`。保存版本 ID，然后 GET 材料列表确认项目、typeCode、isCurrent；下载 `GET /api/material-versions/{id}/download`，校验字节/hash 与原夹具一致。

同一个空白 PDF 可按 17 个不同必填 typeCode 分别上传，但每个响应中的 materialVersionId 都独立保存，不能把一个类型的版本当作所有节点的材料。验收申请节点同时需要两种类型。普通专家默认不能 MATERIAL_WRITE，评审节点选填附件可由负责人或 science 上传。

推进时把选中的当前版本放入 materialVersionIds，不能只上传不引用。若文档生成异步产生新版本，应重新读取列表，选择当前版本；手工占位文件测试不等于验证自动生成文档功能，自动生成成功/内容需独立断言。

## 6. 当前内置 BPMN 的逐节点成功路径

角色别名见第 3 节。网关由后端在一次 transition 中自动解析，不单独发送 gateway 的 eventType。APPLICATION 14 个、CONTRACT 10 个、ACCEPTANCE 13 个，共 37 个办理节点；其中 3 个评审节点由评分完成事件自动推进。

### APPLICATION

| 次序 | 当前节点 | 操作者 | eventType | 下一节点 | 必填材料类型 |
| --- | --- | --- | --- | --- | --- |
| 1 | `SubmitApplicationTask` | leader | `USER_CONFIRMED_SUBMIT` | `DeptReviewTask` | `PROJECT_APPLICATION_FORM` |
| 2 | `DeptReviewTask` | department | `DEPT_REVIEW_FINISHED` | `DeptExpertAssignTask` |  |
| 3 | `DeptExpertAssignTask` | department | `DEPT_EXPERT_ASSIGNED` | `DeptExpertReviewTask` |  |
| 4 | `DeptExpertReviewTask` | expert1+expert2（评分后等待自动推进） | `DEPT_EXPERT_REVIEW_SUBMITTED` | `DeptExpertSummaryTask` |  |
| 5 | `DeptExpertSummaryTask` | department | `DEPT_EXPERT_REVIEW_FINISHED` | `ScienceOfficeInitialReviewTask` |  |
| 6 | `ScienceOfficeInitialReviewTask` | science | `SCIENCE_INITIAL_REVIEW_FINISHED` | `ScienceExpertAssignTask` |  |
| 7 | `ScienceExpertAssignTask` | science | `SCIENCE_EXPERT_ASSIGNED` | `ScienceExpertReviewTask` |  |
| 8 | `ScienceExpertReviewTask` | expert1+expert2（评分后等待自动推进） | `SCIENCE_EXPERT_REVIEW_SUBMITTED` | `ScienceExpertSummaryTask` |  |
| 9 | `ScienceExpertSummaryTask` | science | `SCIENCE_EXPERT_REVIEW_FINISHED` | `ScienceOfficePublicityTask` |  |
| 10 | `ScienceOfficePublicityTask` | science | `PUBLICITY_FINISHED` | `ScienceOfficeSubmitTask` |  |
| 11 | `ScienceOfficeSubmitTask` | science | `SCIENCE_SUBMIT_TO_AUTHORITY` | `AuthorityReviewTask` |  |
| 12 | `AuthorityReviewTask` | science | `AUTHORITY_REVIEW_FINISHED` | `SignAndSealTask` | `AUTHORITY_APPROVAL_FILE` |
| 13 | `SignAndSealTask` | leader | `SIGN_AND_SEAL_COMPLETED` | `SubmitFinalMaterialsTask` | `SIGNED_APPLICATION_FORM` |
| 14 | `SubmitFinalMaterialsTask` | science | `FINAL_MATERIALS_SUBMITTED` | `ApprovedEndEvent` | `FINAL_APPLICATION_MATERIALS` |

### CONTRACT

| 次序 | 当前节点 | 操作者 | eventType | 下一节点 | 必填材料类型 |
| --- | --- | --- | --- | --- | --- |
| 1 | `AuthorityApproveProjectTask` | science | `PROJECT_APPROVAL_REGISTERED` | `FillContractTask` | `PROJECT_APPROVAL_FILE` |
| 2 | `FillContractTask` | leader | `CONTRACT_CONFIRMED_SUBMIT` | `DeptReviewTask` | `CONTRACT_DRAFT` |
| 3 | `DeptReviewTask` | department | `DEPT_CONTRACT_REVIEW_FINISHED` | `ScienceOfficeReviewTask` |  |
| 4 | `ScienceOfficeReviewTask` | science | `SCIENCE_CONTRACT_REVIEW_FINISHED` | `AuthorityReviewTask` |  |
| 5 | `AuthorityReviewTask` | science | `AUTHORITY_CONTRACT_REVIEW_FINISHED` | `PrintPdfContractTask` |  |
| 6 | `PrintPdfContractTask` | leader | `CONTRACT_PDF_PRINTED` | `LeaderSignTask` | `CONTRACT_PDF` |
| 7 | `LeaderSignTask` | leader | `LEADER_SIGN_COMPLETED` | `SchoolSealTask` | `LEADER_SIGNED_CONTRACT` |
| 8 | `SchoolSealTask` | science | `SCHOOL_SEAL_COMPLETED` | `AuthoritySealTask` | `SCHOOL_SEALED_CONTRACT` |
| 9 | `AuthoritySealTask` | science | `AUTHORITY_SEAL_COMPLETED` | `ArchiveContractTask` | `AUTHORITY_SEALED_CONTRACT` |
| 10 | `ArchiveContractTask` | science | `CONTRACT_ARCHIVED` | `ApprovedEndEvent` | `ARCHIVED_CONTRACT` |

### ACCEPTANCE

| 次序 | 当前节点 | 操作者 | eventType | 下一节点 | 必填材料类型 |
| --- | --- | --- | --- | --- | --- |
| 1 | `PublishAcceptanceNoticeTask` | science | `ACCEPTANCE_NOTICE_PUBLISHED` | `DeptNotifyLeaderTask` |  |
| 2 | `DeptNotifyLeaderTask` | department | `DEPT_LEADER_NOTIFIED` | `FinancialSettlementTask` |  |
| 3 | `FinancialSettlementTask` | finance | `FINANCIAL_SETTLEMENT_COMPLETED` | `SubmitAcceptanceMaterialsTask` | `FINANCIAL_SETTLEMENT_LIST` |
| 4 | `SubmitAcceptanceMaterialsTask` | leader | `ACCEPTANCE_CONFIRMED_SUBMIT` | `DeptReviewTask` | `ACCEPTANCE_APPLICATION_FORM`, `ACCEPTANCE_REPORT` |
| 5 | `DeptReviewTask` | department | `DEPT_ACCEPTANCE_REVIEW_FINISHED` | `ScienceOfficeReviewTask` |  |
| 6 | `ScienceOfficeReviewTask` | science | `SCIENCE_ACCEPTANCE_REVIEW_FINISHED` | `AuthorityReviewTask` |  |
| 7 | `AuthorityReviewTask` | science | `AUTHORITY_ACCEPTANCE_REVIEW_FINISHED` | `SignSealAcceptanceTask` |  |
| 8 | `SignSealAcceptanceTask` | leader | `ACCEPTANCE_SIGN_SEAL_COMPLETED` | `SubmitFinalMaterialsTask` | `SIGNED_ACCEPTANCE_MATERIALS` |
| 9 | `SubmitFinalMaterialsTask` | science | `ACCEPTANCE_FINAL_MATERIALS_SUBMITTED` | `ExpertAssignTask` | `FINAL_ACCEPTANCE_MATERIALS` |
| 10 | `ExpertAssignTask` | science | `EXPERT_ACCEPTANCE_ASSIGNED` | `ExpertReviewTask` |  |
| 11 | `ExpertReviewTask` | expert1+expert2（评分后等待自动推进） | `EXPERT_ACCEPTANCE_REVIEW_SUBMITTED` | `ExpertSummaryTask` |  |
| 12 | `ExpertSummaryTask` | science | `EXPERT_ACCEPTANCE_REVIEW_FINISHED` | `IssueCertificateTask` |  |
| 13 | `IssueCertificateTask` | science | `ACCEPTANCE_CERTIFICATE_ISSUED` | `AcceptedEndEvent` | `ACCEPTANCE_CERTIFICATE` |

申请最终状态为 `APPLICATION_APPROVED`，合同为 `CONTRACT_APPROVED`，验收为 `ACCEPTANCE_ACCEPTED`。校外路径包含主管部门结果、签字及最终材料，并最终进入验收专家评审。

## 7. 每次专家评审的完整编排

在 `DeptExpertAssignTask`、`ScienceExpertAssignTask`、`ExpertAssignTask`，由当前 department/science 操作者创建 **新的** batch：`POST /api/expert-reviews/batches`。

```json
{"moduleInstanceId":789,"workflowNodeId":321,"reviewType":"APPLICATION","reviewTitle":"E2E 院级评审","ruleType":"AVERAGE","minExpertCount":0,"passScore":60,"recommendScore":85,"expectedExpertCount":0}
```

`workflowNodeId` 是 runtime 的 `context.currentWorkflowNodeId` 数字配置 ID，不是 BPMN 字符串 nodeId；reviewType 取当前 moduleType。min/expected=0 是当前 `ExpertReviewPanel.ensureBatch()` 的实际请求值，不代表保证有两人。用例必须独立断言两名专家已指派；服务汇总时会按实际 assignment 数量更新 expected/min。

保存 `data.batch.batchId`。对两个专家分别 `POST /api/expert-reviews/batches/{batchId}/assignments`：`{"expertUserId":<id>,"expertName":"测试专家一","expertOrg":"端到端测试部门"}`。响应中 assignmentId 位于 `data.assignments[i].assignment.assignmentId`，按 expertUserId 找对应记录，不依赖数组顺序。

**先指派满两人，再离开指派节点，再评分。** 断言两条不同 assignment；不要先邀请一人就评分，实际完成门槛按当前指派数计算，可能提前完成。指派 API 会同步 Project 专家 grant，不应只调用 `/expert-grants` 而省略真实评审 assignment。

当前指派人发对应 `*_EXPERT_ASSIGNED` transition，确认进入 ReviewTask；两位专家分别登录、GET runtime 验证 canOperate=true，再调用 `POST /api/expert-reviews/assignments/{assignmentId}/scores`：

```json
{"valid":true,"conflictOfInterest":false,"reviewResult":"APPROVED","reviewComment":"E2E 综合评分","scores":[{"itemCode":"OVERALL","itemName":"综合评分","scoreValue":90,"weight":1,"maxScore":100}]}
```

expert1=90、expert2=80，平均85；阈值60/85时最终结果应为 `RECOMMENDED`，不是简单照抄个人 reviewResult=APPROVED。第一人提交后 submitted=1、未完成且仍在 ReviewTask；第二人提交后 submitted=2、valid=2、finalScore=85、status=COMPLETED。

后端 `ExpertReviewCompletedConsumer` 自动执行对应 `*_EXPERT_REVIEW_SUBMITTED`。**此处只轮询等待 SummaryTask，不再手动提交该 transition。** 使用 science 或下一节点操作者轮询，因为专家阶段结束后可能失去该模块访问权限。最后由 department/science 提交 summary 的 FINISHED 事件，进入下一节点。

三个批次复用两名合格专家，但 batchId、assignmentId、workflowNodeId 和授权的节点作用域各不相同。跨批次沿用旧 assignment 应被拒绝。离开专家节点后的授权结束事件也应收敛，不能残留整项目永久专家授权。

## 8. 自动后继模块、等待与最终断言

APPLICATION 完成时 Workflow 自动创建 CONTRACT；CONTRACT 完成时自动创建 ACCEPTANCE。测试 **不再次 POST 创建这两个模块**，否则可能得到“already exists”冲突，也没有验证自动衔接。

当前前端可用 `GET /api/workflow-workbench/items` 查可办理模块。完成边界由 science 轮询工作台，按 projectId + moduleType 定位新模块，获得 moduleInstanceId 并检查新 runtime：CONTRACT 初始 `AuthorityApproveProjectTask`，ACCEPTANCE 初始 `PublishAcceptanceNoticeTask`。工作台按权限过滤，不能用 leader 的空列表认定 science 的后继模块未创建；当前没有发现用于列举全部模块的公开 GET `/projects/{id}/module-instances`。

轮询为有界重读，建议初值 interval=0.5秒、deadline=30秒，运行环境可配置。等待资格角色快照、评审自动推进、后继模块可见、Project 生命周期与授权撤销，不用固定 sleep 猜测。HTTP 写超时不能盲目重发；先读最新 runtime/batch 和 seq，确认操作是否已提交。重复评分会返回冲突。

每次普通 transition 成功后确认目标节点、seq 增加1、原任务关闭、新任务对应角色/负责人；每个专家自动 transition 也只能产生一个 seq。完成后检查：

1. 三模块最终状态正确、finished=true、各自无 OPEN 任务，history 序号连续无重复。
2. 三个评审批次、每批两条已提交且有效 assignment，汇总分/结论正确。
3. `GET /api/projects` 的目标项目最终 `lifecycleStage=PROJECT_FINISHED`；该字段来自异步 Project 消费，不能只看 Workflow finished。
4. 所有必填材料可下载，选定版本与相应 state record 关联；材料确认事件收敛。关联/确认没有公开读接口的部分可在测试环境用只读数据库断言，不通过 SQL推进状态。
5. 表单内容能够通过实际公开读接口读回，专家资格、角色变更和相关授权事件可追踪。页面展示所用表单/业务数据都应有业务断言。

## 9. 已发现的实现差距与必须失败的断言

这些是静态源码发现，尚未做在线故障复现，不能把它们跳过后宣称完整通过。

| 问题 | 源码依据 | 测试处理 |
| --- | --- | --- |
| 流转表单写入键与表单读取键不同 | Runtime 把 payload 写 `node_form.node_id=当前 BPMN nodeId`；NodeFormService 单实例按 formCode、历史按 `formCode-%` 读；formCode 参数未参与 Runtime 写入 | 用 `GET /api/node-forms/{formCode}?projectId=...&moduleInstanceId=...` 验证读回。数据缺失作为失败，不只断言流转成功 |
| 普通提交未同步结构化草稿 | transitions 写 node_form；business-data 从 project_application/contract_form/acceptance_form 读；独立 StructuredBusiness API 才写对应表，当前主页面未调用它 | 填写新的合同/验收内容后检查 business-data；不能自动追加前端未发送的 PUT 来掩盖差异 |
| 校级验收分支缺少当前前端输入链路 | Runtime 读取顶层 isSchoolLevelAcceptance 或 acceptance_form 同名字段；当前主表单 builder 没输出该字段 | 主用例走默认校外路线；校级分支另设“待前端/后端契约接通”的用例，不注入私有 flag 假装前端已支持 |
| 两人要求不是服务配置自动保证 | 前端 batch min/expected 为0，refreshAggregate 使用实际 assigned 数 | 用例强制邀请两人、分别评分，并加一个人评分后不推进的断言 |

后续修复表单写读契约后，同一个主用例应升级为全部业务数据断言均通过，不把缺口永久标记 skipped。可以分别记录 workflowTraversalPassed 与 businessDataPassed，但 overallPassed 必须两者都成立。

## 10. 补充负向和并发用例

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| N01 | 未登录发受保护请求；角色更新/资格审批后使用旧 token | 401；新 token 正常 |
| N02 | 未经资格审批直接指派专家；重复提交资格申请；越部门审批 | 业务拒绝；跨部门审批403；不能获得 EXPERT |
| N03 | 负责人执行部门审核、专家代别人评分、财务无项目 grant 办理 | 403；状态/分数不变 |
| N04 | 漏必填材料、材料属于别的项目、同类型旧版本 | 缺失/跨项目应拒绝；前端要求当前版本，后端是否同样拒绝旧版本单独记录 |
| N05 | 相同 expectedSeq 双提交、重复评分 | 只有一次生效；过期 seq/重复评分冲突；无重复 state record |
| N06 | 仅一人评分后尝试手动推进评审 | 必须拒绝，batch仍未完成 |
| N07 | 部门审核退回再修改提交 | 回到申请节点；旧轮次/旧专家 assignment 不能用于新轮次 |
| N08 | 专家低于60分，验收走失败文件和余款退回 | 最终 ACCEPTANCE_REJECTED；与通过路径分别验证 |
| N09 | Project/Redis/RabbitMQ 延迟，与批量授权或普通推进混合 | 错误有界、连接释放、不会重放无幂等写；记录锁等待和请求耗时 |

素材旧版本拒绝、最少两专家、表单读回等属于期望契约，不应因为当前实现可能宽松就改为成功。失败/退回路径在单独 runId 的项目上测试，保持主成功路径可独立复现。

## 11. 自动化实现建议与报告格式

使用现有 Python requests、RunLogger/Redactor 和初始化检查，新增独立 `verify_business_lifecycle.py`（待实现），不要把基础 verifier 的单 token 改成隐式共享。建议分层为 HttpClient、ActorSessions、FixtureRegistry、RuntimePoller、MaterialHelper、ExpertReviewHelper、ScenarioRunner。

runner 读取 [full-business-lifecycle.case.json](../verification/scenarios/full-business-lifecycle.case.json)，按当前节点匹配已知用例步骤，确认 expected node 再请求；不任意遍历所有 availableTransitions。固定成功路线配合运行时最新 ID/seq，比硬编码递增和纯随机推进更适合回归。

报告按 runId 保存：角色别名、脱敏用户名、项目/模块/批次/assignment ID、节点前后状态、HTTP 状态、耗时、traceId、材料版本/hash、异步等待结果、具体断言。不要将本设计 JSON 放进“运行结果”目录当作 passed 报告。

建议命令契约（**未来实现接口，并非当前可执行命令**）：

```powershell
python scripts/verify_business_lifecycle.py --gateway-base-url http://127.0.0.1:8080 --case verification/scenarios/full-business-lifecycle.case.json --fixture-pdf output/pdf/blank-upload.pdf --report-path verification/runs/<runId>.json
```

CI 分基础 smoke 与完整业务两个阶段，业务阶段失败立即非零退出并保留结果；确认功能基线后，再加入多项目并行和批量授权混合负载。当前网关默认每 IP 40请求/秒，应纳入测试节奏；不能把429误报成业务权限错误。

测试环境使用独立数据集/可恢复卷。当前没有完整公开删除用户和项目的清理链路，材料被引用后也禁止删除；通过 runId 隔离、保留失败现场、环境级重建来重复运行，不设计不存在的 DELETE 清理 API，更不对普通环境自动清库。

## 12. 主要证据与本次校验

- 前端：[auth/admin API](../../SicProjectIS_fronted/src/api/admin.ts)、[资格 API](../../SicProjectIS_fronted/src/api/expertQualification.ts)、[评分 API](../../SicProjectIS_fronted/src/api/expertReviews.ts)、[材料 API](../../SicProjectIS_fronted/src/api/materials.ts)、[WorkflowDetail 提交](../../SicProjectIS_fronted/src/views/WorkflowDetailView.vue)、[ExpertReviewPanel](../../SicProjectIS_fronted/src/components/workflow/ExpertReviewPanel.vue)。
- 后端：[资格审批(../services/iam-service/src/main/java/com/amatrix/sic/iam/service/ExpertQualificationService.java)、[WorkflowRuntimeService(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/runtime/WorkflowRuntimeService.java)、[ExpertReviewService(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/review/ExpertReviewService.java)、[自动评审推进(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/messaging/consumer/ExpertReviewCompletedConsumer.java)、[后继模块策略(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/policy/ModuleSuccessorPolicy.java)、[表单读写(../services/workflow-service/src/main/java/com/amatrix/sic/workflow/service/form/NodeFormService.java)。
- 模型：[申请 BPMN](../services/workflow-service/src/main/resources/workflows/bpmn_auxiliary_tags_augmented/项目申请_辅助标签版.bpmn)、[合同 BPMN](../services/workflow-service/src/main/resources/workflows/bpmn_auxiliary_tags_augmented/纵向项目合同_辅助标签版.bpmn)、[验收 BPMN](../services/workflow-service/src/main/resources/workflows/bpmn_auxiliary_tags_augmented/项目结题_辅助标签版.bpmn)。

本次静态校验包括：用例节点/候选角色/eventType/必填材料与当前 XML 一致，三个成功终态可达，3个批次和17种材料完整；用例 JSON 可解析且包含 BPMN SHA-256；PDF 一页 A4、非空、可解析、已渲染并确认空白。实际服务上的 HTTP、数据库和异步全链路尚未执行。
