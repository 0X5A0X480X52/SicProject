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

CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL UNIQUE,
    project_name VARCHAR(200) NOT NULL,
    project_type VARCHAR(64) NULL,
    project_level VARCHAR(64) NULL,
    approved_amount DECIMAL(18,2) NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    owner_user_id BIGINT NOT NULL,
    owner_name_snapshot VARCHAR(100) NOT NULL,
    dept_id BIGINT NULL,
    dept_name_snapshot VARCHAR(100) NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    business_status VARCHAR(64) NOT NULL DEFAULT 'APPLICATION',
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_project_owner_status(owner_user_id, lifecycle_status),
    INDEX idx_project_dept_status(dept_id, lifecycle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(64) NOT NULL,
    responsibility VARCHAR(500) NULL,
    user_name_snapshot VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_project_member(project_id, user_id),
    INDEX idx_project_member_user(user_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_access_grant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    grantee_user_id BIGINT NOT NULL,
    grantee_name_snapshot VARCHAR(100) NOT NULL,
    grant_role_code VARCHAR(64) NOT NULL,
    grant_scope VARCHAR(64) NULL,
    module_type VARCHAR(64) NULL,
    round_no INT NULL,
    task_node_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    grant_reason VARCHAR(500) NULL,
    effective_from TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    effective_to TIMESTAMP(6) NULL,
    granted_by BIGINT NOT NULL,
    granted_by_name_snapshot VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_grant_decision(project_id, grantee_user_id, status),
    INDEX idx_grant_role_scope(project_id, grant_role_code, module_type, round_no, task_node_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_access_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    allowed BOOLEAN NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_access_log_project_time(project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_operation_log (
    id CHAR(36) PRIMARY KEY,
    scope_type VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    project_id BIGINT NULL,
    project_name_snapshot VARCHAR(200) NULL,
    operator_user_id BIGINT NULL,
    operator_name_snapshot VARCHAR(100) NULL,
    target_user_id BIGINT NULL,
    target_name_snapshot VARCHAR(100) NULL,
    grant_type VARCHAR(64) NULL,
    role_code VARCHAR(64) NULL,
    permission_code VARCHAR(100) NULL,
    before_snapshot TEXT NULL,
    after_snapshot TEXT NULL,
    remark VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_admin_log_project_time(project_id, created_at),
    INDEX idx_admin_log_operator_time(operator_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application_saga (
    saga_id CHAR(36) PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE,
    actor_user_id BIGINT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    module_instance_id BIGINT NULL,
    response_json JSON NULL,
    last_error VARCHAR(1000) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_saga_recovery(status, next_retry_at),
    INDEX idx_saga_idempotency(actor_user_id, request_hash, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_snapshot (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    dept_id BIGINT NULL,
    dept_name VARCHAR(100) NULL,
    role_codes_json JSON NOT NULL,
    enabled BOOLEAN NOT NULL,
    source_version BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

