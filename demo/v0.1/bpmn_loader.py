from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any
import xml.etree.ElementTree as ET

NS = {
    "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
    "rm": "http://example.com/research-management",
}


@dataclass
class NodeDef:
    node_id: str
    node_name: str
    node_type: str
    state_code: str | None
    candidate_role_code: str | None
    operation_mode: str | None


@dataclass
class MaterialRequirementDef:
    node_id: str
    material_type_code: str
    material_type_name: str
    requirement_timing: str
    required: bool
    min_count: int
    usage_type: str | None
    allowed_file_types: str | None
    max_file_size_mb: int | None
    validator_key: str | None


@dataclass
class DocumentConfigDef:
    node_id: str
    document_type_code: str
    document_type_name: str
    generate_timing: str
    template_code: str | None
    output_material_type_code: str | None
    output_material_type_name: str | None
    required: bool


@dataclass
class TransitionDef:
    flow_id: str
    source_ref: str
    target_ref: str
    event_type: str
    result: str | None
    source_state_code: str | None
    target_state_code: str | None
    condition_type: str | None
    condition_key: str | None
    condition_value: str | None
    action_keys: list[str] = field(default_factory=list)


@dataclass
class ProcessDef:
    process_key: str
    module_type: str
    process_name: str
    bpmn_file: str
    nodes: dict[str, NodeDef] = field(default_factory=dict)
    transitions: list[TransitionDef] = field(default_factory=list)
    material_requirements: list[MaterialRequirementDef] = field(default_factory=list)
    document_configs: list[DocumentConfigDef] = field(default_factory=list)


def _guess_node_type(elem: ET.Element) -> str:
    tag = elem.tag.split("}")[-1]
    mapping = {
        "startEvent": "START_EVENT",
        "endEvent": "END_EVENT",
        "userTask": "USER_TASK",
        "serviceTask": "SERVICE_TASK",
        "exclusiveGateway": "GATEWAY",
    }
    return mapping.get(tag, tag.upper())


def load_bpmn_processes(bpmn_dir: Path) -> dict[str, ProcessDef]:
    processes: dict[str, ProcessDef] = {}
    for file in sorted(bpmn_dir.glob("*.bpmn")):
        root = ET.parse(file).getroot()
        process = root.find("bpmn:process", NS)
        if process is None:
            continue

        proc_cfg = process.find("bpmn:extensionElements/rm:processConfig", NS)
        process_key = proc_cfg.attrib.get("processKey") if proc_cfg is not None else process.attrib.get("id", file.stem)
        module_type = proc_cfg.attrib.get("moduleType", process_key) if proc_cfg is not None else process_key
        p = ProcessDef(
            process_key=process_key,
            module_type=module_type,
            process_name=process.attrib.get("name", process_key),
            bpmn_file=file.name,
        )

        for elem in process:
            tag = elem.tag.split("}")[-1]
            if tag in {"startEvent", "endEvent", "userTask", "serviceTask", "exclusiveGateway"}:
                w = elem.find("bpmn:extensionElements/rm:workflowNode", NS)
                node_id = elem.attrib.get("id")
                p.nodes[node_id] = NodeDef(
                    node_id=node_id,
                    node_name=elem.attrib.get("name", node_id),
                    node_type=(w.attrib.get("nodeType") if w is not None else _guess_node_type(elem)),
                    state_code=(w.attrib.get("stateCode") if w is not None else None),
                    candidate_role_code=(w.attrib.get("candidateRoleCode") if w is not None else None),
                    operation_mode=(w.attrib.get("operationMode") if w is not None else None),
                )
                for m in elem.findall("bpmn:extensionElements/rm:materialRequirement", NS):
                    p.material_requirements.append(
                        MaterialRequirementDef(
                            node_id=node_id,
                            material_type_code=m.attrib.get("materialTypeCode", ""),
                            material_type_name=m.attrib.get("materialTypeName", m.attrib.get("materialTypeCode", "")),
                            requirement_timing=m.attrib.get("requirementTiming", "BEFORE_SUBMIT"),
                            required=m.attrib.get("required", "false").lower() == "true",
                            min_count=int(m.attrib.get("minCount", "0")),
                            usage_type=m.attrib.get("usageType"),
                            allowed_file_types=m.attrib.get("allowedFileTypes"),
                            max_file_size_mb=int(m.attrib["maxFileSizeMb"]) if m.attrib.get("maxFileSizeMb") else None,
                            validator_key=m.attrib.get("validatorKey"),
                        )
                    )
                for d in elem.findall("bpmn:extensionElements/rm:documentConfig", NS):
                    p.document_configs.append(
                        DocumentConfigDef(
                            node_id=node_id,
                            document_type_code=d.attrib.get("documentTypeCode", ""),
                            document_type_name=d.attrib.get("documentTypeName", d.attrib.get("documentTypeCode", "")),
                            generate_timing=d.attrib.get("generateTiming", "ON_NODE_COMPLETE"),
                            template_code=d.attrib.get("templateCode"),
                            output_material_type_code=d.attrib.get("outputMaterialTypeCode"),
                            output_material_type_name=d.attrib.get("outputMaterialTypeName"),
                            required=d.attrib.get("required", "false").lower() == "true",
                        )
                    )
            if tag == "sequenceFlow":
                t = elem.find("bpmn:extensionElements/rm:transition", NS)
                if t is None:
                    continue
                p.transitions.append(
                    TransitionDef(
                        flow_id=elem.attrib.get("id", ""),
                        source_ref=elem.attrib.get("sourceRef", ""),
                        target_ref=elem.attrib.get("targetRef", ""),
                        event_type=t.attrib.get("eventType", ""),
                        result=t.attrib.get("result"),
                        source_state_code=t.attrib.get("sourceStateCode"),
                        target_state_code=t.attrib.get("targetStateCode"),
                        condition_type=t.attrib.get("conditionType"),
                        condition_key=t.attrib.get("conditionKey"),
                        condition_value=t.attrib.get("conditionValue"),
                        action_keys=[x.strip() for x in t.attrib.get("actionKeys", "").split(",") if x.strip()],
                    )
                )

        processes[p.module_type] = p
    return processes


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
