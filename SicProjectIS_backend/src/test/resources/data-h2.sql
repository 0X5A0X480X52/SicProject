INSERT INTO `department` (`dept_id`, `dept_code`, `dept_name`, `parent_dept_id`, `enabled`, `created_at`, `updated_at`)
VALUES (1, 'SCI', 'Science Department', NULL, TRUE, TIMESTAMP '2026-01-01 09:00:00', TIMESTAMP '2026-01-01 09:00:00');

INSERT INTO `department` (`dept_id`, `dept_code`, `dept_name`, `parent_dept_id`, `enabled`, `created_at`, `updated_at`)
VALUES (2, 'ENG', 'Engineering Department', NULL, TRUE, TIMESTAMP '2026-01-01 09:01:00', TIMESTAMP '2026-01-01 09:01:00');

INSERT INTO `app_user` (`user_id`, `username`, `password_hash`, `real_name`, `dept_id`, `phone`, `email`, `enabled`, `created_at`, `updated_at`)
VALUES (1, 'alice', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6r0HaqzU1aTKmM.mjz0zx1NQf1iku', 'Alice Zhang', 1, '13800000000', 'alice@example.com', TRUE, TIMESTAMP '2026-01-01 09:05:00', TIMESTAMP '2026-01-01 09:05:00');

INSERT INTO `app_user` (`user_id`, `username`, `password_hash`, `real_name`, `dept_id`, `phone`, `email`, `enabled`, `created_at`, `updated_at`)
VALUES (2, 'bob', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6r0HaqzU1aTKmM.mjz0zx1NQf1iku', 'Bob Li', 1, '13900000000', 'bob@example.com', TRUE, TIMESTAMP '2026-01-01 09:06:00', TIMESTAMP '2026-01-01 09:06:00');

INSERT INTO `app_user` (`user_id`, `username`, `password_hash`, `real_name`, `dept_id`, `phone`, `email`, `enabled`, `created_at`, `updated_at`)
VALUES (3, 'carol', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6r0HaqzU1aTKmM.mjz0zx1NQf1iku', 'Carol Wu', 1, '13600000000', 'carol@example.com', TRUE, TIMESTAMP '2026-01-01 09:07:00', TIMESTAMP '2026-01-01 09:07:00');

INSERT INTO `app_user` (`user_id`, `username`, `password_hash`, `real_name`, `dept_id`, `phone`, `email`, `enabled`, `created_at`, `updated_at`)
VALUES (4, 'diana', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6r0HaqzU1aTKmM.mjz0zx1NQf1iku', 'Diana Sun', 1, '13500000000', 'diana@example.com', TRUE, TIMESTAMP '2026-01-01 09:08:00', TIMESTAMP '2026-01-01 09:08:00');

INSERT INTO `app_user` (`user_id`, `username`, `password_hash`, `real_name`, `dept_id`, `phone`, `email`, `enabled`, `created_at`, `updated_at`)
VALUES (5, 'eve', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6r0HaqzU1aTKmM.mjz0zx1NQf1iku', 'Eve Gao', 1, '13400000000', 'eve@example.com', TRUE, TIMESTAMP '2026-01-01 09:09:00', TIMESTAMP '2026-01-01 09:09:00');

INSERT INTO `app_user` (`user_id`, `username`, `password_hash`, `real_name`, `dept_id`, `phone`, `email`, `enabled`, `created_at`, `updated_at`)
VALUES (6, 'frank', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6r0HaqzU1aTKmM.mjz0zx1NQf1iku', 'Frank He', 2, '13300000000', 'frank@example.com', TRUE, TIMESTAMP '2026-01-01 09:10:00', TIMESTAMP '2026-01-01 09:10:00');

INSERT INTO `role` (`role_id`, `role_code`, `role_name`, `role_desc`, `enabled`)
VALUES (1, 'PROJECT_LEADER', 'Project Leader', 'Principal investigator role', TRUE);

INSERT INTO `role` (`role_id`, `role_code`, `role_name`, `role_desc`, `enabled`)
VALUES (2, 'EXPERT', 'Expert', 'Reviewer role without project view permission', TRUE);

INSERT INTO `role` (`role_id`, `role_code`, `role_name`, `role_desc`, `enabled`)
VALUES (3, 'SCIENCE_ADMIN', 'Science Admin', 'Research office administrator', TRUE);

INSERT INTO `role` (`role_id`, `role_code`, `role_name`, `role_desc`, `enabled`)
VALUES (4, 'DEPT_ADMIN', 'Department Admin', 'Department administrator', TRUE);

INSERT INTO `role` (`role_id`, `role_code`, `role_name`, `role_desc`, `enabled`)
VALUES (5, 'SYSTEM_ADMIN', 'System Admin', 'System-wide administrator', TRUE);

INSERT INTO `role` (`role_id`, `role_code`, `role_name`, `role_desc`, `enabled`)
VALUES (6, 'FINANCE_ADMIN', 'Finance Admin', 'Finance handler role', TRUE);

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (1, 'project:view', 'View Project', 'API', 'Read project information');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (2, 'project:authorization:manage', 'Manage Project Authorization', 'API', 'Manage project authorization settings');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (3, 'project:expert:assign', 'Assign Project Expert', 'API', 'Assign experts to project modules');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (4, 'admin:overview:view', 'View Admin Overview', 'API', 'View permission administration overview');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (5, 'user:manage', 'Manage Users', 'API', 'Manage users and their system roles');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (6, 'role-permission:manage', 'Manage Role Permissions', 'API', 'Manage role permission matrix');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (7, 'audit-log:view', 'View Audit Logs', 'API', 'View authorization audit logs');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (8, 'project:finance:assign', 'Assign Project Finance', 'API', 'Assign project finance handlers');

INSERT INTO `permission` (`permission_id`, `permission_code`, `permission_name`, `permission_type`, `permission_desc`)
VALUES (9, 'project:proxy:assign', 'Assign Project Proxy Recorder', 'API', 'Assign project proxy recorders');

INSERT INTO `user_role` (`user_role_id`, `user_id`, `role_id`, `assigned_at`)
VALUES (1, 1, 1, TIMESTAMP '2026-01-01 09:10:00');

INSERT INTO `user_role` (`user_role_id`, `user_id`, `role_id`, `assigned_at`)
VALUES (2, 2, 2, TIMESTAMP '2026-01-01 09:11:00');

INSERT INTO `user_role` (`user_role_id`, `user_id`, `role_id`, `assigned_at`)
VALUES (3, 3, 3, TIMESTAMP '2026-01-01 09:12:00');

INSERT INTO `user_role` (`user_role_id`, `user_id`, `role_id`, `assigned_at`)
VALUES (4, 4, 4, TIMESTAMP '2026-01-01 09:13:00');

INSERT INTO `user_role` (`user_role_id`, `user_id`, `role_id`, `assigned_at`)
VALUES (5, 6, 5, TIMESTAMP '2026-01-01 09:14:00');

INSERT INTO `user_role` (`user_role_id`, `user_id`, `role_id`, `assigned_at`)
VALUES (6, 5, 6, TIMESTAMP '2026-01-01 09:14:30');

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (1, 1, 1);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (2, 3, 1);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (3, 3, 2);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (4, 3, 3);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (5, 4, 1);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (6, 4, 2);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (7, 5, 1);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (8, 5, 2);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (9, 5, 3);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (10, 3, 4);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (11, 3, 7);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (12, 3, 8);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (13, 3, 9);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (14, 4, 4);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (15, 5, 4);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (16, 5, 5);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (17, 5, 6);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (18, 5, 7);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (19, 5, 8);

INSERT INTO `role_permission` (`role_permission_id`, `role_id`, `permission_id`)
VALUES (20, 5, 9);

INSERT INTO `workflow_definition` (`workflow_definition_id`, `process_key`, `process_name`, `module_type`, `bpmn_xml`, `state_machine_rules_json`, `version_no`, `status`, `created_at`, `updated_at`)
VALUES (1, 'project_apply', 'Project Application Flow', 'APPLICATION', '<bpmn />', '{"initial":"SUBMITTED"}', 1, 'ACTIVE', TIMESTAMP '2026-01-01 09:15:00', TIMESTAMP '2026-01-01 09:15:00');

INSERT INTO `workflow_node` (`workflow_node_id`, `workflow_definition_id`, `node_id`, `node_name`, `node_type`, `state_code`, `lane_name`, `responsible_actor_code`, `responsible_actor_name`, `candidate_role_code`, `operation_mode`, `represented_actor_code`, `represented_actor_name`, `created_at`)
VALUES (1, 1, 'start_apply', 'Start Apply', 'USER_TASK', 'SUBMITTED', 'Research Office', 'PROJECT_LEADER', 'Project Leader', 'PROJECT_LEADER', 'MANUAL', 'APPLICANT', 'Applicant', TIMESTAMP '2026-01-01 09:20:00');

INSERT INTO `material_type` (`material_type_id`, `material_type_code`, `material_type_name`, `module_type`, `allowed_file_types`, `max_file_size_mb`, `enabled`, `created_at`, `updated_at`)
VALUES (1, 'APP_FORM', 'Application Form', 'APPLICATION', 'pdf,docx', 20, TRUE, TIMESTAMP '2026-01-01 09:25:00', TIMESTAMP '2026-01-01 09:25:00');

INSERT INTO `workflow_node_material_requirement` (`requirement_id`, `workflow_node_id`, `material_type_id`, `requirement_timing`, `required`, `min_count`, `max_count`, `usage_type`, `validator_key`, `description`)
VALUES (1, 1, 1, 'BEFORE_SUBMIT', TRUE, 1, 1, 'INPUT', 'default', 'Application form is required');

INSERT INTO `workflow_node_document_config` (`document_config_id`, `workflow_node_id`, `document_type_code`, `document_type_name`, `generate_timing`, `template_code`, `snapshot_schema_json`, `snapshot_view_name`, `output_material_type_id`, `required`, `enabled`)
VALUES (1, 1, 'NOTICE', 'Approval Notice', 'AFTER_APPROVE', 'notice-template', '{"type":"object"}', 'v_state_record_context', 1, TRUE, TRUE);

INSERT INTO `project` (`project_id`, `project_code`, `project_name`, `leader_user_id`, `dept_id`, `project_type`, `project_level`, `approved_amount`, `start_date`, `end_date`, `lifecycle_stage`, `created_at`, `updated_at`)
VALUES (1, 'PRJ-001', 'Quantum Sensor Research', 1, 1, 'RESEARCH', 'NATIONAL', 100000.00, DATE '2026-01-01', DATE '2026-12-31', 'APPLYING', TIMESTAMP '2026-01-01 09:30:00', TIMESTAMP '2026-01-01 09:30:00');

INSERT INTO `project` (`project_id`, `project_code`, `project_name`, `leader_user_id`, `dept_id`, `project_type`, `project_level`, `approved_amount`, `start_date`, `end_date`, `lifecycle_stage`, `created_at`, `updated_at`)
VALUES (2, 'PRJ-002', 'Industrial Robotics Upgrade', 6, 2, 'ENGINEERING', 'PROVINCIAL', 50000.00, DATE '2026-03-01', DATE '2026-12-31', 'REVIEWING', TIMESTAMP '2026-01-01 09:31:00', TIMESTAMP '2026-01-01 09:31:00');

INSERT INTO `project_module_instance` (`module_instance_id`, `project_id`, `module_type`, `workflow_definition_id`, `started_at`, `finished_at`, `created_at`, `updated_at`)
VALUES (1, 1, 'APPLICATION', 1, TIMESTAMP '2026-01-01 09:35:00', NULL, TIMESTAMP '2026-01-01 09:35:00', TIMESTAMP '2026-01-01 09:35:00');

INSERT INTO `module_state_record` (`state_record_id`, `module_instance_id`, `seq`, `round_no`, `event_type`, `from_state`, `to_state`, `from_node_id`, `to_node_id`, `result`, `summary`, `payload_json`, `created_at`)
VALUES (1, 1, 1, 1, 'SUBMIT', 'DRAFT', 'SUBMITTED', 'draft_apply', 'start_apply', 'PASS', 'Initial submission', '{"submitted":true}', TIMESTAMP '2026-01-01 09:40:00');

INSERT INTO `state_record_remark` (`remark_id`, `state_record_id`, `participant_user_id`, `participant_role_id`, `participant_type`, `action_type`, `result`, `is_operator`, `score`, `remark_content`, `is_final`, `sort_no`, `created_at`)
VALUES (1, 1, 1, 1, 'USER', 'SUBMIT', 'PASS', TRUE, 98.50, 'Submission completed', TRUE, 1, TIMESTAMP '2026-01-01 09:45:00');

INSERT INTO `task_instance` (`task_instance_id`, `module_instance_id`, `node_id`, `state_code`, `assignee_user_id`, `candidate_role_code`, `task_status`, `round_no`, `created_at`, `claimed_at`, `completed_at`, `deadline_at`)
VALUES (1, 1, 'start_apply', 'SUBMITTED', 1, 'PROJECT_LEADER', 'OPEN', 1, TIMESTAMP '2026-01-01 09:50:00', TIMESTAMP '2026-01-01 09:51:00', NULL, TIMESTAMP '2026-01-10 18:00:00');

INSERT INTO `project_member` (`project_member_id`, `project_id`, `user_id`, `member_role`, `responsibility`, `joined_at`)
VALUES (1, 1, 1, 'LEADER', 'Overall responsibility', TIMESTAMP '2026-01-01 09:55:00');

INSERT INTO `project_member` (`project_member_id`, `project_id`, `user_id`, `member_role`, `responsibility`, `joined_at`)
VALUES (2, 2, 6, 'LEADER', 'Project direction', TIMESTAMP '2026-01-01 09:56:00');

INSERT INTO `project_application` (`application_id`, `project_id`, `application_title`, `is_limited_project`, `submitted_at`, `approved_at`, `application_summary`, `created_at`, `updated_at`)
VALUES (1, 1, 'Quantum Sensor Application', FALSE, TIMESTAMP '2026-01-01 10:00:00', NULL, 'Application summary', TIMESTAMP '2026-01-01 10:00:00', TIMESTAMP '2026-01-01 10:00:00');

INSERT INTO `project_contract` (`contract_id`, `project_id`, `contract_code`, `contract_name`, `contract_amount`, `contract_start_date`, `contract_end_date`, `seal_status`, `submitted_at`, `signed_at`, `archived_at`, `created_at`, `updated_at`)
VALUES (1, 1, 'CT-001', 'Quantum Sensor Contract', 80000.00, DATE '2026-02-01', DATE '2026-11-30', 'PENDING', TIMESTAMP '2026-02-01 09:00:00', NULL, NULL, TIMESTAMP '2026-02-01 09:00:00', TIMESTAMP '2026-02-01 09:00:00');

INSERT INTO `project_acceptance` (`acceptance_id`, `project_id`, `submitted_at`, `completed_at`, `certificate_no`, `conclusion`, `created_at`, `updated_at`)
VALUES (1, 1, TIMESTAMP '2026-12-01 09:00:00', NULL, 'ACC-001', 'Pending final review', TIMESTAMP '2026-12-01 09:00:00', TIMESTAMP '2026-12-01 09:00:00');

INSERT INTO `material` (`material_id`, `project_id`, `material_type_id`, `created_by`, `created_at`)
VALUES (1, 1, 1, 1, TIMESTAMP '2026-01-01 10:05:00');

INSERT INTO `material_version` (`material_version_id`, `material_id`, `version_no`, `file_name`, `file_url`, `file_hash`, `uploaded_by`, `uploaded_at`, `is_current`)
VALUES (1, 1, 1, 'application-form-v1.pdf', '/files/application-form-v1.pdf', 'hash-v1', 1, TIMESTAMP '2026-01-01 10:06:00', TRUE);

INSERT INTO `state_record_material` (`record_material_id`, `state_record_id`, `remark_id`, `material_version_id`, `material_usage`, `is_required`, `linked_at`)
VALUES (1, 1, 1, 1, 'SUBMIT_ATTACHMENT', TRUE, TIMESTAMP '2026-01-01 10:07:00');

INSERT INTO `process_document` (`document_id`, `module_instance_id`, `generated_state_record_id`, `document_type_code`, `document_no`, `document_title`, `document_status`, `snapshot_json`, `generated_at`)
VALUES (1, 1, 1, 'NOTICE', 'DOC-001', 'Submission Notice', 'GENERATED', '{"status":"generated"}', TIMESTAMP '2026-01-01 10:08:00');

INSERT INTO `process_document_file` (`document_file_id`, `document_id`, `material_version_id`, `file_purpose`, `is_main_file`)
VALUES (1, 1, 1, 'ATTACHMENT', TRUE);
