-- Full schema for SicProjectIS backend on MySQL 8+.
-- This script creates an empty database schema from scratch.
-- Runtime seed data such as roles, permissions, bootstrap admin, and BPMN workflow assets
-- is initialized by Spring Boot ApplicationRunner components on first application startup.

CREATE DATABASE IF NOT EXISTS `research_project_management`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `research_project_management`;

SET FOREIGN_KEY_CHECKS = 0;
DROP VIEW IF EXISTS `v_material_context`;
DROP VIEW IF EXISTS `v_state_record_context`;
DROP VIEW IF EXISTS `v_module_runtime_context`;
DROP VIEW IF EXISTS `v_workflow_node_config`;
DROP VIEW IF EXISTS `v_user_role_detail`;

DROP TABLE IF EXISTS `process_document_file`;
DROP TABLE IF EXISTS `process_document`;
DROP TABLE IF EXISTS `surplus_funds_return_record`;
DROP TABLE IF EXISTS `project_achievement`;
DROP TABLE IF EXISTS `acceptance_financial_settlement`;
DROP TABLE IF EXISTS `project_application_publicity`;
DROP TABLE IF EXISTS `archive_record`;
DROP TABLE IF EXISTS `submission_record`;
DROP TABLE IF EXISTS `seal_record`;
DROP TABLE IF EXISTS `external_result_record`;
DROP TABLE IF EXISTS `state_record_check_item`;
DROP TABLE IF EXISTS `project_acceptance_ext`;
DROP TABLE IF EXISTS `project_contract_ext`;
DROP TABLE IF EXISTS `project_application_detail`;
DROP TABLE IF EXISTS `project_application_ext`;
DROP TABLE IF EXISTS `notice_record`;
DROP TABLE IF EXISTS `expert_review_score`;
DROP TABLE IF EXISTS `expert_review_assignment`;
DROP TABLE IF EXISTS `expert_review_batch`;
DROP TABLE IF EXISTS `state_record_material`;
DROP TABLE IF EXISTS `material_version`;
DROP TABLE IF EXISTS `material`;
DROP TABLE IF EXISTS `project_acceptance`;
DROP TABLE IF EXISTS `project_contract`;
DROP TABLE IF EXISTS `project_application`;
DROP TABLE IF EXISTS `admin_operation_log`;
DROP TABLE IF EXISTS `project_role_grant_log`;
DROP TABLE IF EXISTS `project_role_grant`;
DROP TABLE IF EXISTS `project_member`;
DROP TABLE IF EXISTS `task_instance`;
DROP TABLE IF EXISTS `state_record_remark`;
DROP TABLE IF EXISTS `module_state_record`;
DROP TABLE IF EXISTS `project_module_instance`;
DROP TABLE IF EXISTS `project`;
DROP TABLE IF EXISTS `workflow_node_document_config`;
DROP TABLE IF EXISTS `workflow_node_material_requirement`;
DROP TABLE IF EXISTS `material_type`;
DROP TABLE IF EXISTS `workflow_node`;
DROP TABLE IF EXISTS `workflow_definition`;
DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `permission`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `app_user`;
DROP TABLE IF EXISTS `department`;

CREATE TABLE `department` (
    `dept_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `dept_code` VARCHAR(64),
    `dept_name` VARCHAR(255),
    `parent_dept_id` BIGINT,
    `enabled` BOOLEAN,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `app_user` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(128),
    `password_hash` VARCHAR(255),
    `real_name` VARCHAR(128),
    `dept_id` BIGINT,
    `phone` VARCHAR(64),
    `email` VARCHAR(255),
    `enabled` BOOLEAN,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `role` (
    `role_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `role_code` VARCHAR(64),
    `role_name` VARCHAR(128),
    `role_desc` LONGTEXT,
    `enabled` BOOLEAN
);

CREATE TABLE `permission` (
    `permission_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `permission_code` VARCHAR(128),
    `permission_name` VARCHAR(128),
    `permission_type` VARCHAR(32),
    `permission_desc` LONGTEXT
);

CREATE TABLE `user_role` (
    `user_role_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT,
    `role_id` BIGINT,
    `assigned_at` DATETIME
);

CREATE TABLE `role_permission` (
    `role_permission_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT,
    `permission_id` BIGINT
);

CREATE TABLE `workflow_definition` (
    `workflow_definition_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `process_key` VARCHAR(128),
    `process_name` VARCHAR(255),
    `module_type` VARCHAR(64),
    `bpmn_xml` LONGTEXT,
    `state_machine_rules_json` LONGTEXT,
    `version_no` INT,
    `status` VARCHAR(32),
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `workflow_node` (
    `workflow_node_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `workflow_definition_id` BIGINT,
    `node_id` VARCHAR(128),
    `node_name` VARCHAR(255),
    `node_type` VARCHAR(64),
    `state_code` VARCHAR(64),
    `lane_name` VARCHAR(128),
    `responsible_actor_code` VARCHAR(128),
    `responsible_actor_name` VARCHAR(128),
    `candidate_role_code` VARCHAR(128),
    `operation_mode` VARCHAR(64),
    `represented_actor_code` VARCHAR(128),
    `represented_actor_name` VARCHAR(128),
    `created_at` DATETIME
);

CREATE TABLE `material_type` (
    `material_type_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `material_type_code` VARCHAR(128),
    `material_type_name` VARCHAR(255),
    `module_type` VARCHAR(64),
    `allowed_file_types` VARCHAR(255),
    `max_file_size_mb` INT,
    `enabled` BOOLEAN,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `workflow_node_material_requirement` (
    `requirement_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `workflow_node_id` BIGINT,
    `material_type_id` BIGINT,
    `requirement_timing` VARCHAR(64),
    `required` BOOLEAN,
    `min_count` INT,
    `max_count` INT,
    `usage_type` VARCHAR(64),
    `validator_key` VARCHAR(128),
    `description` LONGTEXT
);

CREATE TABLE `workflow_node_document_config` (
    `document_config_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `workflow_node_id` BIGINT,
    `document_type_code` VARCHAR(128),
    `document_type_name` VARCHAR(255),
    `generate_timing` VARCHAR(64),
    `template_code` VARCHAR(128),
    `snapshot_schema_json` LONGTEXT,
    `snapshot_view_name` VARCHAR(128),
    `output_material_type_id` BIGINT,
    `required` BOOLEAN,
    `enabled` BOOLEAN
);

CREATE TABLE `project` (
    `project_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_code` VARCHAR(128),
    `project_name` VARCHAR(255),
    `leader_user_id` BIGINT,
    `dept_id` BIGINT,
    `project_type` VARCHAR(64),
    `project_level` VARCHAR(64),
    `approved_amount` DECIMAL(15, 2),
    `start_date` DATE,
    `end_date` DATE,
    `lifecycle_stage` VARCHAR(64),
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `project_module_instance` (
    `module_instance_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT,
    `module_type` VARCHAR(64),
    `workflow_definition_id` BIGINT,
    `started_at` DATETIME,
    `finished_at` DATETIME,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `module_state_record` (
    `state_record_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `module_instance_id` BIGINT,
    `seq` INT,
    `round_no` INT,
    `event_type` VARCHAR(64),
    `from_state` VARCHAR(64),
    `to_state` VARCHAR(64),
    `from_node_id` VARCHAR(128),
    `to_node_id` VARCHAR(128),
    `result` VARCHAR(64),
    `summary` LONGTEXT,
    `created_at` DATETIME
);

CREATE TABLE `state_record_remark` (
    `remark_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `state_record_id` BIGINT,
    `participant_user_id` BIGINT,
    `participant_role_id` BIGINT,
    `participant_type` VARCHAR(64),
    `action_type` VARCHAR(64),
    `result` VARCHAR(64),
    `is_operator` BOOLEAN,
    `score` DECIMAL(8, 2),
    `remark_content` LONGTEXT,
    `is_final` BOOLEAN,
    `sort_no` INT,
    `created_at` DATETIME
);

CREATE TABLE `task_instance` (
    `task_instance_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `module_instance_id` BIGINT,
    `node_id` VARCHAR(128),
    `state_code` VARCHAR(64),
    `assignee_user_id` BIGINT,
    `candidate_role_code` VARCHAR(128),
    `task_status` VARCHAR(64),
    `round_no` INT,
    `created_at` DATETIME,
    `claimed_at` DATETIME,
    `completed_at` DATETIME,
    `deadline_at` DATETIME
);

CREATE TABLE `project_role_grant` (
    `project_role_grant_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT,
    `module_type` VARCHAR(64),
    `grant_role_code` VARCHAR(128),
    `grantee_user_id` BIGINT,
    `granted_by_user_id` BIGINT,
    `grant_scope` VARCHAR(32),
    `round_no` INT,
    `task_node_id` VARCHAR(128),
    `status` VARCHAR(32),
    `effective_from` DATETIME,
    `effective_to` DATETIME,
    `grant_reason` LONGTEXT,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `project_role_grant_log` (
    `grant_log_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_role_grant_id` BIGINT,
    `action_type` VARCHAR(32),
    `operator_user_id` BIGINT,
    `before_snapshot_json` LONGTEXT,
    `after_snapshot_json` LONGTEXT,
    `remark` LONGTEXT,
    `created_at` DATETIME
);

CREATE TABLE `admin_operation_log` (
    `admin_operation_log_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `scope_type` VARCHAR(32),
    `action_type` VARCHAR(32),
    `operator_user_id` BIGINT,
    `target_user_id` BIGINT,
    `project_id` BIGINT,
    `role_code` VARCHAR(128),
    `permission_code` VARCHAR(128),
    `grant_type` VARCHAR(128),
    `before_snapshot_json` LONGTEXT,
    `after_snapshot_json` LONGTEXT,
    `remark` LONGTEXT,
    `created_at` DATETIME
);

CREATE TABLE `project_member` (
    `project_member_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT,
    `user_id` BIGINT,
    `member_role` VARCHAR(64),
    `responsibility` VARCHAR(255),
    `joined_at` DATETIME
);

CREATE TABLE `project_application` (
    `application_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT,
    `application_title` VARCHAR(255),
    `is_limited_project` BOOLEAN NOT NULL DEFAULT FALSE,
    `submitted_at` DATETIME,
    `approved_at` DATETIME,
    `application_summary` LONGTEXT,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `project_contract` (
    `contract_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT,
    `contract_code` VARCHAR(128),
    `contract_name` VARCHAR(255),
    `contract_amount` DECIMAL(15, 2),
    `contract_start_date` DATE,
    `contract_end_date` DATE,
    `seal_status` VARCHAR(64),
    `submitted_at` DATETIME,
    `signed_at` DATETIME,
    `archived_at` DATETIME,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `project_acceptance` (
    `acceptance_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT,
    `submitted_at` DATETIME,
    `completed_at` DATETIME,
    `certificate_no` VARCHAR(128),
    `conclusion` LONGTEXT,
    `created_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `notice_record` (
    `notice_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `module_instance_id` BIGINT, `state_record_id` BIGINT,
    `module_type` VARCHAR(64) NOT NULL, `notice_type` VARCHAR(64) NOT NULL, `notice_title` VARCHAR(255) NOT NULL,
    `notice_no` VARCHAR(128), `publish_unit` VARCHAR(255), `publish_user_id` BIGINT, `publish_time` DATETIME,
    `notice_scope` VARCHAR(255), `target_dept_scope` LONGTEXT, `target_user_scope` LONGTEXT, `project_category` VARCHAR(128),
    `is_limited_project` BOOLEAN, `limit_count` INT, `start_time` DATETIME, `deadline_time` DATETIME,
    `material_requirement_summary` LONGTEXT, `content_summary` LONGTEXT, `remark` LONGTEXT, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `project_application_ext` (
    `application_ext_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `application_id` BIGINT NOT NULL UNIQUE, `project_id` BIGINT NOT NULL,
    `module_instance_id` BIGINT, `application_category` VARCHAR(128), `application_batch_no` VARCHAR(128), `application_notice_id` BIGINT,
    `application_notice_no` VARCHAR(128), `is_limited_project` BOOLEAN, `limit_group` VARCHAR(128), `expected_budget` DECIMAL(14,2),
    `expected_start_date` DATE, `expected_end_date` DATE, `dept_recommend_rank` INT, `dept_recommend_score` DECIMAL(6,2),
    `dept_recommend_result` VARCHAR(64), `science_recommend_rank` INT, `science_recommend_score` DECIMAL(6,2),
    `science_recommend_result` VARCHAR(64), `authority_approval_no` VARCHAR(128), `authority_approval_date` DATE,
    `authority_result` VARCHAR(64), `authority_approved_amount` DECIMAL(14,2), `final_submission_no` VARCHAR(128),
    `final_submission_at` DATETIME, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `project_application_detail` (
    `application_detail_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `application_id` BIGINT NOT NULL UNIQUE, `project_id` BIGINT NOT NULL,
    `research_background` LONGTEXT, `research_objective` LONGTEXT, `research_content` LONGTEXT, `innovation_points` LONGTEXT, `technical_route` LONGTEXT,
    `schedule_plan` LONGTEXT, `budget_description` LONGTEXT, `expected_outcomes` LONGTEXT, `feasibility_analysis` LONGTEXT,
    `risk_analysis` LONGTEXT, `applicant_commitment` LONGTEXT, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `project_contract_ext` (
    `contract_ext_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `contract_id` BIGINT NOT NULL UNIQUE, `project_id` BIGINT NOT NULL,
    `module_instance_id` BIGINT, `contract_source` VARCHAR(128), `party_a_name` VARCHAR(255), `party_a_contact` VARCHAR(128),
    `party_a_phone` VARCHAR(64), `party_b_name` VARCHAR(255), `party_b_contact` VARCHAR(128), `party_b_phone` VARCHAR(64),
    `authority_review_result` VARCHAR(64), `authority_review_date` DATE, `authority_review_opinion` LONGTEXT, `leader_signed_at` DATETIME,
    `school_sealed_at` DATETIME, `authority_sealed_at` DATETIME, `effective_date` DATE, `archive_no` VARCHAR(128),
    `archive_location` VARCHAR(255), `archive_copies` INT, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `project_acceptance_ext` (
    `acceptance_ext_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `acceptance_id` BIGINT NOT NULL UNIQUE, `project_id` BIGINT NOT NULL,
    `module_instance_id` BIGINT, `is_school_level_acceptance` BOOLEAN, `acceptance_type` VARCHAR(128), `acceptance_batch_no` VARCHAR(128),
    `task_completion_rate` DECIMAL(5,2), `paper_count` INT DEFAULT 0, `patent_count` INT DEFAULT 0,
    `software_copyright_count` INT DEFAULT 0, `other_achievement_count` INT DEFAULT 0, `science_review_result` VARCHAR(64),
    `authority_review_result` VARCHAR(64), `authority_review_date` DATE, `authority_file_no` VARCHAR(128),
    `expert_final_score` DECIMAL(6,2), `expert_final_result` VARCHAR(64), `certificate_no` VARCHAR(128),
    `certificate_issue_date` DATE, `surplus_amount` DECIMAL(14,2), `surplus_return_required` BOOLEAN,
    `surplus_return_status` VARCHAR(64), `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `state_record_check_item` (
    `check_item_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `state_record_id` BIGINT NOT NULL, `module_instance_id` BIGINT NOT NULL,
    `node_id` VARCHAR(128), `state_code` VARCHAR(128), `item_code` VARCHAR(128) NOT NULL, `item_name` VARCHAR(255) NOT NULL,
    `item_type` VARCHAR(64) NOT NULL, `item_value` VARCHAR(255), `item_result` VARCHAR(64), `required` BOOLEAN NOT NULL DEFAULT FALSE,
    `passed` BOOLEAN, `remark` LONGTEXT, `sort_no` INT, `created_at` DATETIME NOT NULL
);

CREATE TABLE `external_result_record` (
    `external_result_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `module_instance_id` BIGINT NOT NULL, `state_record_id` BIGINT NOT NULL,
    `module_type` VARCHAR(64) NOT NULL, `result_type` VARCHAR(64) NOT NULL, `external_actor_code` VARCHAR(128),
    `external_actor_name` VARCHAR(255), `external_result` VARCHAR(64) NOT NULL, `external_result_date` DATE,
    `external_file_no` VARCHAR(128), `external_system_no` VARCHAR(128), `approved_amount` DECIMAL(14,2), `effective_date` DATE,
    `summary` LONGTEXT, `registered_by` BIGINT, `registered_at` DATETIME NOT NULL, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `seal_record` (
    `seal_record_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `module_instance_id` BIGINT NOT NULL, `state_record_id` BIGINT NOT NULL,
    `module_type` VARCHAR(64) NOT NULL, `seal_subject` VARCHAR(128) NOT NULL, `seal_type` VARCHAR(64), `seal_reason` LONGTEXT,
    `copy_count` INT, `applicant_user_id` BIGINT, `handled_by` BIGINT, `leader_signed` BOOLEAN NOT NULL DEFAULT FALSE,
    `leader_signed_at` DATETIME, `school_sealed` BOOLEAN NOT NULL DEFAULT FALSE, `school_sealed_at` DATETIME,
    `external_sealed` BOOLEAN NOT NULL DEFAULT FALSE, `external_actor_name` VARCHAR(255), `external_sealed_at` DATETIME,
    `seal_status` VARCHAR(64), `remark` LONGTEXT, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `submission_record` (
    `submission_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `module_instance_id` BIGINT NOT NULL, `state_record_id` BIGINT NOT NULL,
    `module_type` VARCHAR(64) NOT NULL, `submission_type` VARCHAR(64) NOT NULL, `target_actor_code` VARCHAR(128),
    `target_actor_name` VARCHAR(255), `submission_method` VARCHAR(64), `submission_no` VARCHAR(128), `external_system_no` VARCHAR(128),
    `receipt_no` VARCHAR(128), `submitted_by` BIGINT, `submitted_at` DATETIME NOT NULL, `remark` LONGTEXT,
    `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `archive_record` (
    `archive_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `module_instance_id` BIGINT NOT NULL, `state_record_id` BIGINT NOT NULL,
    `module_type` VARCHAR(64) NOT NULL, `archive_type` VARCHAR(64) NOT NULL, `archive_no` VARCHAR(128), `archive_location` VARCHAR(255),
    `paper_copy_count` INT, `electronic_copy_count` INT, `archived_by` BIGINT, `archived_at` DATETIME NOT NULL,
    `archive_status` VARCHAR(64), `remark` LONGTEXT, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `project_application_publicity` (
    `publicity_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `application_id` BIGINT, `project_id` BIGINT NOT NULL,
    `module_instance_id` BIGINT NOT NULL, `state_record_id` BIGINT, `publicity_title` VARCHAR(255) NOT NULL, `publicity_scope` VARCHAR(255),
    `publicity_start_date` DATE NOT NULL, `publicity_end_date` DATE NOT NULL, `recommended_rank` INT, `recommended_reason` LONGTEXT,
    `has_objection` BOOLEAN NOT NULL DEFAULT FALSE, `objection_content` LONGTEXT, `objection_handling_result` VARCHAR(64),
    `objection_handling_comment` LONGTEXT, `publicity_result` VARCHAR(64) NOT NULL, `confirmed_by` BIGINT, `confirmed_at` DATETIME,
    `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `acceptance_financial_settlement` (
    `settlement_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `acceptance_id` BIGINT, `project_id` BIGINT NOT NULL,
    `module_instance_id` BIGINT NOT NULL, `state_record_id` BIGINT, `approved_amount` DECIMAL(14,2), `received_amount` DECIMAL(14,2),
    `spent_amount` DECIMAL(14,2), `surplus_amount` DECIMAL(14,2), `execution_rate` DECIMAL(5,2), `settlement_result` VARCHAR(64),
    `finance_operator_id` BIGINT, `finance_review_comment` LONGTEXT, `settled_at` DATETIME, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `project_achievement` (
    `achievement_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `project_id` BIGINT NOT NULL, `module_instance_id` BIGINT,
    `acceptance_id` BIGINT, `achievement_type` VARCHAR(64) NOT NULL, `achievement_title` VARCHAR(255) NOT NULL, `author_list` LONGTEXT,
    `achievement_level` VARCHAR(128), `publish_or_grant_date` DATE, `proof_material_version_id` BIGINT, `remark` LONGTEXT,
    `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `surplus_funds_return_record` (
    `return_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, `acceptance_id` BIGINT, `project_id` BIGINT NOT NULL,
    `module_instance_id` BIGINT NOT NULL, `state_record_id` BIGINT, `surplus_amount` DECIMAL(14,2) NOT NULL,
    `return_required` BOOLEAN NOT NULL DEFAULT TRUE, `return_account_name` VARCHAR(255), `return_account_no` VARCHAR(128),
    `return_bank_name` VARCHAR(255), `return_status` VARCHAR(64), `returned_amount` DECIMAL(14,2), `returned_at` DATETIME,
    `finance_operator_id` BIGINT, `remark` LONGTEXT, `created_at` DATETIME NOT NULL, `updated_at` DATETIME
);

CREATE TABLE `material` (
    `material_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `project_id` BIGINT,
    `material_type_id` BIGINT,
    `created_by` BIGINT,
    `created_at` DATETIME
);

CREATE TABLE `material_version` (
    `material_version_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `material_id` BIGINT,
    `version_no` INT,
    `file_name` VARCHAR(255),
    `file_url` VARCHAR(255),
    `file_hash` VARCHAR(128),
    `uploaded_by` BIGINT,
    `uploaded_at` DATETIME,
    `is_current` BOOLEAN
);

CREATE TABLE `state_record_material` (
    `record_material_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `state_record_id` BIGINT,
    `remark_id` BIGINT,
    `material_version_id` BIGINT,
    `material_usage` VARCHAR(64),
    `is_required` BOOLEAN,
    `linked_at` DATETIME
);

CREATE TABLE `process_document` (
    `document_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `module_instance_id` BIGINT,
    `generated_state_record_id` BIGINT,
    `document_type_code` VARCHAR(128),
    `document_no` VARCHAR(128),
    `document_title` VARCHAR(255),
    `document_status` VARCHAR(64),
    `snapshot_json` LONGTEXT,
    `generated_at` DATETIME
);

CREATE TABLE `process_document_file` (
    `document_file_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `document_id` BIGINT,
    `material_version_id` BIGINT,
    `file_purpose` VARCHAR(64),
    `is_main_file` BOOLEAN
);

CREATE TABLE `expert_review_batch` (
    `batch_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `module_instance_id` BIGINT NOT NULL,
    `workflow_node_id` BIGINT,
    `state_record_id` BIGINT,
    `review_type` VARCHAR(64) NOT NULL,
    `review_title` VARCHAR(255) NOT NULL,
    `rule_type` VARCHAR(64) NOT NULL,
    `min_expert_count` INT NOT NULL DEFAULT 3,
    `pass_score` DECIMAL(5, 2),
    `recommend_score` DECIMAL(5, 2),
    `remove_highest_lowest` BOOLEAN NOT NULL DEFAULT FALSE,
    `expected_expert_count` INT,
    `submitted_expert_count` INT DEFAULT 0,
    `valid_expert_count` INT DEFAULT 0,
    `highest_score` DECIMAL(5, 2),
    `lowest_score` DECIMAL(5, 2),
    `final_score` DECIMAL(5, 2),
    `final_result` VARCHAR(64),
    `rank_no` INT,
    `summary_comment` LONGTEXT,
    `status` VARCHAR(32) NOT NULL,
    `created_by` BIGINT,
    `created_at` DATETIME NOT NULL,
    `completed_at` DATETIME,
    `updated_at` DATETIME
);

CREATE TABLE `expert_review_assignment` (
    `assignment_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `batch_id` BIGINT NOT NULL,
    `expert_user_id` BIGINT NOT NULL,
    `expert_name` VARCHAR(128),
    `expert_org` VARCHAR(255),
    `expert_title` VARCHAR(128),
    `assigned_at` DATETIME NOT NULL,
    `submitted_at` DATETIME,
    `review_status` VARCHAR(32) NOT NULL,
    `conflict_of_interest` BOOLEAN NOT NULL DEFAULT FALSE,
    `is_valid` BOOLEAN NOT NULL DEFAULT TRUE,
    `total_score` DECIMAL(5, 2),
    `review_result` VARCHAR(64),
    `review_comment` LONGTEXT,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME,
    CONSTRAINT `uk_expert_review_assignment_batch_user` UNIQUE (`batch_id`, `expert_user_id`),
    CONSTRAINT `fk_expert_review_assignment_batch` FOREIGN KEY (`batch_id`) REFERENCES `expert_review_batch` (`batch_id`)
);

CREATE TABLE `expert_review_score` (
    `score_id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `assignment_id` BIGINT NOT NULL,
    `score_item_code` VARCHAR(64) NOT NULL,
    `score_item_name` VARCHAR(128) NOT NULL,
    `weight` DECIMAL(5, 2) NOT NULL DEFAULT 1.00,
    `max_score` DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
    `score_value` DECIMAL(5, 2) NOT NULL,
    `comment` LONGTEXT,
    `created_at` DATETIME NOT NULL,
    CONSTRAINT `fk_expert_review_score_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `expert_review_assignment` (`assignment_id`)
);

CREATE OR REPLACE VIEW `v_user_role_detail` AS
SELECT
    u.`user_id`,
    u.`username`,
    u.`real_name`,
    u.`dept_id`,
    d.`dept_name`,
    r.`role_id`,
    r.`role_code`,
    r.`role_name`
FROM `app_user` u
LEFT JOIN `department` d ON d.`dept_id` = u.`dept_id`
LEFT JOIN `user_role` ur ON ur.`user_id` = u.`user_id`
LEFT JOIN `role` r ON r.`role_id` = ur.`role_id`
WHERE u.`enabled` = TRUE;

CREATE OR REPLACE VIEW `v_workflow_node_config` AS
SELECT
    wn.`workflow_node_id`,
    wn.`workflow_definition_id`,
    wd.`process_key`,
    wd.`process_name`,
    wd.`version_no`,
    wd.`module_type`,
    wn.`node_id`,
    wn.`node_name`,
    wn.`node_type`,
    wn.`state_code`,
    wn.`lane_name`,
    wn.`responsible_actor_code`,
    wn.`responsible_actor_name`,
    wn.`candidate_role_code`,
    wn.`operation_mode`,
    wn.`represented_actor_code`,
    wn.`represented_actor_name`
FROM `workflow_node` wn
JOIN `workflow_definition` wd ON wd.`workflow_definition_id` = wn.`workflow_definition_id`;

CREATE OR REPLACE VIEW `v_module_runtime_context` AS
SELECT
    pmi.`module_instance_id`,
    pmi.`project_id`,
    pmi.`module_type`,
    pmi.`workflow_definition_id`,
    pmi.`started_at`,
    pmi.`finished_at`,
    msr.`seq` AS `current_seq`,
    msr.`round_no` AS `current_round_no`,
    msr.`to_state` AS `current_state`,
    msr.`to_node_id` AS `current_node_id`,
    msr.`event_type` AS `last_event_type`,
    msr.`result` AS `last_result`,
    msr.`summary` AS `last_summary`,
    msr.`created_at` AS `last_transition_time`,
    wn.`workflow_node_id` AS `current_workflow_node_id`,
    wn.`node_name` AS `current_node_name`,
    wn.`node_type` AS `current_node_type`,
    wn.`lane_name` AS `current_lane_name`,
    wn.`responsible_actor_code` AS `current_responsible_actor_code`,
    wn.`responsible_actor_name` AS `current_responsible_actor_name`,
    wn.`candidate_role_code` AS `current_candidate_role_code`,
    wn.`operation_mode` AS `current_operation_mode`,
    wn.`represented_actor_code` AS `current_represented_actor_code`,
    wn.`represented_actor_name` AS `current_represented_actor_name`
FROM `project_module_instance` pmi
LEFT JOIN `module_state_record` msr
    ON msr.`module_instance_id` = pmi.`module_instance_id`
   AND msr.`seq` = (
       SELECT MAX(inner_msr.`seq`)
       FROM `module_state_record` inner_msr
       WHERE inner_msr.`module_instance_id` = pmi.`module_instance_id`
   )
LEFT JOIN `v_workflow_node_config` wn
    ON wn.`workflow_definition_id` = pmi.`workflow_definition_id`
   AND wn.`state_code` = msr.`to_state`;

CREATE OR REPLACE VIEW `v_state_record_context` AS
SELECT
    msr.`state_record_id`,
    msr.`module_instance_id`,
    pmi.`project_id`,
    pmi.`module_type`,
    pmi.`workflow_definition_id`,
    msr.`seq`,
    msr.`round_no`,
    msr.`event_type`,
    msr.`from_state`,
    msr.`to_state`,
    msr.`from_node_id`,
    msr.`to_node_id`,
    msr.`result`,
    msr.`summary`,
    msr.`created_at` AS `transition_time`,
    wn.`workflow_node_id` AS `to_workflow_node_id`,
    wn.`node_name` AS `to_node_name`,
    wn.`node_type` AS `to_node_type`,
    wn.`lane_name` AS `to_lane_name`,
    wn.`responsible_actor_name` AS `to_responsible_actor_name`,
    wn.`operation_mode` AS `to_operation_mode`,
    wn.`represented_actor_name` AS `to_represented_actor_name`,
    op.`remark_id` AS `operator_remark_id`,
    op.`participant_user_id` AS `operator_user_id`,
    operator_user.`real_name` AS `operator_name`,
    op.`participant_role_id` AS `operator_role_id`,
    operator_role.`role_code` AS `operator_role_code`,
    operator_role.`role_name` AS `operator_role_name`,
    op.`participant_type` AS `operator_participant_type`,
    op.`action_type` AS `operator_action_type`,
    op.`remark_content` AS `operator_remark`
FROM `module_state_record` msr
JOIN `project_module_instance` pmi ON pmi.`module_instance_id` = msr.`module_instance_id`
LEFT JOIN `v_workflow_node_config` wn
    ON wn.`workflow_definition_id` = pmi.`workflow_definition_id`
   AND wn.`state_code` = msr.`to_state`
LEFT JOIN `state_record_remark` op
    ON op.`state_record_id` = msr.`state_record_id`
   AND op.`is_operator` = TRUE
LEFT JOIN `app_user` operator_user ON operator_user.`user_id` = op.`participant_user_id`
LEFT JOIN `role` operator_role ON operator_role.`role_id` = op.`participant_role_id`;

CREATE OR REPLACE VIEW `v_material_context` AS
SELECT
    m.`material_id`,
    m.`project_id`,
    mt.`material_type_id`,
    mt.`material_type_code`,
    mt.`material_type_name`,
    mt.`module_type`,
    mt.`allowed_file_types`,
    mt.`max_file_size_mb`,
    mv.`material_version_id`,
    mv.`version_no`,
    mv.`file_name`,
    mv.`file_url`,
    mv.`file_hash`,
    mv.`uploaded_by`,
    uploader.`real_name` AS `uploaded_by_name`,
    mv.`uploaded_at`,
    mv.`is_current`
FROM `material` m
JOIN `material_type` mt ON mt.`material_type_id` = m.`material_type_id`
JOIN `material_version` mv ON mv.`material_id` = m.`material_id`
LEFT JOIN `app_user` uploader ON uploader.`user_id` = mv.`uploaded_by`;

SET FOREIGN_KEY_CHECKS = 1;
