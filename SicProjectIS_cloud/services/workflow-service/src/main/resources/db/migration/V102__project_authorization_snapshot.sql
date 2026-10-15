ALTER TABLE project_snapshot
    ADD COLUMN owner_user_id BIGINT NULL AFTER project_name,
    ADD COLUMN owner_name_snapshot VARCHAR(100) NULL AFTER owner_user_id,
    ADD COLUMN dept_id BIGINT NULL AFTER owner_name_snapshot;

CREATE INDEX idx_project_snapshot_owner ON project_snapshot(owner_user_id);
CREATE INDEX idx_workflow_task_open_role ON workflow_task(status, candidate_role, module_instance_id);

ALTER TABLE expert_review_batch
    ADD COLUMN access_ended_at TIMESTAMP(6) NULL AFTER completed_at;
CREATE INDEX idx_expert_access_recovery ON expert_review_batch(access_ended_at, module_instance_id, round_no);
