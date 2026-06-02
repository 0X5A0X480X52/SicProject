# v0.1 Python 全栈验证 Demo

## 目标
- 验证“Flowable 仅解析 BPMN + 自研运行时推进”可行性。
- 提供完整前后端：后端 FastAPI，前端纯 HTML。
- 前端界面沿用 `demo/v0.0` 的 `index.html` 与 `detail.html`。
- 覆盖关键能力：
  - 流程发布器：从 BPMN 解析并注册 `workflow_definition`、`workflow_node`、`material_type`、节点材料要求和单据配置
  - BPMN 扩展标签加载（`workflowNode`/`transition`）
  - 项目与模块实例创建
  - 状态迁移（含简单条件和 Gateway 自动推进）
  - Business Service 保存业务 payload
  - Validator / ConditionHandler / ActionHandler 管线
  - 待办任务（`task_instance`语义）
  - `module_state_record`、`state_record_remark`、`state_record_material` 语义
  - `process_document` 单据快照固化
  - SSE 通知前端刷新 ViewModel
  - RBAC 鉴权中间件与节点候选角色校验

## 启动
```bash
cd demo/v0.1
python -m pip install -r requirements.txt
python -m uvicorn app:app --reload --port 8010
```

浏览器打开：`http://127.0.0.1:8010/`

## 说明
- 该 Demo 使用内存存储，不写数据库。
- BPMN 来源：`docs/bpmn_auxiliary_tags_augmented/*.bpmn`
- 详情页“提交审批”会直接调用后端状态迁移接口，并通过 SSE 触发页面重新拉取 ViewModel。
- 详情页右侧角色下拉框会模拟不同登录用户；切换后重新拉取 ViewModel，并按 RBAC 结果显示可办理/只读、操作按钮和 BPMN/时间线状态。
- 时间线、BPMN 高亮、材料要求和操作按钮均由后端 ViewModel 生成，不再使用静态 mock 进度。
- RBAC Demo 用户通过 `X-Demo-User` 请求头识别：
  - `leader`：项目负责人
  - `dept_admin`：二级单位管理员
  - `science_admin`：科技处管理员
  - `expert`：专家
  - `admin`：全角色调试用户
- 前端操作时，可在 `payload` 中输入条件变量，例如：
  - `{"deptApproved": true}`
  - `{"authorityApproved": false}`
