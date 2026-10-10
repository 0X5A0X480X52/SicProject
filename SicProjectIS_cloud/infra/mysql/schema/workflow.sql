-- Final MySQL 8.4 schema. Empty database only; contains no DROP/TRUNCATE.
SET NAMES utf8mb4;

CREATE TABLE outbox_event (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    routing_key VARCHAR(160) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    lock_owner VARCHAR(64) NULL,
    locked_until TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    INDEX idx_outbox_pending (published_at, created_at),
    INDEX idx_outbox_dispatch(published_at, next_attempt_at, locked_until, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consumed_event (
    event_id VARCHAR(36) NOT NULL,
    consumer_name VARCHAR(160) NOT NULL,
    consumed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published BOOLEAN NOT NULL DEFAULT FALSE,
    bpmn_xml LONGTEXT NOT NULL,
    definition_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_workflow_code_version(code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE module_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    saga_id CHAR(36) NULL UNIQUE,
    project_id BIGINT NOT NULL,
    started_by_user_id BIGINT NULL,
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
    INDEX idx_module_state(current_state, finished),
    UNIQUE KEY uk_module_instance_project_code(project_id, module_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
    INDEX idx_task_role_status(candidate_role, status),
    INDEX idx_workflow_task_open_role(status, candidate_role, module_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE node_form (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    round_no INT NOT NULL,
    form_json JSON NOT NULL,
    submitted_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_form_instance_node_round(module_instance_id, node_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    limited_project BOOLEAN NOT NULL DEFAULT FALSE,
    summary TEXT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE contract_form (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_contract_instance_round(module_instance_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE acceptance_form (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_acceptance_instance_round(module_instance_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_material_reference (
    module_instance_id BIGINT NOT NULL,
    seq INT NOT NULL,
    material_version_id BIGINT NOT NULL,
    material_type_code VARCHAR(64) NOT NULL,
    material_type_snapshot JSON NULL,
    PRIMARY KEY(module_instance_id, seq, material_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_snapshot (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    source_version BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_snapshot (
    project_id BIGINT PRIMARY KEY,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    owner_user_id BIGINT NULL,
    owner_name_snapshot VARCHAR(100) NULL,
    dept_id BIGINT NULL,
    lifecycle_stage VARCHAR(64) NULL,
    source_version BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_project_snapshot_owner(owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expert_review_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    workflow_node_id BIGINT NULL,
    round_no INT NOT NULL,
    review_type VARCHAR(64) NOT NULL,
    review_title VARCHAR(200) NOT NULL,
    rule_type VARCHAR(32) NOT NULL DEFAULT 'AVERAGE',
    min_expert_count INT NOT NULL DEFAULT 3,
    pass_score DECIMAL(8,2) NULL,
    recommend_score DECIMAL(8,2) NULL,
    remove_highest_lowest BOOLEAN NOT NULL DEFAULT FALSE,
    expected_expert_count INT NULL,
    submitted_expert_count INT NOT NULL DEFAULT 0,
    valid_expert_count INT NOT NULL DEFAULT 0,
    lowest_score DECIMAL(8,2) NULL,
    highest_score DECIMAL(8,2) NULL,
    final_score DECIMAL(8,2) NULL,
    final_result VARCHAR(32) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    access_ended_at TIMESTAMP(6) NULL,
    INDEX idx_review_batch_module(module_instance_id, round_no),
    INDEX idx_expert_access_recovery(access_ended_at, module_instance_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expert_review_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
    expert_name_snapshot VARCHAR(100) NULL,
    expert_org_snapshot VARCHAR(200) NULL,
    expert_title_snapshot VARCHAR(100) NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    conflict_of_interest BOOLEAN NOT NULL DEFAULT FALSE,
    valid BOOLEAN NOT NULL DEFAULT TRUE,
    total_score DECIMAL(8,2) NULL,
    review_result VARCHAR(32) NULL,
    review_comment VARCHAR(2000) NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_review_batch_expert(batch_id, expert_user_id),
    INDEX idx_review_assignment_expert(expert_user_id, review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expert_review_score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    weight DECIMAL(8,4) NOT NULL DEFAULT 1,
    max_score DECIMAL(8,2) NOT NULL DEFAULT 100,
    score_value DECIMAL(8,2) NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_review_assignment_item(assignment_id, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_node_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_definition_id BIGINT NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    state_code VARCHAR(128) NULL,
    lane_name VARCHAR(200) NULL,
    responsible_actor_code VARCHAR(64) NULL,
    responsible_actor_name VARCHAR(100) NULL,
    candidate_role_code VARCHAR(64) NULL,
    operation_mode VARCHAR(32) NULL,
    represented_actor_code VARCHAR(64) NULL,
    represented_actor_name VARCHAR(100) NULL,
    material_requirements_json JSON NOT NULL,
    document_configs_json JSON NOT NULL,
    UNIQUE KEY uk_workflow_node_definition(workflow_definition_id, node_id),
    INDEX idx_workflow_node_state(workflow_definition_id, state_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_transition_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_definition_id BIGINT NOT NULL,
    transition_id VARCHAR(128) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    target_ref VARCHAR(128) NOT NULL,
    source_state_code VARCHAR(128) NULL,
    target_state_code VARCHAR(128) NULL,
    event_type VARCHAR(128) NOT NULL,
    result_code VARCHAR(64) NULL,
    condition_type VARCHAR(32) NULL,
    condition_key VARCHAR(128) NULL,
    condition_value VARCHAR(500) NULL,
    condition_handler_key VARCHAR(128) NULL,
    condition_expression VARCHAR(1000) NULL,
    action_keys_json JSON NOT NULL,
    UNIQUE KEY uk_workflow_transition_definition(workflow_definition_id, transition_id),
    INDEX idx_workflow_transition_source(workflow_definition_id, source_ref, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_achievement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NULL,
    acceptance_id BIGINT NULL,
    achievement_type VARCHAR(64) NOT NULL,
    achievement_title VARCHAR(300) NOT NULL,
    author_list VARCHAR(1000) NULL,
    achievement_level VARCHAR(100) NULL,
    publish_or_grant_date DATE NULL,
    proof_material_version_id BIGINT NULL,
    remark VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_achievement_project(project_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE acceptance_financial_settlement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    acceptance_id BIGINT NULL,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT NULL,
    approved_amount DECIMAL(18,2) NULL,
    received_amount DECIMAL(18,2) NOT NULL,
    spent_amount DECIMAL(18,2) NOT NULL,
    surplus_amount DECIMAL(18,2) NOT NULL,
    execution_rate DECIMAL(8,2) NOT NULL,
    settlement_result VARCHAR(64) NULL,
    finance_operator_id BIGINT NOT NULL,
    finance_review_comment VARCHAR(2000) NULL,
    settled_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_financial_settlement_project(project_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notice_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NULL,
    state_record_id BIGINT NULL,
    module_type VARCHAR(64) NOT NULL,
    notice_type VARCHAR(64) NOT NULL,
    notice_title VARCHAR(300) NOT NULL,
    notice_no VARCHAR(100) NULL,
    publish_unit VARCHAR(200) NULL,
    publish_user_id BIGINT NOT NULL,
    publish_time TIMESTAMP(6) NULL,
    notice_scope VARCHAR(100) NULL,
    target_dept_scope TEXT NULL,
    target_user_scope TEXT NULL,
    project_category VARCHAR(100) NULL,
    limited_project BOOLEAN NULL,
    limit_count INT NULL,
    start_time TIMESTAMP(6) NULL,
    deadline_time TIMESTAMP(6) NULL,
    material_requirement_summary TEXT NULL,
    content_summary TEXT NULL,
    remark TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_notice_module_type(module_type, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

