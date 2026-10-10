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
);
