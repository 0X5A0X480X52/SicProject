CREATE TABLE IF NOT EXISTS `project_role_grant` (
    `project_role_grant_id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `module_type` VARCHAR(64) NULL,
    `grant_role_code` VARCHAR(128) NOT NULL,
    `grantee_user_id` BIGINT NOT NULL,
    `granted_by_user_id` BIGINT NULL,
    `grant_scope` VARCHAR(32) NULL,
    `round_no` INT NULL,
    `task_node_id` VARCHAR(128) NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `effective_from` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `effective_to` DATETIME NULL,
    `grant_reason` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`project_role_grant_id`),
    KEY `idx_prg_project_status` (`project_id`, `status`),
    KEY `idx_prg_grantee_status` (`grantee_user_id`, `status`),
    KEY `idx_prg_role_status` (`grant_role_code`, `status`),
    KEY `idx_prg_created_at` (`created_at`),
    KEY `idx_prg_matching` (`project_id`, `grant_role_code`, `grantee_user_id`, `status`, `module_type`, `round_no`, `task_node_id`),
    CONSTRAINT `fk_prg_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_prg_grantee`
        FOREIGN KEY (`grantee_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_prg_granted_by`
        FOREIGN KEY (`granted_by_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目级授权表';

CREATE TABLE IF NOT EXISTS `project_role_grant_log` (
    `grant_log_id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_role_grant_id` BIGINT NOT NULL,
    `action_type` VARCHAR(32) NOT NULL,
    `operator_user_id` BIGINT NULL,
    `before_snapshot_json` LONGTEXT NULL,
    `after_snapshot_json` LONGTEXT NULL,
    `remark` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`grant_log_id`),
    KEY `idx_prg_log_grant` (`project_role_grant_id`),
    KEY `idx_prg_log_operator` (`operator_user_id`),
    KEY `idx_prg_log_created_at` (`created_at`),
    CONSTRAINT `fk_prg_log_grant`
        FOREIGN KEY (`project_role_grant_id`) REFERENCES `project_role_grant` (`project_role_grant_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_prg_log_operator`
        FOREIGN KEY (`operator_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目级授权审计日志表';

CREATE TABLE IF NOT EXISTS `admin_operation_log` (
    `admin_operation_log_id` BIGINT NOT NULL AUTO_INCREMENT,
    `scope_type` VARCHAR(32) NOT NULL,
    `action_type` VARCHAR(32) NOT NULL,
    `operator_user_id` BIGINT NULL,
    `target_user_id` BIGINT NULL,
    `project_id` BIGINT NULL,
    `role_code` VARCHAR(128) NULL,
    `permission_code` VARCHAR(128) NULL,
    `grant_type` VARCHAR(128) NULL,
    `before_snapshot_json` LONGTEXT NULL,
    `after_snapshot_json` LONGTEXT NULL,
    `remark` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`admin_operation_log_id`),
    KEY `idx_aol_scope_action` (`scope_type`, `action_type`),
    KEY `idx_aol_operator` (`operator_user_id`),
    KEY `idx_aol_target` (`target_user_id`),
    KEY `idx_aol_project` (`project_id`),
    KEY `idx_aol_created_at` (`created_at`),
    CONSTRAINT `fk_aol_operator`
        FOREIGN KEY (`operator_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT `fk_aol_target`
        FOREIGN KEY (`target_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT `fk_aol_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限中心操作审计日志表';
