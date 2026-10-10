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
);

CREATE TABLE material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    material_type_code VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_material_project_type(project_id, material_type_code)
);

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
);

CREATE TABLE generated_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    module_instance_id BIGINT NOT NULL,
    state_seq INT NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_generated_document(module_instance_id, state_seq, document_type)
);

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
);

CREATE TABLE material_reservation_item (
    reservation_id CHAR(36) NOT NULL,
    material_version_id BIGINT NOT NULL,
    material_type_code VARCHAR(64) NOT NULL,
    PRIMARY KEY(reservation_id, material_version_id),
    INDEX idx_reserved_version(material_version_id)
);
