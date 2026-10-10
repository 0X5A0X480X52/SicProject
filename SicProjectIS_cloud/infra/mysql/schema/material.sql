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

CREATE TABLE material_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    module_type VARCHAR(64) NULL,
    allowed_file_types VARCHAR(255) NULL,
    max_file_size_mb INT NOT NULL DEFAULT 50,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    material_type_code VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_material_project_type(project_id, material_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    file_status VARCHAR(32) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_material_version(material_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE generated_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    command_id VARCHAR(160) NULL,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    state_seq INT NOT NULL,
    state_record_id BIGINT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_name VARCHAR(200) NULL,
    template_code VARCHAR(100) NULL,
    material_version_id BIGINT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(128) NULL,
    size_bytes BIGINT NULL,
    sha256 CHAR(64) NOT NULL,
    snapshot_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_generated_document(module_instance_id, state_seq, document_type),
    UNIQUE KEY uk_generated_document_command(command_id),
    INDEX idx_generated_document_material_version(material_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_reservation (
    reservation_id CHAR(36) PRIMARY KEY,
    command_id VARCHAR(100) NOT NULL UNIQUE,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    expected_seq INT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_reservation_cleanup(status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE material_reservation_item (
    reservation_id CHAR(36) NOT NULL,
    material_version_id BIGINT NOT NULL,
    material_type_code VARCHAR(64) NOT NULL,
    PRIMARY KEY(reservation_id, material_version_id),
    INDEX idx_reserved_version(material_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE object_deletion_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_version_id BIGINT NOT NULL UNIQUE,
    object_key VARCHAR(1024) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    INDEX idx_object_deletion_dispatch(status, next_attempt_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

