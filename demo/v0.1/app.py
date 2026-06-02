from __future__ import annotations

import asyncio
import json
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from bpmn_loader import load_bpmn_processes, now_text
from engine import RuntimeEngine
from store import store

BASE_DIR = Path(__file__).resolve().parent
PROCESS_DIR = BASE_DIR.parent.parent / "docs" / "bpmn_auxiliary_tags_augmented"

processes = load_bpmn_processes(PROCESS_DIR)
engine = RuntimeEngine(processes)

app = FastAPI(title="BPMN State Machine Demo v0.1")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/frontend", StaticFiles(directory=str(BASE_DIR / "frontend"), html=True), name="frontend")

SUBSCRIBERS: list[asyncio.Queue[str]] = []


@app.middleware("http")
async def rbac_identity_middleware(request: Request, call_next):
    if request.url.path.startswith("/api"):
        user_id = request.headers.get("x-demo-user", "admin")
        if user_id not in store.users:
            return JSONResponse({"detail": "unknown demo user"}, status_code=401)
        request.state.demo_user = store.users[user_id]
    return await call_next(request)


class CreateProjectReq(BaseModel):
    projectName: str
    owner: str
    projectCategory: str = "校级限项项目"
    isLimitedProject: bool = True
    isSchoolLevelAcceptance: bool = True


class StartModuleReq(BaseModel):
    moduleType: str


class ActionReq(BaseModel):
    eventType: str
    operator: str
    remark: str = ""
    payload: dict[str, Any] = {}


def current_user(x_demo_user: str = Header(default="admin")):
    user = store.users.get(x_demo_user)
    if not user:
        raise HTTPException(401, "unknown demo user")
    return user


def publish(event: dict[str, Any]) -> None:
    data = f"event: MODULE_STATE_CHANGED\ndata: {json.dumps(event, ensure_ascii=False)}\n\n"
    for q in list(SUBSCRIBERS):
        q.put_nowait(data)


def get_module(project_id: int, module_type: str):
    p = store.projects.get(project_id)
    if not p:
        raise HTTPException(404, "project not found")
    m = p.modules.get(module_type)
    if not m:
        raise HTTPException(404, "module not started")
    return m


def get_module_by_instance(module_instance_id: int):
    for p in store.projects.values():
        for m in p.modules.values():
            if m.module_instance_id == module_instance_id:
                return m
    raise HTTPException(404, "module instance not found")


@app.get("/")
def home():
    return FileResponse(BASE_DIR / "frontend" / "index.html")

@app.get("/detail.html")
def detail_page():
    return FileResponse(BASE_DIR / "frontend" / "detail.html")


@app.get("/api/meta")
def meta():
    return {
        "moduleTypes": sorted(processes.keys()),
        "users": [
            {"userId": u.user_id, "realName": u.real_name, "dept": u.dept, "roles": sorted(u.roles)}
            for u in store.users.values()
        ],
    }


@app.post("/api/projects")
def create_project(req: CreateProjectReq):
    p = store.create_project(
        req.projectName,
        req.owner,
        project_category=req.projectCategory,
        is_limited_project=req.isLimitedProject,
        is_school_level_acceptance=req.isSchoolLevelAcceptance,
    )
    return {"projectId": p.project_id, "projectName": p.project_name, "owner": p.owner}


@app.get("/api/projects")
def list_projects():
    out = []
    for p in store.projects.values():
        out.append({
            "projectId": p.project_id,
            "projectName": p.project_name,
            "owner": p.owner,
            "projectCategory": p.project_category,
            "isLimitedProject": p.is_limited_project,
            "isSchoolLevelAcceptance": p.is_school_level_acceptance,
            "modules": list(p.modules.keys()),
        })
    return out


@app.post("/api/projects/{project_id}/modules")
def start_module(project_id: int, req: StartModuleReq):
    if req.moduleType not in processes:
        raise HTTPException(400, "unknown module type")
    if project_id not in store.projects:
        raise HTTPException(404, "project not found")
    m = engine.start_module(project_id, req.moduleType)
    return engine.vm(m, store.users["admin"])


@app.get("/api/projects/{project_id}/modules/{module_type}/view-model")
def view_model(project_id: int, module_type: str, user=Depends(current_user)):
    m = get_module(project_id, module_type)
    return engine.vm(m, user)


@app.get("/api/module-instances/{module_instance_id}/view-model")
def view_model_by_instance(module_instance_id: int, user=Depends(current_user)):
    return engine.vm(get_module_by_instance(module_instance_id), user)


@app.get("/api/module-instances/{module_instance_id}/bpmn")
def bpmn_xml_by_instance(module_instance_id: int):
    module = get_module_by_instance(module_instance_id)
    definition = store.workflow_definitions[module.workflow_definition_id]
    path = PROCESS_DIR / definition.bpmn_file
    if not path.exists():
        raise HTTPException(404, "bpmn file not found")
    return {"bpmnXml": path.read_text(encoding="utf-8")}


@app.get("/api/dashboard/items")
def dashboard_items():
    items = []
    module_key = {"APPLICATION": "apply", "CONTRACT": "contract", "ACCEPTANCE": "acceptance"}
    for p in store.projects.values():
        for module_type, m in p.modules.items():
            vm = engine.vm(m)
            tasks = vm["tasks"]
            current = vm["current"]
            if current["stateCode"] and current["stateCode"].endswith("APPROVED"):
                status = "completed"
            elif tasks:
                status = "running"
            else:
                status = "draft"
            items.append({
                "id": f"{module_type}-{m.module_instance_id}",
                "moduleInstanceId": m.module_instance_id,
                "projectId": p.project_id,
                "module": module_key.get(module_type, module_type.lower()),
                "name": p.project_name,
                "desc": f"{module_type} · {m.process_key}",
                "owner": p.owner,
                "dept": "默认单位",
                "projectCategory": p.project_category,
                "year": "2026",
                "status": status,
                "node": current.get("nodeName") or "-",
                "material": 80,
                "materialText": "Demo数据",
                "round": 1,
                "returns": 0,
                "update": now_text(),
                "mine": True,
                "todo": bool(tasks),
                "doneByMe": False,
                "overdue": False,
            })
    return items


@app.post("/api/projects/{project_id}/modules/{module_type}/actions")
def do_action(project_id: int, module_type: str, req: ActionReq, user=Depends(current_user)):
    m = get_module(project_id, module_type)
    before = m.current_state
    try:
        rec = engine.transit(m, req.eventType, user, req.remark or req.operator, req.payload)
    except PermissionError as exc:
        raise HTTPException(403, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    publish({
        "type": "MODULE_STATE_CHANGED",
        "projectId": project_id,
        "moduleInstanceId": m.module_instance_id,
        "moduleType": module_type,
        "fromState": before,
        "toState": m.current_state,
        "seq": rec.seq,
        "eventType": rec.event_type,
        "occurredAt": now_text(),
    })
    return engine.vm(m, user)


@app.post("/api/module-instances/{module_instance_id}/actions")
def do_action_by_instance(module_instance_id: int, req: ActionReq, user=Depends(current_user)):
    m = get_module_by_instance(module_instance_id)
    before = m.current_state
    try:
        rec = engine.transit(m, req.eventType, user, req.remark or req.operator, req.payload)
    except PermissionError as exc:
        raise HTTPException(403, str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    publish({
        "type": "MODULE_STATE_CHANGED",
        "projectId": m.project_id,
        "moduleInstanceId": m.module_instance_id,
        "moduleType": m.module_type,
        "fromState": before,
        "toState": m.current_state,
        "seq": rec.seq,
        "eventType": rec.event_type,
        "occurredAt": now_text(),
    })
    return engine.vm(m, user)


@app.get("/api/sse/subscribe")
async def sse_subscribe():
    queue: asyncio.Queue[str] = asyncio.Queue()
    SUBSCRIBERS.append(queue)

    async def gen():
        try:
            while True:
                msg = await queue.get()
                yield msg
        finally:
            if queue in SUBSCRIBERS:
                SUBSCRIBERS.remove(queue)

    return StreamingResponse(gen(), media_type="text/event-stream")


@app.on_event("startup")
def seed_data():
    if store.projects:
        return
    p = store.create_project("示例科研项目A", "张老师")
    for module in ("APPLICATION", "CONTRACT", "ACCEPTANCE"):
        if module in processes:
            engine.start_module(p.project_id, module)
