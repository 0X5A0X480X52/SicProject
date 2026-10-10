ALTER TABLE generated_document
    ADD COLUMN command_id VARCHAR(160) NULL AFTER id,
    ADD COLUMN state_record_id BIGINT NULL AFTER state_seq,
    ADD COLUMN document_name VARCHAR(200) NULL AFTER document_type,
    ADD COLUMN template_code VARCHAR(100) NULL AFTER document_name,
    ADD COLUMN material_version_id BIGINT NULL AFTER template_code,
    ADD COLUMN original_filename VARCHAR(255) NULL AFTER object_key,
    ADD COLUMN content_type VARCHAR(128) NULL AFTER original_filename,
    ADD COLUMN size_bytes BIGINT NULL AFTER content_type,
    ADD COLUMN snapshot_json JSON NULL AFTER sha256,
    ADD UNIQUE KEY uk_generated_document_command(command_id),
    ADD INDEX idx_generated_document_material_version(material_version_id);
