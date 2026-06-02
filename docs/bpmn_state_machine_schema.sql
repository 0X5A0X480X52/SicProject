-- ============================================================
-- 基于 BPMN 状态机的科研项目流程管理系统
-- 数据库表与通用视图创建脚本
--
-- 口径说明：
-- 1. 本脚本以“第一个设计文档/合并版主设计”为准。
-- 2. 如与 SSE 修正版存在冲突，以第一个设计文档为准。
-- 3. module_state_record 只记录状态迁移事实；操作人与意见统一放入 state_record_remark。
-- 4. task_instance 单独表示当前待办，不混入 module_state_record。
-- 5. SSE 为运行时轻量通知机制，本脚本不额外创建消息队列表。
--
-- 数据库方言：MySQL 8.0+
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 可选：按需启用数据库创建语句
CREATE DATABASE IF NOT EXISTS research_project_management
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE research_project_management;

-- ============================================================
-- 0. 删除旧视图与旧表
-- ============================================================

DROP VIEW IF EXISTS `v_material_context`;
DROP VIEW IF EXISTS `v_state_record_context`;
DROP VIEW IF EXISTS `v_module_runtime_context`;
DROP VIEW IF EXISTS `v_workflow_node_config`;
DROP VIEW IF EXISTS `v_user_role_detail`;

DROP TABLE IF EXISTS `process_document_file`;
DROP TABLE IF EXISTS `process_document`;
DROP TABLE IF EXISTS `state_record_material`;
DROP TABLE IF EXISTS `material_version`;
DROP TABLE IF EXISTS `material`;
DROP TABLE IF EXISTS `project_acceptance`;
DROP TABLE IF EXISTS `project_contract`;
DROP TABLE IF EXISTS `project_application`;
DROP TABLE IF EXISTS `project_member`;
DROP TABLE IF EXISTS `task_instance`;
DROP TABLE IF EXISTS `state_record_remark`;
DROP TABLE IF EXISTS `module_state_record`;
DROP TABLE IF EXISTS `workflow_node_document_config`;
DROP TABLE IF EXISTS `workflow_node_material_requirement`;
DROP TABLE IF EXISTS `project_module_instance`;
DROP TABLE IF EXISTS `project`;
DROP TABLE IF EXISTS `material_type`;
DROP TABLE IF EXISTS `workflow_node`;
DROP TABLE IF EXISTS `workflow_definition`;
DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `permission`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `app_user`;
DROP TABLE IF EXISTS `department`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 第一部分：权限管理表
-- ============================================================

CREATE TABLE `department` (
    `dept_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门 ID',
    `dept_code` VARCHAR(64) NOT NULL COMMENT '部门编码',
    `dept_name` VARCHAR(255) NOT NULL COMMENT '部门名称',
    `parent_dept_id` BIGINT NULL COMMENT '上级部门 ID',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`dept_id`),
    UNIQUE KEY `uk_department_code` (`dept_code`),
    KEY `idx_department_parent` (`parent_dept_id`),
    CONSTRAINT `fk_department_parent`
        FOREIGN KEY (`parent_dept_id`) REFERENCES `department` (`dept_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织机构表';

CREATE TABLE `app_user` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username` VARCHAR(128) NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
    `real_name` VARCHAR(128) NOT NULL COMMENT '真实姓名',
    `dept_id` BIGINT NULL COMMENT '所属部门 ID',
    `phone` VARCHAR(64) NULL COMMENT '手机号',
    `email` VARCHAR(255) NULL COMMENT '邮箱',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_app_user_username` (`username`),
    KEY `idx_app_user_dept` (`dept_id`),
    KEY `idx_app_user_enabled` (`enabled`),
    CONSTRAINT `fk_app_user_department`
        FOREIGN KEY (`dept_id`) REFERENCES `department` (`dept_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE `role` (
    `role_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色 ID',
    `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    `role_name` VARCHAR(128) NOT NULL COMMENT '角色名称',
    `role_desc` TEXT NULL COMMENT '角色描述',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (`role_id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_role_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE `permission` (
    `permission_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限 ID',
    `permission_code` VARCHAR(128) NOT NULL COMMENT '权限编码',
    `permission_name` VARCHAR(128) NOT NULL COMMENT '权限名称',
    `permission_type` VARCHAR(32) NOT NULL COMMENT '权限类型：MENU / API / BUTTON',
    `permission_desc` TEXT NULL COMMENT '权限说明',
    PRIMARY KEY (`permission_id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`),
    KEY `idx_permission_type` (`permission_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

CREATE TABLE `user_role` (
    `user_role_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `role_id` BIGINT NOT NULL COMMENT '角色 ID',
    `assigned_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    PRIMARY KEY (`user_role_id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_role_role` (`role_id`),
    CONSTRAINT `fk_user_role_user`
        FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_user_role_role`
        FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关系表';

CREATE TABLE `role_permission` (
    `role_permission_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id` BIGINT NOT NULL COMMENT '角色 ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (`role_permission_id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_permission_permission` (`permission_id`),
    CONSTRAINT `fk_role_permission_role`
        FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_role_permission_permission`
        FOREIGN KEY (`permission_id`) REFERENCES `permission` (`permission_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系表';

-- ============================================================
-- 第二部分：BPMN 状态机表
-- ============================================================

CREATE TABLE `workflow_definition` (
    `workflow_definition_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程定义 ID',
    `process_key` VARCHAR(128) NOT NULL COMMENT '流程编码',
    `process_name` VARCHAR(255) NOT NULL COMMENT '流程名称',
    `module_type` VARCHAR(64) NOT NULL COMMENT '模块类型',
    `bpmn_xml` LONGTEXT NOT NULL COMMENT 'BPMN 原始 XML',
    `state_machine_rules_json` JSON NULL COMMENT '状态迁移规则 JSON',
    `version_no` INT NOT NULL COMMENT '流程版本号',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / ACTIVE / DISABLED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`workflow_definition_id`),
    UNIQUE KEY `uk_workflow_definition_process_version` (`process_key`, `version_no`),
    KEY `idx_workflow_definition_module_status` (`module_type`, `status`),
    KEY `idx_workflow_definition_process_status` (`process_key`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义表';

CREATE TABLE `workflow_node` (
    `workflow_node_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点记录 ID',
    `workflow_definition_id` BIGINT NOT NULL COMMENT '所属流程定义 ID',
    `node_id` VARCHAR(128) NOT NULL COMMENT 'BPMN 节点 ID',
    `node_name` VARCHAR(255) NOT NULL COMMENT 'BPMN 节点名称',
    `node_type` VARCHAR(64) NOT NULL COMMENT 'START_EVENT / USER_TASK / SERVICE_TASK / GATEWAY / END_EVENT',
    `state_code` VARCHAR(128) NULL COMMENT '稳定状态编码，Gateway 可为空',
    `lane_name` VARCHAR(128) NULL COMMENT 'BPMN 泳道名称',
    `responsible_actor_code` VARCHAR(128) NULL COMMENT '业务责任主体编码',
    `responsible_actor_name` VARCHAR(255) NULL COMMENT '业务责任主体名称',
    `candidate_role_code` VARCHAR(64) NULL COMMENT '系统候选操作角色编码',
    `operation_mode` VARCHAR(32) NULL COMMENT 'SELF_OPERATE / PROXY_INPUT / SYSTEM_AUTO',
    `represented_actor_code` VARCHAR(128) NULL COMMENT '代录代表主体编码',
    `represented_actor_name` VARCHAR(255) NULL COMMENT '代录代表主体名称',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`workflow_node_id`),
    UNIQUE KEY `uk_workflow_node_node` (`workflow_definition_id`, `node_id`),
    UNIQUE KEY `uk_workflow_node_state` (`workflow_definition_id`, `state_code`),
    KEY `idx_workflow_node_candidate_role` (`candidate_role_code`),
    KEY `idx_workflow_node_type` (`node_type`),
    CONSTRAINT `fk_workflow_node_definition`
        FOREIGN KEY (`workflow_definition_id`) REFERENCES `workflow_definition` (`workflow_definition_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPMN 节点表';

-- material_type 被节点材料要求和单据输出配置引用，因此提前创建。
CREATE TABLE `material_type` (
    `material_type_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '材料类型 ID',
    `material_type_code` VARCHAR(128) NOT NULL COMMENT '材料类型编码',
    `material_type_name` VARCHAR(255) NOT NULL COMMENT '材料类型名称',
    `module_type` VARCHAR(64) NULL COMMENT '所属模块，可空',
    `allowed_file_types` VARCHAR(255) NULL COMMENT '允许文件类型，例如 pdf,docx,jpg,png',
    `max_file_size_mb` INT NULL COMMENT '最大文件大小 MB',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`material_type_id`),
    UNIQUE KEY `uk_material_type_code` (`material_type_code`),
    KEY `idx_material_type_module` (`module_type`),
    KEY `idx_material_type_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料类型表';

CREATE TABLE `workflow_node_material_requirement` (
    `requirement_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_node_id` BIGINT NOT NULL COMMENT '所属 BPMN 节点 ID',
    `material_type_id` BIGINT NOT NULL COMMENT '材料类型 ID',
    `requirement_timing` VARCHAR(32) NOT NULL COMMENT 'BEFORE_ENTER / BEFORE_SUBMIT / AFTER_COMPLETE',
    `required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必填',
    `min_count` INT NOT NULL DEFAULT 0 COMMENT '最小数量',
    `max_count` INT NULL COMMENT '最大数量',
    `usage_type` VARCHAR(64) NULL COMMENT 'SUBMITTED_FILE / PROOF_FILE / SIGNED_FILE / REVIEW_ATTACHMENT',
    `validator_key` VARCHAR(128) NULL COMMENT '自定义材料校验器 key',
    `description` TEXT NULL COMMENT '说明',
    PRIMARY KEY (`requirement_id`),
    UNIQUE KEY `uk_node_material_requirement` (`workflow_node_id`, `material_type_id`, `requirement_timing`, `usage_type`),
    KEY `idx_node_material_requirement_type` (`material_type_id`),
    KEY `idx_node_material_requirement_timing` (`requirement_timing`),
    CONSTRAINT `fk_node_material_requirement_node`
        FOREIGN KEY (`workflow_node_id`) REFERENCES `workflow_node` (`workflow_node_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_node_material_requirement_type`
        FOREIGN KEY (`material_type_id`) REFERENCES `material_type` (`material_type_id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点材料要求表';

CREATE TABLE `workflow_node_document_config` (
    `document_config_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `workflow_node_id` BIGINT NOT NULL COMMENT '所属 BPMN 节点 ID',
    `document_type_code` VARCHAR(128) NOT NULL COMMENT '单据类型编码',
    `document_type_name` VARCHAR(255) NOT NULL COMMENT '单据名称',
    `generate_timing` VARCHAR(32) NOT NULL COMMENT 'ON_NODE_COMPLETE / ON_PROCESS_END / MANUAL_GENERATE',
    `template_code` VARCHAR(128) NULL COMMENT '模板编码',
    `snapshot_schema_json` JSON NULL COMMENT '快照结构配置',
    `snapshot_view_name` VARCHAR(128) NULL COMMENT '可选，单据数据源视图名',
    `output_material_type_id` BIGINT NULL COMMENT '生成文件对应的材料类型 ID',
    `required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必须生成',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (`document_config_id`),
    UNIQUE KEY `uk_node_document_config` (`workflow_node_id`, `document_type_code`, `generate_timing`),
    KEY `idx_node_document_config_output_type` (`output_material_type_id`),
    KEY `idx_node_document_config_enabled` (`enabled`),
    CONSTRAINT `fk_node_document_config_node`
        FOREIGN KEY (`workflow_node_id`) REFERENCES `workflow_node` (`workflow_node_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_node_document_config_material_type`
        FOREIGN KEY (`output_material_type_id`) REFERENCES `material_type` (`material_type_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点单据输出配置表';

-- ============================================================
-- 第三部分：业务数据表
-- ============================================================

CREATE TABLE `project` (
    `project_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目 ID',
    `project_code` VARCHAR(128) NULL COMMENT '项目编号',
    `project_name` VARCHAR(255) NOT NULL COMMENT '项目名称',
    `leader_user_id` BIGINT NULL COMMENT '项目负责人用户 ID',
    `dept_id` BIGINT NULL COMMENT '所属二级单位 ID',
    `project_type` VARCHAR(64) NULL COMMENT '项目类型',
    `project_level` VARCHAR(64) NULL COMMENT '项目级别',
    `approved_amount` DECIMAL(18,2) NULL COMMENT '批准经费',
    `start_date` DATE NULL COMMENT '开始时间',
    `end_date` DATE NULL COMMENT '结束时间',
    `lifecycle_stage` VARCHAR(64) NULL COMMENT '项目生命周期阶段',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`project_id`),
    UNIQUE KEY `uk_project_code` (`project_code`),
    KEY `idx_project_leader` (`leader_user_id`),
    KEY `idx_project_dept` (`dept_id`),
    KEY `idx_project_lifecycle_stage` (`lifecycle_stage`),
    CONSTRAINT `fk_project_leader`
        FOREIGN KEY (`leader_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT `fk_project_department`
        FOREIGN KEY (`dept_id`) REFERENCES `department` (`dept_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目主表';

CREATE TABLE `project_module_instance` (
    `module_instance_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块实例 ID',
    `project_id` BIGINT NOT NULL COMMENT '所属项目 ID',
    `module_type` VARCHAR(64) NOT NULL COMMENT '模块类型',
    `workflow_definition_id` BIGINT NOT NULL COMMENT '使用的流程定义版本 ID',
    `started_at` DATETIME NULL COMMENT '开始时间',
    `finished_at` DATETIME NULL COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`module_instance_id`),
    UNIQUE KEY `uk_project_module_instance` (`project_id`, `module_type`),
    KEY `idx_pmi_project_module` (`project_id`, `module_type`),
    KEY `idx_pmi_workflow_definition` (`workflow_definition_id`),
    CONSTRAINT `fk_pmi_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_pmi_workflow_definition`
        FOREIGN KEY (`workflow_definition_id`) REFERENCES `workflow_definition` (`workflow_definition_id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目模块流程实例表';

CREATE TABLE `module_state_record` (
    `state_record_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '状态记录 ID',
    `module_instance_id` BIGINT NOT NULL COMMENT '所属模块实例 ID',
    `seq` INT NOT NULL COMMENT '模块内递增序号',
    `round_no` INT NOT NULL DEFAULT 0 COMMENT '第几轮',
    `event_type` VARCHAR(128) NOT NULL COMMENT '触发事件',
    `from_state` VARCHAR(128) NULL COMMENT '原状态',
    `to_state` VARCHAR(128) NOT NULL COMMENT '新状态',
    `from_node_id` VARCHAR(128) NULL COMMENT '来源 BPMN 节点 ID',
    `to_node_id` VARCHAR(128) NULL COMMENT '目标 BPMN 节点 ID',
    `result` VARCHAR(64) NULL COMMENT '总体结果',
    `summary` VARCHAR(512) NULL COMMENT '状态迁移摘要',
    `payload_json` JSON NULL COMMENT '条件变量、上下文',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`state_record_id`),
    UNIQUE KEY `uk_module_state_record_seq` (`module_instance_id`, `seq`),
    KEY `idx_state_record_instance_seq` (`module_instance_id`, `seq` DESC),
    KEY `idx_state_record_instance_round_seq` (`module_instance_id`, `round_no`, `seq`),
    KEY `idx_state_record_to_state` (`to_state`),
    KEY `idx_state_record_event_type` (`event_type`),
    KEY `idx_state_record_created_at` (`created_at`),
    CONSTRAINT `fk_state_record_module_instance`
        FOREIGN KEY (`module_instance_id`) REFERENCES `project_module_instance` (`module_instance_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='状态迁移事实表';

CREATE TABLE `state_record_remark` (
    `remark_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `state_record_id` BIGINT NOT NULL COMMENT '所属状态记录 ID',
    `participant_user_id` BIGINT NULL COMMENT '参与用户 ID，可空',
    `participant_role_id` BIGINT NULL COMMENT '参与角色 ID，可空',
    `participant_type` VARCHAR(32) NOT NULL COMMENT 'OPERATOR / APPROVER / EXPERT / PROXY_OPERATOR / SYSTEM',
    `action_type` VARCHAR(64) NULL COMMENT 'SUBMIT / APPROVE / RETURN / REVIEW / REGISTER_RESULT',
    `result` VARCHAR(64) NULL COMMENT '个人结果',
    `is_operator` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为实际触发状态迁移的人',
    `score` DECIMAL(10,2) NULL COMMENT '评分，可空',
    `remark_content` TEXT NULL COMMENT '意见内容',
    `is_final` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否最终有效',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`remark_id`),
    KEY `idx_remark_state_record` (`state_record_id`),
    KEY `idx_remark_operator` (`state_record_id`, `is_operator`),
    KEY `idx_remark_participant_user` (`participant_user_id`),
    KEY `idx_remark_participant_role` (`participant_role_id`),
    KEY `idx_remark_type_action` (`participant_type`, `action_type`),
    CONSTRAINT `fk_remark_state_record`
        FOREIGN KEY (`state_record_id`) REFERENCES `module_state_record` (`state_record_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_remark_participant_user`
        FOREIGN KEY (`participant_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT `fk_remark_participant_role`
        FOREIGN KEY (`participant_role_id`) REFERENCES `role` (`role_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='状态记录意见表';

CREATE TABLE `task_instance` (
    `task_instance_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '待办 ID',
    `module_instance_id` BIGINT NOT NULL COMMENT '模块实例 ID',
    `node_id` VARCHAR(128) NOT NULL COMMENT 'BPMN 节点 ID',
    `state_code` VARCHAR(128) NOT NULL COMMENT '状态编码',
    `assignee_user_id` BIGINT NULL COMMENT '明确处理人，可空',
    `candidate_role_code` VARCHAR(64) NULL COMMENT '候选角色编码',
    `task_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CLAIMED / COMPLETED / CANCELED',
    `round_no` INT NOT NULL DEFAULT 0 COMMENT '轮次',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `claimed_at` DATETIME NULL COMMENT '领取时间',
    `completed_at` DATETIME NULL COMMENT '完成时间',
    `deadline_at` DATETIME NULL COMMENT '截止时间',
    PRIMARY KEY (`task_instance_id`),
    KEY `idx_task_instance_module` (`module_instance_id`),
    KEY `idx_task_instance_status` (`task_status`),
    KEY `idx_task_instance_assignee_status` (`assignee_user_id`, `task_status`),
    KEY `idx_task_instance_candidate_status` (`candidate_role_code`, `task_status`),
    KEY `idx_task_instance_deadline` (`deadline_at`),
    CONSTRAINT `fk_task_instance_module`
        FOREIGN KEY (`module_instance_id`) REFERENCES `project_module_instance` (`module_instance_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_task_instance_assignee`
        FOREIGN KEY (`assignee_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待办任务表';

CREATE TABLE `project_member` (
    `project_member_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id` BIGINT NOT NULL COMMENT '项目 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `member_role` VARCHAR(64) NULL COMMENT '项目内角色',
    `responsibility` TEXT NULL COMMENT '分工说明',
    `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`project_member_id`),
    UNIQUE KEY `uk_project_member_user_role` (`project_id`, `user_id`, `member_role`),
    KEY `idx_project_member_user` (`user_id`),
    CONSTRAINT `fk_project_member_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_project_member_user`
        FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

CREATE TABLE `project_application` (
    `application_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '申报 ID',
    `project_id` BIGINT NOT NULL COMMENT '项目 ID',
    `application_title` VARCHAR(255) NULL COMMENT '申报标题',
    `is_limited_project` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否限项',
    `submitted_at` DATETIME NULL COMMENT '提交时间',
    `approved_at` DATETIME NULL COMMENT '批准时间',
    `application_summary` TEXT NULL COMMENT '申报摘要',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`application_id`),
    UNIQUE KEY `uk_project_application_project` (`project_id`),
    CONSTRAINT `fk_project_application_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目申报表';

CREATE TABLE `project_contract` (
    `contract_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '合同 ID',
    `project_id` BIGINT NOT NULL COMMENT '项目 ID',
    `contract_code` VARCHAR(128) NULL COMMENT '合同编号',
    `contract_name` VARCHAR(255) NULL COMMENT '合同名称',
    `contract_amount` DECIMAL(18,2) NULL COMMENT '合同金额',
    `contract_start_date` DATE NULL COMMENT '合同开始日期',
    `contract_end_date` DATE NULL COMMENT '合同结束日期',
    `seal_status` VARCHAR(64) NULL COMMENT '盖章状态',
    `submitted_at` DATETIME NULL COMMENT '提交时间',
    `signed_at` DATETIME NULL COMMENT '签订时间',
    `archived_at` DATETIME NULL COMMENT '归档时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`contract_id`),
    UNIQUE KEY `uk_project_contract_code` (`contract_code`),
    KEY `idx_project_contract_project` (`project_id`),
    CONSTRAINT `fk_project_contract_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纵向合同表';

CREATE TABLE `project_acceptance` (
    `acceptance_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '结题 ID',
    `project_id` BIGINT NOT NULL COMMENT '项目 ID',
    `submitted_at` DATETIME NULL COMMENT '提交时间',
    `completed_at` DATETIME NULL COMMENT '完成时间',
    `certificate_no` VARCHAR(128) NULL COMMENT '结题证书编号',
    `conclusion` TEXT NULL COMMENT '结题结论',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`acceptance_id`),
    UNIQUE KEY `uk_project_acceptance_project` (`project_id`),
    UNIQUE KEY `uk_project_acceptance_certificate` (`certificate_no`),
    CONSTRAINT `fk_project_acceptance_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目结题表';

CREATE TABLE `material` (
    `material_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '材料 ID',
    `project_id` BIGINT NOT NULL COMMENT '项目 ID',
    `material_type_id` BIGINT NOT NULL COMMENT '材料类型 ID',
    `created_by` BIGINT NULL COMMENT '创建人用户 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`material_id`),
    KEY `idx_material_project_type` (`project_id`, `material_type_id`),
    KEY `idx_material_type` (`material_type_id`),
    KEY `idx_material_created_by` (`created_by`),
    CONSTRAINT `fk_material_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_material_type`
        FOREIGN KEY (`material_type_id`) REFERENCES `material_type` (`material_type_id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT `fk_material_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑材料表';

CREATE TABLE `material_version` (
    `material_version_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '材料版本 ID',
    `material_id` BIGINT NOT NULL COMMENT '材料 ID',
    `version_no` INT NOT NULL COMMENT '版本号',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_url` VARCHAR(1024) NOT NULL COMMENT '文件路径',
    `file_hash` VARCHAR(128) NULL COMMENT '文件哈希',
    `uploaded_by` BIGINT NULL COMMENT '上传人用户 ID',
    `uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `is_current` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否当前版本',
    PRIMARY KEY (`material_version_id`),
    UNIQUE KEY `uk_material_version_no` (`material_id`, `version_no`),
    KEY `idx_material_version_current` (`material_id`, `is_current`),
    KEY `idx_material_version_uploaded_by` (`uploaded_by`),
    KEY `idx_material_version_hash` (`file_hash`),
    CONSTRAINT `fk_material_version_material`
        FOREIGN KEY (`material_id`) REFERENCES `material` (`material_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_material_version_uploaded_by`
        FOREIGN KEY (`uploaded_by`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料版本表';

CREATE TABLE `state_record_material` (
    `record_material_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `state_record_id` BIGINT NOT NULL COMMENT '所属状态记录 ID',
    `remark_id` BIGINT NULL COMMENT '可选，关联某条意见',
    `material_version_id` BIGINT NOT NULL COMMENT '材料版本 ID',
    `material_usage` VARCHAR(64) NULL COMMENT '材料用途',
    `is_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必填',
    `linked_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (`record_material_id`),
    KEY `idx_state_record_material_state` (`state_record_id`),
    KEY `idx_state_record_material_remark` (`remark_id`),
    KEY `idx_state_record_material_version` (`material_version_id`),
    CONSTRAINT `fk_state_record_material_state`
        FOREIGN KEY (`state_record_id`) REFERENCES `module_state_record` (`state_record_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_state_record_material_remark`
        FOREIGN KEY (`remark_id`) REFERENCES `state_record_remark` (`remark_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT `fk_state_record_material_version`
        FOREIGN KEY (`material_version_id`) REFERENCES `material_version` (`material_version_id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='状态记录材料关联表';

CREATE TABLE `process_document` (
    `document_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '单据 ID',
    `module_instance_id` BIGINT NOT NULL COMMENT '模块实例 ID',
    `generated_state_record_id` BIGINT NULL COMMENT '由哪次状态记录生成',
    `document_type_code` VARCHAR(128) NOT NULL COMMENT '单据类型编码',
    `document_no` VARCHAR(128) NULL COMMENT '单据编号',
    `document_title` VARCHAR(255) NULL COMMENT '单据标题',
    `document_status` VARCHAR(32) NOT NULL DEFAULT 'GENERATED' COMMENT '单据状态',
    `snapshot_json` JSON NULL COMMENT '生成时快照',
    `generated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`document_id`),
    UNIQUE KEY `uk_process_document_no` (`document_no`),
    KEY `idx_process_document_module` (`module_instance_id`),
    KEY `idx_process_document_state` (`generated_state_record_id`),
    KEY `idx_process_document_type` (`document_type_code`),
    CONSTRAINT `fk_process_document_module`
        FOREIGN KEY (`module_instance_id`) REFERENCES `project_module_instance` (`module_instance_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_process_document_state_record`
        FOREIGN KEY (`generated_state_record_id`) REFERENCES `module_state_record` (`state_record_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正式流程单据表';

CREATE TABLE `process_document_file` (
    `document_file_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `document_id` BIGINT NOT NULL COMMENT '单据 ID',
    `material_version_id` BIGINT NOT NULL COMMENT '文件版本 ID',
    `file_purpose` VARCHAR(64) NULL COMMENT 'PDF / WORD / SCAN / ATTACHMENT',
    `is_main_file` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主文件',
    PRIMARY KEY (`document_file_id`),
    KEY `idx_process_document_file_document` (`document_id`),
    KEY `idx_process_document_file_version` (`material_version_id`),
    CONSTRAINT `fk_process_document_file_document`
        FOREIGN KEY (`document_id`) REFERENCES `process_document` (`document_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_process_document_file_version`
        FOREIGN KEY (`material_version_id`) REFERENCES `material_version` (`material_version_id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正式单据文件关系表';

-- ============================================================
-- 第四部分：通用视图
-- ============================================================

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
LEFT JOIN `department` d
    ON d.`dept_id` = u.`dept_id`
LEFT JOIN `user_role` ur
    ON ur.`user_id` = u.`user_id`
LEFT JOIN `role` r
    ON r.`role_id` = ur.`role_id`
WHERE u.`enabled` = 1;

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
JOIN `workflow_definition` wd
    ON wd.`workflow_definition_id` = wn.`workflow_definition_id`;

CREATE OR REPLACE VIEW `v_module_runtime_context` AS
SELECT
    pmi.`module_instance_id`,
    pmi.`project_id`,
    pmi.`module_type`,
    pmi.`workflow_definition_id`,
    pmi.`started_at`,
    pmi.`finished_at`,
    cs.`current_seq`,
    cs.`current_round_no`,
    cs.`current_state`,
    cs.`current_node_id`,
    cs.`last_event_type`,
    cs.`last_result`,
    cs.`last_summary`,
    cs.`last_transition_time`,
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
LEFT JOIN (
    SELECT
        t.`module_instance_id`,
        t.`seq` AS `current_seq`,
        t.`round_no` AS `current_round_no`,
        t.`to_state` AS `current_state`,
        t.`to_node_id` AS `current_node_id`,
        t.`event_type` AS `last_event_type`,
        t.`result` AS `last_result`,
        t.`summary` AS `last_summary`,
        t.`created_at` AS `last_transition_time`
    FROM (
        SELECT
            r.*,
            ROW_NUMBER() OVER (
                PARTITION BY r.`module_instance_id`
                ORDER BY r.`seq` DESC, r.`state_record_id` DESC
            ) AS rn
        FROM `module_state_record` r
    ) t
    WHERE t.rn = 1
) cs
    ON cs.`module_instance_id` = pmi.`module_instance_id`
LEFT JOIN `v_workflow_node_config` wn
    ON wn.`workflow_definition_id` = pmi.`workflow_definition_id`
   AND wn.`state_code` = cs.`current_state`;

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
    msr.`payload_json`,
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
JOIN `project_module_instance` pmi
    ON pmi.`module_instance_id` = msr.`module_instance_id`
LEFT JOIN `v_workflow_node_config` wn
    ON wn.`workflow_definition_id` = pmi.`workflow_definition_id`
   AND wn.`state_code` = msr.`to_state`
LEFT JOIN `state_record_remark` op
    ON op.`state_record_id` = msr.`state_record_id`
   AND op.`is_operator` = 1
LEFT JOIN `app_user` operator_user
    ON operator_user.`user_id` = op.`participant_user_id`
LEFT JOIN `role` operator_role
    ON operator_role.`role_id` = op.`participant_role_id`;

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
JOIN `material_type` mt
    ON mt.`material_type_id` = m.`material_type_id`
JOIN `material_version` mv
    ON mv.`material_id` = m.`material_id`
LEFT JOIN `app_user` uploader
    ON uploader.`user_id` = mv.`uploaded_by`;

-- ============================================================
-- 脚本结束
-- ============================================================
