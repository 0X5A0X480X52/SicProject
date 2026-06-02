from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class User:
    user_id: str
    real_name: str
    dept: str
    roles: set[str]


@dataclass
class WorkflowDefinitionRow:
    workflow_definition_id: int
    process_key: str
    process_name: str
    module_type: str
    bpmn_file: str
    version_no: int
    status: str


@dataclass
class WorkflowNodeRow:
    workflow_node_id: int
    workflow_definition_id: int
    node_id: str
    node_name: str
    node_type: str
    state_code: str | None
    candidate_role_code: str | None
    operation_mode: str | None


@dataclass
class MaterialTypeRow:
    material_type_id: int
    material_type_code: str
    material_type_name: str
    module_type: str
    allowed_file_types: str | None = None
    max_file_size_mb: int | None = None


@dataclass
class NodeMaterialRequirementRow:
    requirement_id: int
    workflow_node_id: int
    material_type_id: int
    requirement_timing: str
    required: bool
    min_count: int
    usage_type: str | None
    validator_key: str | None = None
    description: str | None = None


@dataclass
class NodeDocumentConfigRow:
    document_config_id: int
    workflow_node_id: int
    document_type_code: str
    document_type_name: str
    generate_timing: str
    template_code: str | None
    output_material_type_id: int | None
    required: bool


@dataclass
class StateRecord:
    state_record_id: int
    seq: int
    round_no: int
    event_type: str
    from_state: str | None
    to_state: str
    from_node_id: str | None
    to_node_id: str | None
    result: str | None
    summary: str
    created_at: str
    payload: dict[str, Any] = field(default_factory=dict)


@dataclass
class StateRecordRemark:
    remark_id: int
    state_record_id: int
    participant_user_id: str
    participant_type: str
    action_type: str
    result: str | None
    is_operator: bool
    remark_content: str
    created_at: str


@dataclass
class MaterialVersion:
    material_version_id: int
    project_id: int
    material_type_code: str
    file_name: str
    uploaded_by: str
    uploaded_at: str
    is_current: bool = True


@dataclass
class StateRecordMaterial:
    record_material_id: int
    state_record_id: int
    material_version_id: int
    material_usage: str
    is_required: bool


@dataclass
class ProcessDocument:
    document_id: int
    module_instance_id: int
    generated_state_record_id: int
    document_type_code: str
    document_title: str
    snapshot_json: dict[str, Any]
    generated_at: str


@dataclass
class Task:
    task_id: int
    node_id: str
    state_code: str
    candidate_role: str | None
    status: str
    created_at: str
    completed_at: str | None = None


@dataclass
class ModuleInstance:
    module_instance_id: int
    project_id: int
    module_type: str
    process_key: str
    workflow_definition_id: int
    current_state: str | None
    current_node_id: str | None
    state_records: list[StateRecord] = field(default_factory=list)
    remarks: list[StateRecordRemark] = field(default_factory=list)
    materials: list[MaterialVersion] = field(default_factory=list)
    record_materials: list[StateRecordMaterial] = field(default_factory=list)
    tasks: list[Task] = field(default_factory=list)
    documents: list[ProcessDocument] = field(default_factory=list)
    business_data: dict[str, Any] = field(default_factory=dict)


@dataclass
class Project:
    project_id: int
    project_name: str
    owner: str
    owner_user_id: str
    dept: str
    project_category: str
    is_limited_project: bool
    is_school_level_acceptance: bool
    modules: dict[str, ModuleInstance] = field(default_factory=dict)


class InMemoryStore:
    def __init__(self) -> None:
        self.users: dict[str, User] = {
            "leader": User("leader", "张老师", "计算机与信息学院", {"PROJECT_LEADER"}),
            "dept_admin": User("dept_admin", "李老师", "计算机与信息学院", {"DEPT_ADMIN"}),
            "science_admin": User("science_admin", "王老师", "科技处", {"SCIENCE_ADMIN"}),
            "expert": User("expert", "评审专家", "专家库", {"EXPERT"}),
            "admin": User("admin", "系统管理员", "科技处", {"SYSTEM_ADMIN", "PROJECT_LEADER", "DEPT_ADMIN", "SCIENCE_ADMIN", "EXPERT"}),
        }
        self.projects: dict[int, Project] = {}
        self.workflow_definitions: dict[int, WorkflowDefinitionRow] = {}
        self.workflow_nodes: dict[int, WorkflowNodeRow] = {}
        self.material_types: dict[int, MaterialTypeRow] = {}
        self.node_requirements: list[NodeMaterialRequirementRow] = []
        self.node_document_configs: list[NodeDocumentConfigRow] = []
        self._pid = 1000
        self._mid = 5000
        self._task_id = 1
        self._workflow_definition_id = 1
        self._workflow_node_id = 1
        self._material_type_id = 1
        self._requirement_id = 1
        self._document_config_id = 1
        self._state_record_id = 1
        self._remark_id = 1
        self._material_version_id = 1
        self._record_material_id = 1
        self._document_id = 1

    def create_project(
        self,
        project_name: str,
        owner: str,
        owner_user_id: str = "leader",
        dept: str = "计算机与信息学院",
        project_category: str = "校级限项项目",
        is_limited_project: bool = True,
        is_school_level_acceptance: bool = True,
    ) -> Project:
        self._pid += 1
        p = Project(
            self._pid,
            project_name,
            owner,
            owner_user_id,
            dept,
            project_category,
            is_limited_project,
            is_school_level_acceptance,
        )
        self.projects[p.project_id] = p
        return p

    def next_module_id(self) -> int:
        self._mid += 1
        return self._mid

    def next_task_id(self) -> int:
        t = self._task_id
        self._task_id += 1
        return t

    def next_workflow_definition_id(self) -> int:
        v = self._workflow_definition_id
        self._workflow_definition_id += 1
        return v

    def next_workflow_node_id(self) -> int:
        v = self._workflow_node_id
        self._workflow_node_id += 1
        return v

    def next_material_type_id(self) -> int:
        v = self._material_type_id
        self._material_type_id += 1
        return v

    def next_requirement_id(self) -> int:
        v = self._requirement_id
        self._requirement_id += 1
        return v

    def next_document_config_id(self) -> int:
        v = self._document_config_id
        self._document_config_id += 1
        return v

    def next_state_record_id(self) -> int:
        v = self._state_record_id
        self._state_record_id += 1
        return v

    def next_remark_id(self) -> int:
        v = self._remark_id
        self._remark_id += 1
        return v

    def next_material_version_id(self) -> int:
        v = self._material_version_id
        self._material_version_id += 1
        return v

    def next_record_material_id(self) -> int:
        v = self._record_material_id
        self._record_material_id += 1
        return v

    def next_document_id(self) -> int:
        v = self._document_id
        self._document_id += 1
        return v


store = InMemoryStore()
