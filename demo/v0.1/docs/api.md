# v0.1 前后端 API 接口文档

本文档整理 `demo/v0.1` 当前 FastAPI 后端与纯 HTML 前端之间实际使用的接口。当前版本使用内存存储，用于验证“BPMN 解析发布 + 自研状态机运行时 + ViewModel 驱动前端 + RBAC + SSE”的设计可行性。

## 基础信息

- 服务入口：`http://127.0.0.1:8010`
- 后端文件：`demo/v0.1/app.py`
- 前端首页：`demo/v0.1/frontend/index.html`
- 前端详情页：`demo/v0.1/frontend/detail.html`
- BPMN 来源：`docs/bpmn_auxiliary_tags_augmented/*.bpmn`
- 数据存储：内存对象，重启后恢复种子数据

## RBAC 鉴权

所有 `/api/**` 接口都会经过 `rbac_identity_middleware`。请求头：

```http
X-Demo-User: admin
```

可用 Demo 用户：

| userId | 说明 | 角色 |
|---|---|---|
| `leader` | 项目负责人 | `PROJECT_LEADER` |
| `dept_admin` | 二级单位管理员 | `DEPT_ADMIN` |
| `science_admin` | 科技处管理员 | `SCIENCE_ADMIN` |
| `expert` | 专家 | `EXPERT` |
| `admin` | 调试用户 | 全角色 |

如果不传 `X-Demo-User`，默认按 `admin` 处理。未知用户返回：

```json
{ "detail": "unknown demo user" }
```

状态迁移接口会在后端再次校验当前用户是否匹配当前节点的 `candidateRole`。前端只负责展示权限结果，安全边界在后端。

## 页面路由

### `GET /`

返回首页 HTML。

前端文件：`frontend/index.html`

### `GET /detail.html`

返回详情页 HTML。

详情页通过查询参数读取模块实例：

```text
/detail.html?moduleInstanceId=5001
```

前端文件：`frontend/detail.html`

## 元数据接口

### `GET /api/meta`

返回可用模块类型和 Demo 用户列表。

前端用途：

- 详情页角色选择器 `<select class="approval-mode">`
- 调试时确认可启动模块

响应示例：

```json
{
  "moduleTypes": ["ACCEPTANCE", "APPLICATION", "CONTRACT"],
  "users": [
    {
      "userId": "leader",
      "realName": "张老师",
      "dept": "计算机与信息学院",
      "roles": ["PROJECT_LEADER"]
    }
  ]
}
```

## 项目接口

### `POST /api/projects`

创建项目。

请求体：

```json
{
  "projectName": "示例科研项目A",
  "owner": "张老师",
  "projectCategory": "校级限项项目",
  "isLimitedProject": true,
  "isSchoolLevelAcceptance": true
}
```

字段说明：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `projectName` | string | 必填 | 项目名称 |
| `owner` | string | 必填 | 展示用负责人姓名 |
| `projectCategory` | string | `校级限项项目` | 项目类型展示字段 |
| `isLimitedProject` | boolean | `true` | 申报流程“是否限项项目”网关条件 |
| `isSchoolLevelAcceptance` | boolean | `true` | 结题流程“是否校级项目结题验收”网关条件 |

响应示例：

```json
{
  "projectId": 1001,
  "projectName": "示例科研项目A",
  "owner": "张老师"
}
```

### `GET /api/projects`

查询项目列表。

响应示例：

```json
[
  {
    "projectId": 1001,
    "projectName": "示例科研项目A",
    "owner": "张老师",
    "projectCategory": "校级限项项目",
    "isLimitedProject": true,
    "isSchoolLevelAcceptance": true,
    "modules": ["APPLICATION", "CONTRACT", "ACCEPTANCE"]
  }
]
```

## 模块实例接口

### `POST /api/projects/{project_id}/modules`

启动某个项目的模块流程实例。

请求体：

```json
{
  "moduleType": "APPLICATION"
}
```

可用 `moduleType`：

- `APPLICATION`
- `CONTRACT`
- `ACCEPTANCE`

响应：返回该模块的完整 ViewModel。

### `GET /api/projects/{project_id}/modules/{module_type}/view-model`

按 `projectId + moduleType` 获取模块 ViewModel。

请求头示例：

```http
X-Demo-User: science_admin
```

响应：同 ViewModel 结构。

### `GET /api/module-instances/{module_instance_id}/view-model`

按模块实例 ID 获取模块 ViewModel。

这是详情页主要使用的接口。切换模拟用户时，前端会带不同的 `X-Demo-User` 重新拉取该接口。

响应核心结构：

```json
{
  "moduleInstanceId": 5001,
  "projectId": 1001,
  "moduleType": "APPLICATION",
  "processKey": "APPLICATION_PROCESS",
  "workflowDefinition": {},
  "current": {
    "nodeId": "DeptReviewTask",
    "stateCode": "APPLICATION_DEPT_REVIEWING",
    "nodeName": "二级单位形式审核",
    "nodeType": "USER_TASK",
    "candidateRole": "DEPT_ADMIN",
    "operationMode": "SELF_OPERATE"
  },
  "permission": {
    "canOperate": true,
    "deniedReason": "",
    "user": {}
  },
  "availableActions": [],
  "requiredMaterials": [],
  "documentConfigs": [],
  "tasks": [],
  "history": [],
  "remarks": [],
  "attachments": [],
  "recordMaterials": [],
  "documents": [],
  "businessData": {},
  "projectInfo": {
    "projectCategory": "校级限项项目",
    "isLimitedProject": true,
    "isSchoolLevelAcceptance": true
  },
  "workflowNodes": [],
  "workflowTransitions": []
}
```

重要字段说明：

| 字段 | 说明 |
|---|---|
| `current` | 当前运行节点与状态 |
| `permission.canOperate` | 当前 `X-Demo-User` 是否可办理 |
| `availableActions` | 当前可执行事件按钮，前端按钮由此生成 |
| `requiredMaterials` | 当前节点材料要求 |
| `history` | `module_state_record` 语义的状态迁移历史 |
| `remarks` | `state_record_remark` 语义的操作意见 |
| `attachments` | 材料版本列表 |
| `recordMaterials` | 状态记录与材料版本关联 |
| `documents` | `process_document` 语义的单据快照 |
| `workflowNodes` | BPMN 节点元数据，用于时间线生成 |
| `workflowTransitions` | BPMN sequenceFlow 元数据，用于精确高亮真实路径 |

`availableActions` 示例：

```json
[
  {
    "eventType": "DEPT_REVIEW_FINISHED",
    "branchEventType": "DEPT_APPROVE",
    "label": "通过/提交",
    "toState": null,
    "toNode": "LimitProjectGateway",
    "payloadPreset": {
      "deptApproved": true
    }
  },
  {
    "eventType": "DEPT_REVIEW_FINISHED",
    "branchEventType": "DEPT_REJECT",
    "label": "退回",
    "toState": "APPLICATION_DRAFT",
    "toNode": "SubmitApplicationTask",
    "payloadPreset": {
      "deptApproved": false
    }
  }
]
```

说明：

- 用户任务后接 Gateway 时，后端会把 Gateway 分支折叠为前端按钮。
- 前端提交时仍执行当前用户任务的 `eventType`，并合入 `payloadPreset`。
- `isLimitedProject` 和 `isSchoolLevelAcceptance` 由项目业务信息决定，不由前端按钮决定。

### `GET /api/module-instances/{module_instance_id}/bpmn`

返回模块实例对应的 BPMN XML。

详情页用于加载真实 BPMN 图，而不是使用静态内置 XML。

响应示例：

```json
{
  "bpmnXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>..."
}
```

## 首页接口

### `GET /api/dashboard/items`

返回首页业务事项列表。

前端文件：`frontend/index.html`

响应示例：

```json
[
  {
    "id": "APPLICATION-5001",
    "moduleInstanceId": 5001,
    "projectId": 1001,
    "module": "apply",
    "name": "示例科研项目A",
    "desc": "APPLICATION · APPLICATION_PROCESS",
    "owner": "张老师",
    "dept": "默认单位",
    "projectCategory": "校级限项项目",
    "year": "2026",
    "status": "running",
    "node": "发布申报通知",
    "material": 80,
    "materialText": "Demo数据",
    "round": 1,
    "returns": 0,
    "update": "2026-06-01 20:10:00",
    "mine": true,
    "todo": true,
    "doneByMe": false,
    "overdue": false
  }
]
```

`module` 是前端首页 tab 使用的展示分类：

| 后端 moduleType | 首页 module |
|---|---|
| `APPLICATION` | `apply` |
| `CONTRACT` | `contract` |
| `ACCEPTANCE` | `acceptance` |

## 状态迁移接口

### `POST /api/projects/{project_id}/modules/{module_type}/actions`

按 `projectId + moduleType` 执行状态迁移。

请求头：

```http
X-Demo-User: dept_admin
```

请求体：

```json
{
  "eventType": "DEPT_REVIEW_FINISHED",
  "operator": "DEPT_ADMIN",
  "remark": "同意推荐",
  "payload": {
    "deptApproved": true,
    "materials": {
      "DEPT_REVIEW_OPINION": ["二级单位审核意见.pdf"]
    }
  }
}
```

响应：执行迁移后的最新 ViewModel。

### `POST /api/module-instances/{module_instance_id}/actions`

按模块实例 ID 执行状态迁移。

这是详情页“提交审批”按钮使用的接口。

请求头：

```http
X-Demo-User: science_admin
```

请求体：

```json
{
  "eventType": "SCIENCE_INITIAL_REVIEW_FINISHED",
  "operator": "SCIENCE_ADMIN",
  "remark": "初审通过",
  "payload": {
    "scienceInitialApproved": true,
    "materials": {
      "SCIENCE_REVIEW_OPINION": ["科技处初审意见.pdf"]
    }
  }
}
```

执行链路：

```text
RBAC 校验
  ↓
BusinessService 保存 payload
  ↓
ValidatorService 校验并生成材料版本
  ↓
ConditionService 匹配 BPMN transition / Gateway
  ↓
StateMachineRuntime 写状态记录
  ↓
写 state_record_remark
  ↓
写 state_record_material
  ↓
关闭旧 task_instance，创建新 task_instance
  ↓
ActionService 生成 process_document
  ↓
发布 SSE
  ↓
返回最新 ViewModel
```

常见错误：

| 状态码 | 场景 |
|---|---|
| `400` | 当前节点不允许该 `eventType`，或 Gateway 条件不匹配 |
| `401` | `X-Demo-User` 不存在 |
| `403` | 当前用户角色不匹配节点 `candidateRole` |
| `404` | 项目、模块或模块实例不存在 |

## SSE 接口

### `GET /api/sse/subscribe`

建立 SSE 连接。

前端用途：

- 首页收到 `MODULE_STATE_CHANGED` 后刷新事项列表
- 详情页收到 `MODULE_STATE_CHANGED` 后重新拉取 ViewModel 并刷新时间线、BPMN 高亮、任务面板

事件格式：

```text
event: MODULE_STATE_CHANGED
data: {"type":"MODULE_STATE_CHANGED","projectId":1001,"moduleInstanceId":5001,"moduleType":"APPLICATION","fromState":"APPLICATION_DRAFT","toState":"APPLICATION_DEPT_REVIEWING","seq":3,"eventType":"USER_CONFIRMED_SUBMIT","occurredAt":"2026-06-01 20:10:00"}
```

事件 JSON 字段：

| 字段 | 说明 |
|---|---|
| `type` | 固定为 `MODULE_STATE_CHANGED` |
| `projectId` | 项目 ID |
| `moduleInstanceId` | 模块实例 ID |
| `moduleType` | 模块类型 |
| `fromState` | 原状态 |
| `toState` | 新状态 |
| `seq` | 状态记录序号 |
| `eventType` | 触发事件 |
| `occurredAt` | 发生时间 |

## 前端调用关系

首页 `frontend/index.html`：

| 场景 | 接口 |
|---|---|
| 加载事项列表 | `GET /api/dashboard/items` |
| 状态变化自动刷新 | `GET /api/sse/subscribe` |
| 跳转详情页 | `/detail.html?moduleInstanceId={id}` |

详情页 `frontend/detail.html`：

| 场景 | 接口 |
|---|---|
| 加载角色列表 | `GET /api/meta` |
| 加载 BPMN XML | `GET /api/module-instances/{id}/bpmn` |
| 加载 ViewModel | `GET /api/module-instances/{id}/view-model` |
| 切换模拟用户 | 带新 `X-Demo-User` 重拉 ViewModel |
| 提交审批/退回/代录 | `POST /api/module-instances/{id}/actions` |
| 状态变化自动刷新 | `GET /api/sse/subscribe` |

## 与设计文档的映射

| 设计概念 | v0.1 实现 |
|---|---|
| `workflow_definition` | `store.workflow_definitions` |
| `workflow_node` | `store.workflow_nodes` |
| `material_type` | `store.material_types` |
| `workflow_node_material_requirement` | `store.node_requirements` |
| `workflow_node_document_config` | `store.node_document_configs` |
| `project_module_instance` | `Project.modules` / `ModuleInstance` |
| `module_state_record` | `ModuleInstance.state_records` |
| `state_record_remark` | `ModuleInstance.remarks` |
| `state_record_material` | `ModuleInstance.record_materials` |
| `task_instance` | `ModuleInstance.tasks` |
| `process_document` | `ModuleInstance.documents` |
| ViewModel 组装器 | `RuntimeEngine.vm()` |
| RBAC 中间件 | `rbac_identity_middleware` + `RbacService` |
| SSE 通知 | `publish()` + `/api/sse/subscribe` |

