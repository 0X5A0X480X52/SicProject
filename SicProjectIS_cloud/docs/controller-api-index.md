# Controller API 索引

此索引覆盖当前后端 24 个 Controller 的全部 114 个 HTTP API 方法。每个方法的 Javadoc 已说明用途、关键输入、权限、返回结构及业务限制。表格用于定位入口，完整约束以源文件中的注释和 Service 实现为准。

Gateway 使用路由和过滤器承担转发、鉴权等职责，没有 Controller API；以下为 IAM、Project、Workflow、Material、Notification 的实际 HTTP 入口。平台模块也没有 Controller。

通常业务接口需要用户 JWT，并由 Service 进一步检查权限；`/internal/**` 使用带 `SCOPE_internal` 的服务 JWT。例外是 IAM 服务 token 兑换使用引导 secret，登录、注册和 JWKS 为公开接口。内部入口供服务间调用，不能当作前端业务接口。

除 JWKS、文件下载和 SSE 外，下列接口返回 `ApiResponse`；表格和方法注释所述的业务结果位于 `data`。本轮只增加说明，没有修改请求路径、JSON 字段、权限规则或执行逻辑。

## 覆盖统计

| 服务 | Controller 数 | API 数 |
| --- | ---: | ---: |
| iam-service | 5 | 32 |
| material-service | 2 | 7 |
| notification-service | 1 | 1 |
| project-service | 6 | 24 |
| workflow-service | 10 | 50 |
| 合计 | 24 | 114 |

说明规范见 [统一注释规范](code-commenting-guide.md)，验证结果见 [本轮验收报告](../verification/runs/controller-api-commenting-verification.json)。

## iam-service

| 方法 | 路径 | 作用 | 源码入口 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 使用用户名和密码登录，取得用户 JWT 和前端会话信息。 | [AuthController.login](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/AuthController.java#L33) |
| POST | `/api/auth/register` | 注册普通业务账号，并直接返回登录结果。 | [AuthController.register](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/AuthController.java#L44) |
| GET | `/api/auth/me` | 读取当前登录账号的最新身份资料、部门、角色和权限。 | [AuthController.me](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/AuthController.java#L56) |
| POST | `/api/auth/logout` | 撤销本次登录 token，结束当前会话。 | [AuthController.logout](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/AuthController.java#L67) |
| POST | `/api/expert-qualification/applications` | 以当前账号提交专家资格申请，进入部门审核阶段。 | [ExpertQualificationController.submit](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/ExpertQualificationController.java#L29) |
| GET | `/api/expert-qualification/my` | 查询本人的专家资格和全部资格申请历史。 | [ExpertQualificationController.mine](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/ExpertQualificationController.java#L41) |
| GET | `/api/admin/expert-qualification/applications` | 列出当前审核人员可处理的专家资格申请。 | [ExpertQualificationController.query](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/ExpertQualificationController.java#L51) |
| POST | `/api/admin/expert-qualification/applications/{applicationId}/dept-review` | 对指定专家资格申请执行部门审核。 | [ExpertQualificationController.deptReview](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/ExpertQualificationController.java#L61) |
| POST | `/api/admin/expert-qualification/applications/{applicationId}/science-review` | 执行专家资格的科技处终审，通过后授予 EXPERT 角色。 | [ExpertQualificationController.scienceReview](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/ExpertQualificationController.java#L73) |
| GET | `/api/admin/users/roles` | 为用户角色管理页加载完整用户列表及可选角色。 | [IamAdminController.roleManagement](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L36) |
| GET | `/api/admin/users` | 按条件检索用户，并提供角色选择列表。 | [IamAdminController.users](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L46) |
| GET | `/api/admin/users/{userId}` | 读取指定用户的管理详情。 | [IamAdminController.user](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L58) |
| PUT | `/api/admin/users/{userId}/roles` | 替换指定用户的全局角色集合。 | [IamAdminController.roles](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L68) |
| PATCH | `/api/admin/users/{userId}/status` | 启用或停用指定用户账号。 | [IamAdminController.status](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L80) |
| GET | `/api/admin/roles/permissions` | 读取角色与权限的对应关系，供权限配置页展示。 | [IamAdminController.matrix](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L91) |
| GET | `/api/admin/permissions` | 列出系统已登记的权限代码和说明。 | [IamAdminController.permissions](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L101) |
| PUT | `/api/admin/roles/{roleCode}/permissions` | 替换一个角色的完整权限集合。 | [IamAdminController.permissions](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L111) |
| GET | `/api/admin/departments` | 查询当前人员可管理的部门目录。 | [IamAdminController.departments](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L123) |
| POST | `/api/admin/departments` | 创建部门，建立部门管理目录项。 | [IamAdminController.createDepartment](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L133) |
| PUT | `/api/admin/departments/{deptId}` | 修改指定部门的名称、代码、上级及启用状态。 | [IamAdminController.updateDepartment](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L144) |
| DELETE | `/api/admin/departments/{deptId}` | 删除不再被引用的部门目录项。 | [IamAdminController.deleteDepartment](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L155) |
| GET | `/api/admin/departments/members` | 加载部门成员管理页的数据及管理能力提示。 | [IamAdminController.members](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L165) |
| GET | `/api/admin/departments/candidates` | 检索可加入当前管理部门的用户候选。 | [IamAdminController.candidates](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L177) |
| PUT | `/api/admin/departments/{deptId}/members/{userId}` | 将用户加入指定部门，并按需替换部门可分配角色。 | [IamAdminController.member](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L188) |
| PATCH | `/api/admin/departments/members/{userId}/roles` | 调整已有部门成员的可编辑角色子集。 | [IamAdminController.memberRoles](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L200) |
| DELETE | `/api/admin/departments/members/{userId}` | 解除用户与当前部门的归属关系。 | [IamAdminController.removeMember](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/IamAdminController.java#L211) |
| GET | `/.well-known/jwks.json` | 发布 JWT 验签所需的 RSA 公钥集合。 | [JwkController.keys](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/JwkController.java#L24) |
| POST | `/internal/tokens/service` | 使用服务引导凭据兑换内部调用 JWT。 | [InternalIdentityController.serviceToken](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java#L50) |
| GET | `/internal/users/{userId}/summary` | 向其他服务提供单个用户的最小身份摘要。 | [InternalIdentityController.summary](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java#L68) |
| POST | `/internal/users/summaries` | 一次查询多个用户的身份摘要，减少逐用户远程请求。 | [InternalIdentityController.summaries](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java#L80) |
| GET | `/internal/admin/overview-stats` | 提供 IAM 用户、角色和权限的汇总统计。 | [InternalIdentityController.overviewStats](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java#L91) |
| GET | `/internal/departments/{deptId}/summary` | 为项目申请或归属修复查询部门名称和有效状态。 | [InternalIdentityController.department](../services/iam-service/src/main/java/com/amatrix/sic/iam/controller/internal/InternalIdentityController.java#L101) |

## material-service

| 方法 | 路径 | 作用 | 源码入口 |
| --- | --- | --- | --- |
| POST | `/api/projects/{projectId}/materials/{materialTypeCode}/versions` | 向项目的指定材料类型上传一个新文件版本。 | [MaterialController.upload](../services/material-service/src/main/java/com/amatrix/sic/material/controller/MaterialController.java#L34) |
| GET | `/api/projects/{projectId}/materials` | 查询项目材料及其版本上下文。 | [MaterialController.list](../services/material-service/src/main/java/com/amatrix/sic/material/controller/MaterialController.java#L42) |
| GET | `/api/material-versions/{id}/download` | 下载某个材料版本对应的原始文件。 | [MaterialController.download](../services/material-service/src/main/java/com/amatrix/sic/material/controller/MaterialController.java#L48) |
| DELETE | `/api/material-versions/{id}` | 删除可删除的材料版本，并登记对象存储清理任务。 | [MaterialController.delete](../services/material-service/src/main/java/com/amatrix/sic/material/controller/MaterialController.java#L55) |
| POST | `/internal/material-reservations` | 在流程正式迁移前，为本次提交预留所引用的材料版本。 | [InternalMaterialReservationController.reserve](../services/material-service/src/main/java/com/amatrix/sic/material/controller/internal/InternalMaterialReservationController.java#L23) |
| POST | `/internal/material-reservations/{id}/confirm` | 将指定材料预留确认为已提交引用。 | [InternalMaterialReservationController.confirm](../services/material-service/src/main/java/com/amatrix/sic/material/controller/internal/InternalMaterialReservationController.java#L31) |
| DELETE | `/internal/material-reservations/{id}` | 释放失败或取消的流程提交所持有的材料预留。 | [InternalMaterialReservationController.release](../services/material-service/src/main/java/com/amatrix/sic/material/controller/internal/InternalMaterialReservationController.java#L38) |

## notification-service

| 方法 | 路径 | 作用 | 源码入口 |
| --- | --- | --- | --- |
| GET | `/api/sse/subscribe` | 为当前账号建立业务通知的 SSE 长连接。 | [SseController.subscribe](../services/notification-service/src/main/java/com/amatrix/sic/notification/controller/SseController.java#L22) |

## project-service

| 方法 | 路径 | 作用 | 源码入口 |
| --- | --- | --- | --- |
| GET | `/api/admin/projects/authorizations` | 加载项目授权管理页的可管理项目及授权类型统计。 | [ProjectAdminController.authorizations](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAdminController.java#L27) |
| GET | `/api/admin/overview` | 汇总后台管理首页的账号、权限、项目授权和审计统计。 | [ProjectAuditController.overview](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuditController.java#L26) |
| GET | `/api/admin/audit-logs` | 按条件查询管理员操作与项目授权审计日志。 | [ProjectAuditController.logs](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuditController.java#L34) |
| GET | `/api/projects/{projectId}/authorization` | 读取项目授权详情及当前操作人的管理能力。 | [ProjectAuthorizationController.detail](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L34) |
| PUT | `/api/projects/{projectId}/leader` | 更换项目负责人并同步负责人/成员关系。 | [ProjectAuthorizationController.leader](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L44) |
| PUT | `/api/projects/{projectId}/members` | 新增或更新一个项目成员及其承担职责。 | [ProjectAuthorizationController.member](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L56) |
| PUT | `/api/projects/{projectId}/members/batch` | 在一个本地事务中批量新增或更新项目成员。 | [ProjectAuthorizationController.members](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L68) |
| DELETE | `/api/projects/{projectId}/members/{userId}` | 移除项目成员关系，收回该关系带来的项目访问资格。 | [ProjectAuthorizationController.removeMember](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L81) |
| POST | `/api/projects/{projectId}/expert-grants` | 为指定专家配置项目范围的临时评审授权。 | [ProjectAuthorizationController.expert](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L92) |
| POST | `/api/projects/{projectId}/expert-grants/batch` | 批量配置同一范围的专家项目授权。 | [ProjectAuthorizationController.experts](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L104) |
| POST | `/api/projects/{projectId}/finance-grants` | 为财务人员配置项目经办授权，使其能够办理匹配的财务节点。 | [ProjectAuthorizationController.finance](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L116) |
| POST | `/api/projects/{projectId}/finance-grants/batch` | 在一个事务内批量配置财务经办授权。 | [ProjectAuthorizationController.finances](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L128) |
| POST | `/api/projects/{projectId}/proxy-grants` | 授权其他用户在指定项目范围代录业务。 | [ProjectAuthorizationController.proxy](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L139) |
| POST | `/api/projects/{projectId}/proxy-grants/batch` | 批量配置统一范围的项目代录授权。 | [ProjectAuthorizationController.proxies](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L150) |
| POST | `/api/projects/{projectId}/grants/{grantId}/revoke` | 撤销项目中的一条专家、财务或代录授权。 | [ProjectAuthorizationController.revoke](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L161) |
| POST | `/api/projects/{projectId}/grants/revoke-batch` | 原子撤销同一项目内的一组授权记录。 | [ProjectAuthorizationController.revokeBatch](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectAuthorizationController.java#L172) |
| GET | `/api/projects` | 列出当前账号有权读取的项目主数据。 | [ProjectController.projects](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectController.java#L37) |
| POST | `/api/project-applications/start` | 创建项目并启动申请模块，返回申请业务入口。 | [ProjectController.start](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectController.java#L48) |
| PUT | `/api/projects/{projectId}/department` | 为历史上缺失所属部门的项目补充归属。 | [ProjectDepartmentController.assign](../services/project-service/src/main/java/com/amatrix/sic/project/controller/ProjectDepartmentController.java#L20) |
| GET | `/internal/projects/{projectId}/finance-readiness` | 检查目标财务节点是否已有匹配范围的有效财务经办人。 | [InternalProjectController.financeReadiness](../services/project-service/src/main/java/com/amatrix/sic/project/controller/internal/InternalProjectController.java#L41) |
| GET | `/internal/projects/{projectId}/access` | 对一个用户和项目进行内部访问判定。 | [InternalProjectController.access](../services/project-service/src/main/java/com/amatrix/sic/project/controller/internal/InternalProjectController.java#L52) |
| POST | `/internal/projects/access-decisions` | 批量判定同一操作人对多组项目操作的权限。 | [InternalProjectController.accessDecisions](../services/project-service/src/main/java/com/amatrix/sic/project/controller/internal/InternalProjectController.java#L65) |
| POST | `/internal/projects/{projectId}/expert-grants/sync` | 同步 Workflow 专家指派对应的临时项目授权。 | [InternalProjectController.syncExpertGrant](../services/project-service/src/main/java/com/amatrix/sic/project/controller/internal/InternalProjectController.java#L77) |
| POST | `/internal/projects/{projectId}/expert-grants/revoke` | 按 Workflow 指派范围回收专家临时项目授权。 | [InternalProjectController.revokeExpertGrant](../services/project-service/src/main/java/com/amatrix/sic/project/controller/internal/InternalProjectController.java#L89) |

## workflow-service

| 方法 | 路径 | 作用 | 源码入口 |
| --- | --- | --- | --- |
| GET | `/api/expert-reviews/my-assignments` | 分页查询本人的待评审任务和已提交、已完成等历史指派。 | [ExpertPersonalReviewController.list](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertPersonalReviewController.java#L20) |
| GET | `/api/expert-reviews/my-assignments/{assignmentId}` | 读取本人某次专家指派的详情和本人评分明细。 | [ExpertPersonalReviewController.detail](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertPersonalReviewController.java#L31) |
| POST | `/api/expert-reviews/batches` | 在当前专家指派节点建立本轮评审批次。 | [ExpertReviewController.create](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java#L33) |
| GET | `/api/expert-reviews/batches/{batchId}` | 读取评审批次、指派进度及评分汇总。 | [ExpertReviewController.detail](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java#L43) |
| POST | `/api/expert-reviews/batches/{batchId}/assignments` | 向当前批次指派一名专家，并同步其项目临时授权。 | [ExpertReviewController.assign](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java#L52) |
| POST | `/api/expert-reviews/assignments/{assignmentId}/scores` | 提交当前登录专家对本人 assignment 的分项评分及评审意见。 | [ExpertReviewController.submit](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java#L63) |
| DELETE | `/api/expert-reviews/assignments/{assignmentId}` | 移除评审批次中的一个专家指派，并回收对应临时授权。 | [ExpertReviewController.remove](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java#L75) |
| POST | `/api/expert-reviews/assignments/{assignmentId}/remove` | 为兼容调用方提供 POST 形式的专家指派移除入口。 | [ExpertReviewController.removeFallback](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ExpertReviewController.java#L85) |
| GET | `/api/module-instances/{moduleInstanceId}/business-data` | 读取一个工作流模块聚合的业务草稿与评审数据。 | [ModuleBusinessDataController.data](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/ModuleBusinessDataController.java#L19) |
| GET | `/api/node-forms/definitions` | 列出节点表单注册定义，供前端选择表单组件和写入方式。 | [NodeFormController.definitions](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java#L31) |
| GET | `/api/node-forms/{formCode}` | 按表单代码与项目/模块上下文读取节点表单数据。 | [NodeFormController.get](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java#L37) |
| PUT | `/api/node-forms/{formCode}` | 保存当前节点的单例表单草稿及对应业务草稿。 | [NodeFormController.save](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java#L44) |
| POST | `/api/node-forms/{formCode}/records` | 为当前节点的历史记录型表单新增一条记录。 | [NodeFormController.create](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java#L51) |
| PUT | `/api/node-forms/{formCode}/records/{recordId}` | 修改当前节点、本轮次的一条历史表单记录。 | [NodeFormController.update](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java#L58) |
| DELETE | `/api/node-forms/{formCode}/records/{recordId}` | 删除当前节点、本轮次中匹配的历史表单记录。 | [NodeFormController.delete](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NodeFormController.java#L65) |
| GET | `/api/notices` | 查询当前账号可见的业务公告。 | [NoticeController.list](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NoticeController.java#L35) |
| GET | `/api/notices/{noticeId}` | 读取一个当前账号可见的公告详情。 | [NoticeController.get](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NoticeController.java#L46) |
| POST | `/api/notices` | 创建申请、合同或验收链路使用的业务公告。 | [NoticeController.create](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NoticeController.java#L56) |
| PUT | `/api/notices/{noticeId}` | 更新已有公告的业务内容、时间与可见范围。 | [NoticeController.update](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/NoticeController.java#L67) |
| GET | `/api/projects/{projectId}/applications` | 读取项目申请正文及扩展信息。 | [StructuredBusinessController.application](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L34) |
| PUT | `/api/projects/{projectId}/applications` | 更新当前申请填报节点的申请正文。 | [StructuredBusinessController.saveApplication](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L44) |
| GET | `/api/projects/{projectId}/contracts` | 读取项目合同业务草稿。 | [StructuredBusinessController.contract](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L56) |
| PUT | `/api/projects/{projectId}/contracts` | 保存当前合同填报节点的合同草稿。 | [StructuredBusinessController.saveContract](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L66) |
| GET | `/api/projects/{projectId}/acceptances` | 读取项目验收申请业务草稿。 | [StructuredBusinessController.acceptance](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L77) |
| PUT | `/api/projects/{projectId}/acceptances` | 保存当前验收材料填报节点的验收业务草稿。 | [StructuredBusinessController.saveAcceptance](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L87) |
| GET | `/api/projects/{projectId}/achievements` | 列出项目登记的科研成果记录。 | [StructuredBusinessController.achievements](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L98) |
| POST | `/api/projects/{projectId}/achievements` | 在验收材料填报阶段新增一项项目成果。 | [StructuredBusinessController.addAchievement](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L108) |
| GET | `/api/projects/{projectId}/acceptance-financial-settlements` | 查询项目验收财务结算记录。 | [StructuredBusinessController.settlements](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L120) |
| POST | `/api/projects/{projectId}/acceptance-financial-settlements` | 在当前验收财务节点新增结算记录。 | [StructuredBusinessController.addSettlement](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/StructuredBusinessController.java#L130) |
| POST | `/api/workflow-definitions/upload` | 上传 BPMN XML，建立新的工作流定义草稿及解析配置。 | [WorkflowDefinitionController.upload](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L33) |
| GET | `/api/workflow-definitions/assets` | 列出服务内置的 BPMN 资产及其模块和版本信息。 | [WorkflowDefinitionController.assets](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L43) |
| POST | `/api/workflow-definitions/assets/{assetName}/upload` | 把指定内置 BPMN 资产导入为定义草稿。 | [WorkflowDefinitionController.uploadAsset](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L53) |
| POST | `/api/workflow-definitions/assets/{assetName}/publish` | 导入并发布指定内置资产对应的定义版本。 | [WorkflowDefinitionController.publishAsset](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L63) |
| POST | `/api/workflow-definitions/{id}/validate` | 检查已保存定义的 BPMN 配置是否满足运行约束。 | [WorkflowDefinitionController.validate](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L74) |
| POST | `/api/workflow-definitions/{id}/publish` | 发布已保存的工作流定义，作为对应模块的有效定义。 | [WorkflowDefinitionController.publish](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L84) |
| GET | `/api/workflow-definitions` | 查询数据库中的全部工作流定义摘要。 | [WorkflowDefinitionController.list](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L95) |
| GET | `/api/workflow-definitions/latest` | 按模块类型读取最新可用的已发布工作流定义。 | [WorkflowDefinitionController.latest](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L105) |
| GET | `/api/workflow-definitions/{id}` | 读取流程定义的管理详情及解析后的配置。 | [WorkflowDefinitionController.detail](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L115) |
| GET | `/api/workflow-definitions/{id}/bpmn` | 读取某个定义的原始 BPMN XML，供流程图渲染。 | [WorkflowDefinitionController.bpmn](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L125) |
| GET | `/api/workflow-definitions/{id}/nodes` | 读取流程定义的节点配置，供前端标识责任人、表单与材料要求。 | [WorkflowDefinitionController.nodes](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L136) |
| GET | `/api/workflow-definitions/{id}/transitions` | 读取流程定义的连线及迁移规则。 | [WorkflowDefinitionController.transitions](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowDefinitionController.java#L146) |
| POST | `/api/projects/{projectId}/module-instances` | 为已有项目启动指定业务模块实例。 | [WorkflowRuntimeController.start](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowRuntimeController.java#L32) |
| POST | `/api/module-instances/{moduleInstanceId}/transitions` | 提交当前节点业务动作，保存表单和材料引用并推进状态机。 | [WorkflowRuntimeController.transition](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowRuntimeController.java#L43) |
| POST | `/api/legacy/module-instances/{moduleInstanceId}/transitions` | 保留旧调用路径的节点迁移兼容入口。 | [WorkflowRuntimeController.legacyTransition](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowRuntimeController.java#L55) |
| GET | `/api/module-instances/{moduleInstanceId}/runtime-view` | 读取模块的当前运行视图及操作上下文。 | [WorkflowRuntimeController.view](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowRuntimeController.java#L64) |
| GET | `/api/workflow-workbench/items` | 读取旧版工作台使用的当前可办理模块条目集合。 | [WorkflowRuntimeController.workbench](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowRuntimeController.java#L74) |
| GET | `/api/workflow-workbench/query` | 分页查询工作台的待办、经办、可见或已结束模块。 | [WorkflowWorkbenchController.query](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/WorkflowWorkbenchController.java#L19) |
| POST | `/internal/application-sagas` | 为 Project 申请 Saga 幂等创建 Workflow 侧的申请业务和模块。 | [InternalApplicationSagaController.create](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/internal/InternalApplicationSagaController.java#L30) |
| GET | `/internal/application-sagas/{sagaId}` | 查询申请 Saga 在 Workflow 侧的创建结果。 | [InternalApplicationSagaController.status](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/internal/InternalApplicationSagaController.java#L36) |
| DELETE | `/internal/application-sagas/{sagaId}` | 补偿指定申请 Saga 在 Workflow 侧创建的业务数据。 | [InternalApplicationSagaController.compensate](../services/workflow-service/src/main/java/com/amatrix/sic/workflow/controller/internal/InternalApplicationSagaController.java#L42) |

## 关键调用关系

- 项目创建及申请启动使用 `POST /api/project-applications/start`；模块启动接口只允许启动合同或验收，不能直接启动申请。
- 表单及结构化正文的保存不推进流程；正式提交使用模块 `transitions`，提交前读取最新 `runtime-view` 并传入 `expectedSeq`。
- 专家项目授权不等于正式评审指派；创建批次、指派 assignment、本人提交评分、批次完成分别使用各自业务动作。本人历史使用 `my-assignments`。
- 财务人员须同时具备 `FINANCE_ADMIN` 和匹配范围的项目经办授权；工作台与运行视图按当前资格重新查询，SSE 仅用于提示刷新。
- 新版工作台 `query` 支持 TODO、OPERATED、VISIBLE、FINISHED；旧版 `items` 仅返回当前可办理条目，FINISHED 是模块结束状态。
- 材料下载使用具体版本 ID 并再次鉴权；上传成功、预留成功、迁移提交、材料确认是不同阶段。删除成功仅表示已登记对象清理任务。

