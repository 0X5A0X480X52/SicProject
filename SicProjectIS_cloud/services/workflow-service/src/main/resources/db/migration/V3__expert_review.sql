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
    INDEX idx_review_batch_module(module_instance_id, round_no)
);

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
);

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
);
