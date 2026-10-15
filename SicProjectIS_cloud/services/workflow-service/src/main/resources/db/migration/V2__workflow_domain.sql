CREATE TABLE workflow_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    bpmn_xml LONGTEXT NOT NULL,
    definition_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_workflow_code_version(code, version)
);

CREATE TABLE module_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    saga_id CHAR(36) NULL UNIQUE,
    project_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    definition_id BIGINT NULL,
    current_node_id VARCHAR(64) NOT NULL,
    current_state VARCHAR(64) NOT NULL,
    current_seq INT NOT NULL DEFAULT 0,
    current_round INT NOT NULL DEFAULT 1,
    finished BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_module_project(project_id, module_code),
    INDEX idx_module_state(current_state, finished)
);

CREATE TABLE state_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    seq INT NOT NULL,
    round_no INT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    from_state VARCHAR(64) NULL,
    to_state VARCHAR(64) NOT NULL,
    from_node_id VARCHAR(64) NULL,
    to_node_id VARCHAR(64) NOT NULL,
    result VARCHAR(64) NULL,
    summary VARCHAR(1000) NULL,
    actor_user_id BIGINT NOT NULL,
    actor_name_snapshot VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_state_instance_seq(module_instance_id, seq)
);

CREATE TABLE workflow_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    assignee_user_id BIGINT NULL,
    candidate_role VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    round_no INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    INDEX idx_task_assignee_status(assignee_user_id, status),
    INDEX idx_task_role_status(candidate_role, status)
);

CREATE TABLE node_form (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    round_no INT NOT NULL,
    form_json JSON NOT NULL,
    submitted_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_form_instance_node_round(module_instance_id, node_id, round_no)
);

CREATE TABLE project_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    limited_project BOOLEAN NOT NULL DEFAULT FALSE,
    summary TEXT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE contract_form (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_contract_instance_round(module_instance_id, round_no)
);

CREATE TABLE acceptance_form (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_acceptance_instance_round(module_instance_id, round_no)
);

CREATE TABLE expert_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    result VARCHAR(32) NOT NULL,
    score DECIMAL(5,2) NULL,
    comment VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_review_task_expert(task_id, expert_user_id)
);

CREATE TABLE workflow_material_reference (
    module_instance_id BIGINT NOT NULL,
    seq INT NOT NULL,
    material_version_id BIGINT NOT NULL,
    material_type_code VARCHAR(64) NOT NULL,
    material_type_snapshot JSON NULL,
    PRIMARY KEY(module_instance_id, seq, material_version_id)
);

CREATE TABLE user_snapshot (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    source_version BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE project_snapshot (
    project_id BIGINT PRIMARY KEY,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    lifecycle_stage VARCHAR(64) NULL,
    source_version BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

INSERT INTO workflow_definition(code,name,version,published,bpmn_xml,definition_json) VALUES
('APPLICATION','项目申请',1,TRUE,'<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="APPLICATION_PROCESS"><startEvent id="StartEvent"/><userTask id="SubmitApplicationTask" name="填写项目申请"/></process></definitions>','{}'),
('CONTRACT','项目合同',1,TRUE,'<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="CONTRACT_PROCESS"><startEvent id="StartEvent"/><userTask id="SubmitContractTask" name="填写项目合同"/></process></definitions>','{}'),
('ACCEPTANCE','项目验收',1,TRUE,'<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="ACCEPTANCE_PROCESS"><startEvent id="StartEvent"/><userTask id="SubmitAcceptanceTask" name="填写项目验收"/></process></definitions>','{}');
