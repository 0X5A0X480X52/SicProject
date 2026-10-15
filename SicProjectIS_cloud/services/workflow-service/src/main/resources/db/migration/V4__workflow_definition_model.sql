ALTER TABLE module_instance
    ADD COLUMN started_by_user_id BIGINT NULL AFTER project_id;

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
);

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
);
