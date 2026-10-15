# 前后端核心代码注释规范

本规范适用于 `SicProjectIS_cloud` 和 `SicProjectIS_fronted` 的业务核心代码，面向接手项目的新成员。注释帮助解释职责、业务步骤和约束；接口、状态机、事务及 SQL 的事实仍以实际实现为准。

## 1. 本轮覆盖与阅读顺序

本轮核对了 242 个主 Java 文件、62 个 TypeScript/Vue 文件和 29 个 Mapper XML。为 **187 个 Java 文件、56 个前端文件及全部 29 个 Mapper XML** 补充或修正核心说明；其余 55 个 Java 文件和 6 个前端文件按简单载体、启动入口、第三方适配或范围外工具豁免。

具体文件及豁免理由见 [覆盖清单](code-commenting-coverage.md)。生成代码、依赖、测试源码、历史迁移及生成 SQL 不在本轮注释范围。

Controller 接口说明已进一步覆盖 24 个 Controller 的全部 114 个 API 方法，按服务、路径和用途查阅 [Controller API 索引](controller-api-index.md)。详细请求约束与权限说明位于各方法的 Javadoc，包含内部服务、兼容入口、下载和 SSE。

后端主动请求已覆盖 8 个 OpenFeign 客户端的全部 20 个方法，并补充主要入站 DTO、跨服务 Command/Target、服务 token 缓存和 Trace Header 转发说明，见 [后端请求与 OpenFeign 契约](backend-request-contracts.md)。

建议沿以下链路阅读，每条链路先看类或模块说明，再看用例方法和关键分支：

| 链路 | 主要入口与说明重点 |
| --- | --- |
| 身份与鉴权 | Gateway 会话检查 → IAM 登录及 SessionVersionService → ProjectAccessPolicy；JWT 版本、pending 屏障与项目范围分别处理 |
| 项目申请 | ProjectApplicationSagaService → ApplicationSagaWorkflowService；本地短事务、远程创建、确认和补偿的实际范围 |
| 普通流转 | WorkflowRuntimeService → 节点表单与业务草稿 → MaterialReservationService；序号复核、行锁、独立租约和确认事件 |
| 专家评审 | ExpertReviewService → 完成事件 → WorkflowRuntimeService；模块→批次→assignment 锁顺序、个人提交与批次完成 |
| 授权与归属 | ProjectAuthorizationService、ProjectDepartmentService、财务配置查询及身份/项目快照消费者 |
| 消息与刷新 | Outbox/Inbox → Notification → useWorkflowEvents；允许重复投递，断线后重查，推送不代替鉴权 |
| 前端表单与工作台 | api/client、auth store → 工作台与详情页 → 共享字段/请求转换；身份展示、请求取消、草稿保留及运行时契约 |

## 2. 统一写法

使用中文业务说明，保留 `expectedSeq`、`authVersion`、BPMN、Outbox 等技术标识的原拼写。解释“负责什么、为什么这样处理、有哪些约束”，避免逐行翻译语法。

| 位置 | 格式 | 应说明什么 |
| --- | --- | --- |
| Java 核心类、接口 | 声明及注解之前的 Javadoc | 本类职责、所属链路及必要协作边界 |
| Java 核心业务方法 | 方法注解之前的 Javadoc | 前置条件、结果、副作用，以及重要事务、远程调用或恢复约束 |
| TypeScript 模块 | 文件开头的 JSDoc | 请求契约或模块职责，避免把界面判断描述成服务端授权 |
| TypeScript 函数 | 声明前的 JSDoc | 输入含义、转换规则、状态来源或生命周期 |
| Vue 页面、组件 | `<script setup>` 内的模块说明及逻辑注释 | 页面职责、数据来源、请求和组件事件的协作方式 |
| 重要字段、record 参数 | 字段前 JSDoc 或 Javadoc `@param` | ID 的种类、单位、版本、轮次、空值和读写语义 |
| 复杂分支 | 邻近的 `//` | 设计原因、竞争窗口或兼容规则 |
| Mapper XML | SQL 元素外的 `<!-- ... -->` | 查询用途、锁查询条件、幂等或条件更新；不向 SQL 本体插入注释 |

类说明一般用一至三句。核心方法有必要时分行展开；能够从名称或类型直接理解的参数不机械添加 `@param`、`@return` 或 `@throws`。构造器、getter/setter、常规 CRUD 不逐项重复类职责。

### Controller API 方法

每个映射方法均在第一条方法注解之前写 Javadoc，即使方法本身只是委托 Service。说明完整 HTTP 方法和路径、业务用途、关键输入的来源和含义、权限条件、返回结构，以及会引起的业务变更或重要限制。使用 `{@code ...}` 保留路由原拼写，使用 `<p>` 分隔说明段落。

权限必须核对 Service 方法安全注解及业务策略，不以 `/admin` 路径或页面按钮推测。需区分角色、权限代码、项目授权、当前节点资格，以及服务 token 的 `SCOPE_internal`。全局管理员在项目授权上下文指 `SYSTEM_ADMIN` 或 `SCIENCE_ADMIN`；各接口仍以实际权限检查为准。

不机械罗列所有状态码，只描述实现确实覆盖的重要分支。兼容接口明确关联主入口；`ApiResponse` 的 `data` 内容、二进制下载与 SSE 流分别说明。单纯保存、个人评分提交、评审批次完成、模块结束和项目终态不得混为一谈。

```java
/**
 * {@code GET /api/expert-reviews/my-assignments/{assignmentId}}：读取本人评审详情和评分。
 * <p>assignmentId 是个人指派 ID；不存在或属于其他账号时返回 404。
 * <p>返回本人评分和 canReview；历史可读不表示仍有项目材料访问权限。
 */
```

### OpenFeign 与请求契约

Feign 方法注释应写明调用方与目标服务、完整 HTTP 方法和内部路径、服务身份与业务身份的来源、请求范围、响应关联方式及失败语义。`Authorization` 是服务 token 时，不把它描述为当前用户身份；业务用户通过 `ActorContext`、路径参数或命令体传递。

请求 record 使用 `@param` 解释字段的 ID 种类、空值处理、幂等键、范围和并发版本。同步 HTTP Command 与异步 Outbox 事件要明确区分。敏感 secret/token 只说明传输位置，不记录示例值。

```java
/**
 * {@code POST /internal/projects/access-decisions}：批量判定项目操作权限。
 * <p>Authorization 是服务身份；request.actor 是被判定的业务用户。
 * <p>响应必须按 decisionId 与 projectId 关联，依赖失败不能视为允许访问。
 */
```

### 示例：事务与租约

```java
/**
 * 先读取状态并校验 expectedSeq，在事务外预留材料；
 * 随后锁内复核状态、权限并提交迁移。
 * 每次尝试使用独立租约，失败只释放本次预留。
 */
```

```java
// 也处理响应丢失的占屏障命令；仅本事务的 owner 能释放自己占有的屏障。
```

### 示例：前端请求与生命周期

```ts
/**
 * 按统一响应结构请求 Gateway。
 * 收到 401 时清理会话并跳转登录；其他失败交给调用方处理。
 */
```

```ts
// token 切换后旧连接仍可能回调；只让当前连接关闭自己，避免误关新会话连接。
```

### 示例：SQL 的前置条件

```xml
<!-- 先锁定活动项目，再修改成员或 grant；专家回收消费也遵守项目→grant 的顺序。 -->
```

不添加作者、修改日期、代码搬迁历史或空泛 TODO。保留许可证、`@SuppressWarnings`、`@ts-ignore`、纯函数标记等工具识别信息；业务注释不替代这些指令，也不自行增加编译忽略。

## 3. 必须准确区分的概念

| 概念 | 当前实现应如何解释 |
| --- | --- |
| JWT 与当前身份 | 签名有效不等于会话仍有效；Gateway 检查撤销、当前权限版本和 pending 屏障 |
| pending 与版本发布 | 提交前占屏障，提交后单调发布版本；发布失败或提交结果不确定时保持拒绝访问，恢复读取数据库最终版本 |
| 角色与项目 grant | 财务、专家的任务办理还需要匹配项目、模块、轮次和节点范围；一般读取与当前任务操作分别判断 |
| 身份快照与 IAM 当前数据 | ProjectUserDirectory 的 find/requireAll 读取本地快照；缺失补齐、refresh、fetchAll 才访问 IAM。候选缓存被绕过不等于读取 IAM 实时身份 |
| 项目部门与账号部门 | 新申请读取 IAM 当前启用部门；项目归属不会随负责人调部门自动转移。历史空部门补充与账号部门修改分开 |
| Saga 幂等与自动恢复 | 接口支持重复调用不代表所有失败路径都被定时任务扫描；当前申请确认恢复只处理 WORKFLOW_CREATED |
| 事务与远程调用 | 材料预留在迁移事务外，部分锁内权限复核仍会调用 Project；生成文档的 MinIO 调用仍发生在数据库事务内，但不参加该事务提交 |
| seq、roundNo 与记录 ID | seq 是模块状态序号，roundNo 是业务轮次，stateRecordId 是历史主键；节点代码与节点配置主键也分别处理 |
| 预留与确认 | 独立预留租约防止失败竞争释放他人的材料；Workflow 落库后通过 Outbox 请求确认。EXPIRED 且对象仍可用可接受迟到确认，RELEASED 不会复活 |
| 个人评分与批次结束 | SUBMITTED 是个人已提交；全部已指派专家提交后批次完成，最终推进与临时权限回收经过后续事件和消费 |
| 专家历史与项目读取 | 本人历史只按账号及 assignment 查询，不能借历史接口恢复材料读取或暴露他人评分 |
| 界面配置与后端规则 | 前端默认人数、按钮或必填提示不自动形成后端硬性约束。评分总分当前是分项值乘权重后求和，不按总权重归一化 |
| 模块结束与项目终态 | finished 描述该模块，lifecycleStage 描述项目；历史存在不恢复已撤销的项目读取权限 |
| Outbox 入库与消息送达 | 事件随本地业务提交后再发送，允许重复投递；消费去重与消费写入同事务，推送成功也不等于用户已查看 |

重要说明依据代码逐项核对。本轮修正了申请补偿“可重复”与“已有恢复任务”的混淆，以及工作流启动资产说明；当前默认校验 SQL 已安装定义，仅显式启用 `bootstrap-if-missing` 时补齐缺失资产，重启保留已配置或在用定义。

这些说明不是功能修复、即时一致或无死锁的承诺。本轮保持已有逻辑，发现的失败窗口和实际远程调用位置用注释说明，未新增重试、降级或数据修复。

## 4. 后续修改时的审查要求

1. 先读取调用链、事务注解、Mapper 条件和事件消费路径，再描述业务保证；不要仅凭方法名推断。
2. 契约含义优先写在接口或类型声明处，设计原因写在实现处，避免多处复制同一长段说明。
3. 调整锁顺序、版本发布、幂等键、恢复扫描条件或授权范围时，同步修改相邻注释。
4. 保持格式、编码和工具指令；添加文档注释时确认它关联正确声明，不放在方法注解之后。
5. 发现实现与旧说明不同，修正事实说明并单独记录；功能改造在另外的变更中验收。

本轮覆盖清单给出了核心文件和豁免文件。新增核心用例、消费者或页面需要补充职责说明；新增简单载体只有产生业务语义时才补字段注释，不追求注释行数比例。

## 5. 验证方式与证据

本轮修改前保存全部 333 个源码及 Mapper 的基线，用于核对：

- **Java**：242 个文件忽略注释和代码间空白后的 token 一致，包含字符串与 Lua 文本块原文。
- **Mapper XML**：29 个文件忽略 XML 注释节点后，元素、绑定属性与非空 SQL 文本一致。
- **前端**：62 个文件使用 TypeScript 解析及去注释输出核对脚本；Vue 模板、样式和自定义块一致，保留编译工具指令。比对统一换行格式，避免 Windows CRLF 造成误报。

语义比对脚本和原始基线位于本机 `target/comment-annotation/`，不纳入 Git。核心注释检查记录在 [核心注释验收报告](../verification/runs/code-commenting-verification.json)，Controller API 检查记录在 [Controller 验收报告](../verification/runs/controller-api-commenting-verification.json)，OpenFeign 与请求契约检查记录在 [后端请求验收报告](../verification/runs/backend-request-commenting-verification.json)。报告不包含源码原文、密码或 token。

维护者可执行现有测试与构建：

```powershell
# SicProjectIS_cloud
mvn -B -pl '!platform/integration-tests' test
python scripts/assert_verification_gate.py --gate smoke
mvn -B -DskipTests package
git diff --check
```

```powershell
# SicProjectIS_fronted
npm test
npm run build
```

本轮验收不包含真实 MySQL 全量测试、容量故障矩阵或运行环境重新部署；代码及 SQL 执行内容保持一致后，使用普通测试、编译和前端构建验证此次注释变更。以后出现逻辑或 SQL 修改时，需要按相应变更重新选择集成与业务验收。
