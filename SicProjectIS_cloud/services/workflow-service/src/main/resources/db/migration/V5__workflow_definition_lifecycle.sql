ALTER TABLE workflow_definition
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER version;

UPDATE workflow_definition SET status=CASE WHEN published=TRUE THEN 'ACTIVE' ELSE 'DRAFT' END;
