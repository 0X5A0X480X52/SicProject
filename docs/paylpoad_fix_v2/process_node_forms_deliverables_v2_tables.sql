-- V2 structured table additions
-- Target: MySQL 8.0+

CREATE TABLE IF NOT EXISTS notice_record (
    notice_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT,
    module_type VARCHAR(64) NOT NULL,
    notice_type VARCHAR(64) NOT NULL,
    notice_title VARCHAR(255) NOT NULL,
    notice_no VARCHAR(128),
    publish_unit VARCHAR(255),
    publish_user_id BIGINT,
    publish_time DATETIME,
    notice_scope VARCHAR(255),
    target_dept_scope TEXT,
    target_user_scope TEXT,
    project_category VARCHAR(128),
    is_limited_project BOOLEAN,
    limit_count INT,
    start_time DATETIME,
    deadline_time DATETIME,
    material_requirement_summary TEXT,
    content_summary TEXT,
    remark TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS state_record_check_item (
    check_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    state_record_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    node_id VARCHAR(128),
    state_code VARCHAR(128),
    item_code VARCHAR(128) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    item_type VARCHAR(64) NOT NULL,
    item_value VARCHAR(255),
    item_result VARCHAR(64),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    passed BOOLEAN,
    remark TEXT,
    sort_no INT,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS external_result_record (
    external_result_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT NOT NULL,
    module_type VARCHAR(64) NOT NULL,
    result_type VARCHAR(64) NOT NULL,
    external_actor_code VARCHAR(128),
    external_actor_name VARCHAR(255),
    external_result VARCHAR(64) NOT NULL,
    external_result_date DATE,
    external_file_no VARCHAR(128),
    external_system_no VARCHAR(128),
    approved_amount DECIMAL(14,2),
    effective_date DATE,
    summary TEXT,
    registered_by BIGINT,
    registered_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS seal_record (
    seal_record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT NOT NULL,
    module_type VARCHAR(64) NOT NULL,
    seal_subject VARCHAR(128) NOT NULL,
    seal_type VARCHAR(64),
    seal_reason TEXT,
    copy_count INT,
    applicant_user_id BIGINT,
    handled_by BIGINT,
    leader_signed BOOLEAN NOT NULL DEFAULT FALSE,
    leader_signed_at DATETIME,
    school_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    school_sealed_at DATETIME,
    external_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    external_actor_name VARCHAR(255),
    external_sealed_at DATETIME,
    seal_status VARCHAR(64),
    remark TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS submission_record (
    submission_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT NOT NULL,
    module_type VARCHAR(64) NOT NULL,
    submission_type VARCHAR(64) NOT NULL,
    target_actor_code VARCHAR(128),
    target_actor_name VARCHAR(255),
    submission_method VARCHAR(64),
    submission_no VARCHAR(128),
    external_system_no VARCHAR(128),
    receipt_no VARCHAR(128),
    submitted_by BIGINT,
    submitted_at DATETIME NOT NULL,
    material_summary TEXT,
    remark TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS archive_record (
    archive_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT NOT NULL,
    module_type VARCHAR(64) NOT NULL,
    archive_type VARCHAR(64) NOT NULL,
    archive_no VARCHAR(128),
    archive_location VARCHAR(255),
    paper_copy_count INT,
    electronic_copy_count INT,
    archived_by BIGINT,
    archived_at DATETIME NOT NULL,
    archive_status VARCHAR(64),
    remark TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS project_application_ext (
    application_ext_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT,
    application_category VARCHAR(128),
    application_batch_no VARCHAR(128),
    application_notice_id BIGINT,
    application_notice_no VARCHAR(128),
    is_limited_project BOOLEAN,
    limit_group VARCHAR(128),
    expected_budget DECIMAL(14,2),
    expected_start_date DATE,
    expected_end_date DATE,
    dept_recommend_rank INT,
    dept_recommend_score DECIMAL(6,2),
    dept_recommend_result VARCHAR(64),
    science_recommend_rank INT,
    science_recommend_score DECIMAL(6,2),
    science_recommend_result VARCHAR(64),
    authority_approval_no VARCHAR(128),
    authority_approval_date DATE,
    authority_result VARCHAR(64),
    authority_approved_amount DECIMAL(14,2),
    final_submission_no VARCHAR(128),
    final_submission_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    UNIQUE (application_id)
);

CREATE TABLE IF NOT EXISTS project_application_detail (
    application_detail_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    research_background TEXT,
    research_objective TEXT,
    research_content TEXT,
    innovation_points TEXT,
    technical_route TEXT,
    schedule_plan TEXT,
    budget_description TEXT,
    expected_outcomes TEXT,
    feasibility_analysis TEXT,
    risk_analysis TEXT,
    applicant_commitment TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    UNIQUE (application_id)
);

CREATE TABLE IF NOT EXISTS project_application_publicity (
    publicity_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT,
    publicity_title VARCHAR(255) NOT NULL,
    publicity_scope VARCHAR(255),
    publicity_start_date DATE NOT NULL,
    publicity_end_date DATE NOT NULL,
    recommended_rank INT,
    recommended_reason TEXT,
    has_objection BOOLEAN NOT NULL DEFAULT FALSE,
    objection_content TEXT,
    objection_handling_result VARCHAR(64),
    objection_handling_comment TEXT,
    publicity_result VARCHAR(64) NOT NULL,
    confirmed_by BIGINT,
    confirmed_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS project_contract_ext (
    contract_ext_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT,
    contract_source VARCHAR(128),
    party_a_name VARCHAR(255),
    party_a_contact VARCHAR(128),
    party_a_phone VARCHAR(64),
    party_b_name VARCHAR(255),
    party_b_contact VARCHAR(128),
    party_b_phone VARCHAR(64),
    authority_review_result VARCHAR(64),
    authority_review_date DATE,
    authority_review_opinion TEXT,
    leader_signed_at DATETIME,
    school_sealed_at DATETIME,
    authority_sealed_at DATETIME,
    effective_date DATE,
    archive_no VARCHAR(128),
    archive_location VARCHAR(255),
    archive_copies INT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    UNIQUE (contract_id)
);

CREATE TABLE IF NOT EXISTS project_acceptance_ext (
    acceptance_ext_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    acceptance_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT,
    is_school_level_acceptance BOOLEAN,
    acceptance_type VARCHAR(128),
    acceptance_batch_no VARCHAR(128),
    task_completion_rate DECIMAL(5,2),
    paper_count INT DEFAULT 0,
    patent_count INT DEFAULT 0,
    software_copyright_count INT DEFAULT 0,
    other_achievement_count INT DEFAULT 0,
    science_review_result VARCHAR(64),
    authority_review_result VARCHAR(64),
    authority_review_date DATE,
    authority_file_no VARCHAR(128),
    expert_final_score DECIMAL(6,2),
    expert_final_result VARCHAR(64),
    certificate_no VARCHAR(128),
    certificate_issue_date DATE,
    surplus_amount DECIMAL(14,2),
    surplus_return_required BOOLEAN,
    surplus_return_status VARCHAR(64),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    UNIQUE (acceptance_id)
);

CREATE TABLE IF NOT EXISTS acceptance_financial_settlement (
    settlement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    acceptance_id BIGINT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT,
    approved_amount DECIMAL(14,2),
    received_amount DECIMAL(14,2),
    spent_amount DECIMAL(14,2),
    surplus_amount DECIMAL(14,2),
    execution_rate DECIMAL(5,2),
    settlement_result VARCHAR(64),
    finance_operator_id BIGINT,
    finance_review_comment TEXT,
    settled_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS project_achievement (
    achievement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT,
    acceptance_id BIGINT,
    achievement_type VARCHAR(64) NOT NULL,
    achievement_title VARCHAR(255) NOT NULL,
    author_list TEXT,
    achievement_level VARCHAR(128),
    publish_or_grant_date DATE,
    proof_material_version_id BIGINT,
    remark TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS surplus_funds_return_record (
    return_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    acceptance_id BIGINT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    state_record_id BIGINT,
    surplus_amount DECIMAL(14,2) NOT NULL,
    return_required BOOLEAN NOT NULL DEFAULT TRUE,
    return_account_name VARCHAR(255),
    return_account_no VARCHAR(128),
    return_bank_name VARCHAR(255),
    return_status VARCHAR(64),
    returned_amount DECIMAL(14,2),
    returned_at DATETIME,
    finance_operator_id BIGINT,
    remark TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS expert_review_batch (
    batch_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_instance_id BIGINT NOT NULL,
    workflow_node_id BIGINT,
    state_record_id BIGINT,
    review_type VARCHAR(64) NOT NULL,
    review_title VARCHAR(255) NOT NULL,
    rule_type VARCHAR(64) NOT NULL,
    min_expert_count INT NOT NULL DEFAULT 3,
    pass_score DECIMAL(5,2),
    recommend_score DECIMAL(5,2),
    remove_highest_lowest BOOLEAN NOT NULL DEFAULT FALSE,
    expected_expert_count INT,
    submitted_expert_count INT DEFAULT 0,
    valid_expert_count INT DEFAULT 0,
    highest_score DECIMAL(5,2),
    lowest_score DECIMAL(5,2),
    final_score DECIMAL(5,2),
    final_result VARCHAR(64),
    rank_no INT,
    summary_comment TEXT,
    status VARCHAR(32) NOT NULL,
    created_by BIGINT,
    created_at DATETIME NOT NULL,
    completed_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS expert_review_assignment (
    assignment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
    expert_name VARCHAR(128),
    expert_org VARCHAR(255),
    expert_title VARCHAR(128),
    assigned_at DATETIME NOT NULL,
    submitted_at DATETIME,
    review_status VARCHAR(32) NOT NULL,
    conflict_of_interest BOOLEAN NOT NULL DEFAULT FALSE,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    total_score DECIMAL(5,2),
    review_result VARCHAR(64),
    review_comment TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    UNIQUE (batch_id, expert_user_id)
);

CREATE TABLE IF NOT EXISTS expert_review_score (
    score_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    score_item_code VARCHAR(64) NOT NULL,
    score_item_name VARCHAR(128) NOT NULL,
    weight DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    max_score DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    score_value DECIMAL(5,2) NOT NULL,
    comment TEXT,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_notice_module ON notice_record (module_instance_id, notice_type);
CREATE INDEX idx_check_state_record ON state_record_check_item (state_record_id);
CREATE INDEX idx_external_module_type ON external_result_record (module_instance_id, result_type);
CREATE INDEX idx_seal_module ON seal_record (module_instance_id, seal_subject);
CREATE INDEX idx_submission_module ON submission_record (module_instance_id, submission_type);
CREATE INDEX idx_archive_module ON archive_record (module_instance_id, archive_type);
CREATE INDEX idx_app_ext_project ON project_application_ext (project_id);
CREATE INDEX idx_contract_ext_project ON project_contract_ext (project_id);
CREATE INDEX idx_acceptance_ext_project ON project_acceptance_ext (project_id);
CREATE INDEX idx_finance_module ON acceptance_financial_settlement (module_instance_id);
CREATE INDEX idx_achievement_project ON project_achievement (project_id, achievement_type);
CREATE INDEX idx_surplus_module ON surplus_funds_return_record (module_instance_id);
CREATE INDEX idx_expert_batch_module ON expert_review_batch (module_instance_id, review_type);
CREATE INDEX idx_expert_assignment_batch ON expert_review_assignment (batch_id);
CREATE INDEX idx_expert_score_assignment ON expert_review_score (assignment_id);
