from __future__ import annotations

from dataclasses import asdict
from typing import Any

from bpmn_loader import ProcessDef, TransitionDef, now_text
from store import (
    MaterialTypeRow,
    MaterialVersion,
    ModuleInstance,
    NodeDocumentConfigRow,
    NodeMaterialRequirementRow,
    ProcessDocument,
    StateRecord,
    StateRecordMaterial,
    StateRecordRemark,
    Task,
    User,
    WorkflowDefinitionRow,
    WorkflowNodeRow,
    store,
)


class WorkflowPublisher:
    def __init__(self, processes: dict[str, ProcessDef]) -> None:
        self.processes = processes
        self.definition_by_module: dict[str, int] = {}
        self.node_row_by_module_node: dict[tuple[str, str], int] = {}

    def publish_all(self) -> None:
        if self.definition_by_module:
            return
        for process in self.processes.values():
            definition_id = store.next_workflow_definition_id()
            store.workflow_definitions[definition_id] = WorkflowDefinitionRow(
                definition_id,
                process.process_key,
                process.process_name,
                process.module_type,
                process.bpmn_file,
                1,
                "ACTIVE",
            )
            self.definition_by_module[process.module_type] = definition_id
            for node in process.nodes.values():
                node_row_id = store.next_workflow_node_id()
                store.workflow_nodes[node_row_id] = WorkflowNodeRow(
                    node_row_id,
                    definition_id,
                    node.node_id,
                    node.node_name,
                    node.node_type,
                    node.state_code,
                    node.candidate_role_code,
                    node.operation_mode,
                )
                self.node_row_by_module_node[(process.module_type, node.node_id)] = node_row_id
            for req in process.material_requirements:
                material_type_id = self._ensure_material_type(
                    req.material_type_code,
                    req.material_type_name,
                    process.module_type,
                    req.allowed_file_types,
                    req.max_file_size_mb,
                )
                store.node_requirements.append(
                    NodeMaterialRequirementRow(
                        store.next_requirement_id(),
                        self.node_row_by_module_node[(process.module_type, req.node_id)],
                        material_type_id,
                        req.requirement_timing,
                        req.required,
                        req.min_count,
                        req.usage_type,
                        req.validator_key,
                    )
                )
            for cfg in process.document_configs:
                output_material_type_id = None
                if cfg.output_material_type_code:
                    output_material_type_id = self._ensure_material_type(
                        cfg.output_material_type_code,
                        cfg.output_material_type_name or cfg.output_material_type_code,
                        process.module_type,
                        "pdf",
                        50,
                    )
                store.node_document_configs.append(
                    NodeDocumentConfigRow(
                        store.next_document_config_id(),
                        self.node_row_by_module_node[(process.module_type, cfg.node_id)],
                        cfg.document_type_code,
                        cfg.document_type_name,
                        cfg.generate_timing,
                        cfg.template_code,
                        output_material_type_id,
                        cfg.required,
                    )
                )

    def _ensure_material_type(self, code: str, name: str, module_type: str, allowed: str | None, max_size: int | None) -> int:
        for material_type_id, row in store.material_types.items():
            if row.material_type_code == code:
                return material_type_id
        material_type_id = store.next_material_type_id()
        store.material_types[material_type_id] = MaterialTypeRow(material_type_id, code, name, module_type, allowed, max_size)
        return material_type_id


class RbacService:
    def assert_can_operate(self, user: User, module: ModuleInstance, node_role: str | None) -> None:
        if "SYSTEM_ADMIN" in user.roles:
            return
        project = store.projects[module.project_id]
        if node_role == "PROJECT_LEADER" and project.owner_user_id == user.user_id:
            return
        if node_role and node_role in user.roles:
            return
        raise PermissionError(f"{user.real_name} 缺少当前节点角色权限: {node_role or '-'}")


class BusinessService:
    def save_payload(self, module: ModuleInstance, payload: dict[str, Any]) -> None:
        module.business_data.update(payload)


class ValidatorService:
    def validate(self, module: ModuleInstance, node_id: str | None, payload: dict[str, Any]) -> list[MaterialVersion]:
        if not node_id:
            return []
        node_row = self._node_row(module, node_id)
        reqs = [r for r in store.node_requirements if r.workflow_node_id == node_row.workflow_node_id]
        linked: list[MaterialVersion] = []
        supplied = payload.get("materials") or {}
        for req in reqs:
            material_type = store.material_types[req.material_type_id]
            files = supplied.get(material_type.material_type_code)
            if files is None:
                files = [f"{material_type.material_type_name}.pdf"] if req.required else []
            if req.required and len(files) < max(1, req.min_count):
                raise ValueError(f"缺少必填材料: {material_type.material_type_name}")
            for file_name in files:
                mv = MaterialVersion(
                    store.next_material_version_id(),
                    module.project_id,
                    material_type.material_type_code,
                    str(file_name),
                    payload.get("operatorUserId", "system"),
                    now_text(),
                )
                module.materials.append(mv)
                linked.append(mv)
        return linked

    def _node_row(self, module: ModuleInstance, node_id: str) -> WorkflowNodeRow:
        for row in store.workflow_nodes.values():
            if row.workflow_definition_id == module.workflow_definition_id and row.node_id == node_id:
                return row
        raise ValueError(f"未知节点: {node_id}")


class ConditionService:
    def matches(self, transition: TransitionDef, payload: dict[str, Any]) -> bool:
        if not transition.condition_key:
            return True
        value = payload.get(transition.condition_key)
        if value is None:
            return False
        return str(value).lower() == str(transition.condition_value).lower()


class ActionService:
    def after_transition(self, module: ModuleInstance, record: StateRecord, transition: TransitionDef) -> None:
        self._generate_documents(module, record, "ON_NODE_COMPLETE")
        if module.current_node_id:
            proc = self._process(module)
            node = proc.nodes.get(module.current_node_id)
            if node and node.node_type == "END_EVENT":
                self._generate_documents(module, record, "ON_PROCESS_END")

    def _generate_documents(self, module: ModuleInstance, record: StateRecord, timing: str) -> None:
        if not record.to_node_id:
            return
        node_row = None
        for row in store.workflow_nodes.values():
            if row.workflow_definition_id == module.workflow_definition_id and row.node_id == record.to_node_id:
                node_row = row
                break
        if not node_row:
            return
        for cfg in store.node_document_configs:
            if cfg.workflow_node_id != node_row.workflow_node_id or cfg.generate_timing != timing:
                continue
            module.documents.append(
                ProcessDocument(
                    store.next_document_id(),
                    module.module_instance_id,
                    record.state_record_id,
                    cfg.document_type_code,
                    cfg.document_type_name,
                    {
                        "projectId": module.project_id,
                        "moduleType": module.module_type,
                        "state": record.to_state,
                        "businessData": module.business_data,
                    },
                    now_text(),
                )
            )

    def _process(self, module: ModuleInstance) -> ProcessDef:
        return runtime_processes[module.module_type]


runtime_processes: dict[str, ProcessDef] = {}


class RuntimeEngine:
    def __init__(self, processes: dict[str, ProcessDef]):
        global runtime_processes
        self.processes = processes
        runtime_processes = processes
        self.publisher = WorkflowPublisher(processes)
        self.publisher.publish_all()
        self.rbac = RbacService()
        self.business = BusinessService()
        self.validator = ValidatorService()
        self.condition = ConditionService()
        self.actions = ActionService()

    def start_module(self, project_id: int, module_type: str) -> ModuleInstance:
        p = store.projects[project_id]
        if module_type in p.modules:
            return p.modules[module_type]
        proc = self.processes[module_type]
        start = next((n for n in proc.nodes.values() if n.node_type == "START_EVENT"), None)
        module = ModuleInstance(
            store.next_module_id(),
            project_id,
            module_type,
            proc.process_key,
            self.publisher.definition_by_module[module_type],
            start.state_code if start else None,
            start.node_id if start else None,
        )
        p.modules[module_type] = module
        self._append_record(module, "PROCESS_STARTED", None, module.current_state or "UNKNOWN", None, module.current_node_id, "START", {}, "流程启动", "system", [])
        self._auto_advance(module, self._runtime_context(module, {"operatorUserId": "system"}))
        self._activate_next_task(module)
        return module

    def available_events(self, module: ModuleInstance) -> list[dict[str, Any]]:
        proc = self.processes[module.module_type]
        events = []
        for t in proc.transitions:
            if t.source_ref == module.current_node_id:
                target_node = proc.nodes.get(t.target_ref)
                gateway_outgoing = [g for g in proc.transitions if target_node and target_node.node_type == "GATEWAY" and g.source_ref == target_node.node_id]
                if gateway_outgoing:
                    ordered = sorted(gateway_outgoing, key=lambda item: 1 if any(word in item.event_type for word in ("REJECT", "RETURN")) else 0)
                    for g in ordered:
                        events.append({
                            "eventType": t.event_type,
                            "branchEventType": g.event_type,
                            "label": self._label_for_transition(g),
                            "toState": g.target_state_code,
                            "toNode": g.target_ref,
                            "payloadPreset": {g.condition_key: self._coerce_condition_value(g.condition_value)} if g.condition_key else {},
                        })
                else:
                    label = self._label_for_transition(t)
                    events.append({"eventType": t.event_type, "branchEventType": t.event_type, "label": label, "toState": t.target_state_code, "toNode": t.target_ref, "payloadPreset": {}})
        return events

    def transit(self, module: ModuleInstance, event_type: str, user: User, remark: str, payload: dict[str, Any]) -> StateRecord:
        proc = self.processes[module.module_type]
        node = proc.nodes.get(module.current_node_id or "")
        self.rbac.assert_can_operate(user, module, node.candidate_role_code if node else None)
        payload = dict(payload)
        payload["operatorUserId"] = user.user_id
        payload = self._runtime_context(module, payload)
        self.business.save_payload(module, payload)
        linked_materials = self.validator.validate(module, module.current_node_id, payload)

        current = module.current_node_id
        candidates = [t for t in proc.transitions if t.source_ref == current and t.event_type == event_type]
        if not candidates:
            raise ValueError(f"event {event_type} not allowed at node {current}")
        chosen = next((t for t in candidates if self.condition.matches(t, payload)), None)
        if chosen is None:
            raise ValueError("没有匹配当前 payload 的流转条件")

        self._close_tasks(module)
        record = self._move(module, chosen, payload, remark or f"{event_type} 执行", user.user_id, linked_materials)
        self._auto_advance(module, payload)
        self._activate_next_task(module)
        return record

    def _runtime_context(self, module: ModuleInstance, payload: dict[str, Any]) -> dict[str, Any]:
        project = store.projects[module.project_id]
        context = {
            "projectCategory": project.project_category,
            "isLimitedProject": project.is_limited_project,
            "isSchoolLevelAcceptance": project.is_school_level_acceptance,
        }
        context.update(payload)
        return context

    def _auto_advance(self, module: ModuleInstance, payload: dict[str, Any]) -> None:
        proc = self.processes[module.module_type]
        while True:
            node = proc.nodes.get(module.current_node_id or "")
            if not node or node.node_type not in {"START_EVENT", "GATEWAY"}:
                return
            outgoing = [t for t in proc.transitions if t.source_ref == node.node_id and self.condition.matches(t, payload)]
            if not outgoing:
                return
            self._move(module, outgoing[0], payload, f"系统自动推进: {outgoing[0].event_type}", "system", [])

    def _move(self, module: ModuleInstance, transition: TransitionDef, payload: dict[str, Any], summary: str, operator_user_id: str, linked_materials: list[MaterialVersion]) -> StateRecord:
        from_state = module.current_state
        from_node = module.current_node_id
        module.current_state = transition.target_state_code or self._state_for_node(module, transition.target_ref)
        module.current_node_id = transition.target_ref
        record = self._append_record(module, transition.event_type, from_state, module.current_state or "UNKNOWN", from_node, transition.target_ref, transition.result, payload, summary, operator_user_id, linked_materials)
        self.actions.after_transition(module, record, transition)
        return record

    def _append_record(self, module: ModuleInstance, event_type: str, from_state: str | None, to_state: str, from_node: str | None, to_node: str | None, result: str | None, payload: dict[str, Any], summary: str, operator_user_id: str, linked_materials: list[MaterialVersion]) -> StateRecord:
        record = StateRecord(store.next_state_record_id(), len(module.state_records) + 1, 1, event_type, from_state, to_state, from_node, to_node, result, summary, now_text(), payload)
        module.state_records.append(record)
        module.remarks.append(
            StateRecordRemark(
                store.next_remark_id(),
                record.state_record_id,
                operator_user_id,
                "SYSTEM" if operator_user_id == "system" else "OPERATOR",
                event_type,
                result,
                True,
                summary,
                now_text(),
            )
        )
        for material in linked_materials:
            module.record_materials.append(
                StateRecordMaterial(store.next_record_material_id(), record.state_record_id, material.material_version_id, "SUBMITTED_FILE", True)
            )
        return record

    def _close_tasks(self, module: ModuleInstance) -> None:
        for t in module.tasks:
            if t.status == "PENDING":
                t.status = "COMPLETED"
                t.completed_at = now_text()

    def _activate_next_task(self, module: ModuleInstance) -> None:
        proc = self.processes[module.module_type]
        node = proc.nodes.get(module.current_node_id or "")
        if not node or node.node_type in {"END_EVENT", "START_EVENT", "GATEWAY"}:
            return
        if any(t.node_id == node.node_id and t.status == "PENDING" for t in module.tasks):
            return
        module.tasks.append(Task(store.next_task_id(), node.node_id, node.state_code or "", node.candidate_role_code, "PENDING", now_text()))

    def _state_for_node(self, module: ModuleInstance, node_id: str) -> str | None:
        node = self.processes[module.module_type].nodes.get(node_id)
        return node.state_code if node else None

    def _label_for_transition(self, transition: TransitionDef) -> str:
        if any(word in transition.event_type for word in ("REJECT", "RETURN")):
            return "退回"
        if any(word in transition.event_type for word in ("APPROVE", "PASS", "COMPLETED", "SUBMITTED", "FINISHED", "REGISTERED", "PRINTED", "SIGNED", "SEALED")):
            return "通过/提交"
        return transition.event_type

    def _coerce_condition_value(self, value: str | None) -> Any:
        if value is None:
            return None
        if value.lower() == "true":
            return True
        if value.lower() == "false":
            return False
        return value

    def vm(self, module: ModuleInstance, user: User | None = None) -> dict[str, Any]:
        proc = self.processes[module.module_type]
        node = proc.nodes.get(module.current_node_id or "")
        can_operate = False
        denied_reason = ""
        if user:
            try:
                self.rbac.assert_can_operate(user, module, node.candidate_role_code if node else None)
                can_operate = bool([t for t in module.tasks if t.status == "PENDING"])
            except PermissionError as exc:
                denied_reason = str(exc)
        node_row_id = self.publisher.node_row_by_module_node.get((module.module_type, module.current_node_id or ""), -1)
        reqs = [r for r in store.node_requirements if r.workflow_node_id == node_row_id]
        docs = [d for d in store.node_document_configs if d.workflow_node_id == node_row_id]
        return {
            "moduleInstanceId": module.module_instance_id,
            "projectId": module.project_id,
            "moduleType": module.module_type,
            "processKey": module.process_key,
            "workflowDefinition": asdict(store.workflow_definitions[module.workflow_definition_id]),
            "current": {
                "nodeId": module.current_node_id,
                "stateCode": module.current_state,
                "nodeName": node.node_name if node else None,
                "nodeType": node.node_type if node else None,
                "candidateRole": node.candidate_role_code if node else None,
                "operationMode": node.operation_mode if node else None,
            },
            "permission": {"canOperate": can_operate, "deniedReason": denied_reason, "user": asdict(user) if user else None},
            "availableActions": self.available_events(module) if can_operate or user is None else [],
            "requiredMaterials": [self._requirement_vm(r) for r in reqs],
            "documentConfigs": [asdict(d) for d in docs],
            "tasks": [asdict(t) for t in module.tasks if t.status == "PENDING"],
            "history": [asdict(r) for r in module.state_records],
            "remarks": [asdict(r) for r in module.remarks],
            "attachments": [asdict(m) for m in module.materials],
            "recordMaterials": [asdict(m) for m in module.record_materials],
            "documents": [asdict(d) for d in module.documents],
            "businessData": module.business_data,
            "projectInfo": {
                "projectCategory": store.projects[module.project_id].project_category,
                "isLimitedProject": store.projects[module.project_id].is_limited_project,
                "isSchoolLevelAcceptance": store.projects[module.project_id].is_school_level_acceptance,
            },
            "workflowNodes": [asdict(n) for n in proc.nodes.values()],
            "workflowTransitions": [asdict(t) for t in proc.transitions],
        }

    def _requirement_vm(self, req: NodeMaterialRequirementRow) -> dict[str, Any]:
        row = asdict(req)
        row["materialType"] = asdict(store.material_types[req.material_type_id])
        return row
