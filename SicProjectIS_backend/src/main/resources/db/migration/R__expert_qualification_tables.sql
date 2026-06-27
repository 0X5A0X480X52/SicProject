CREATE TABLE IF NOT EXISTS `expert_qualification_application` (
    `application_id` BIGINT NOT NULL AUTO_INCREMENT,
    `applicant_user_id` BIGINT NOT NULL,
    `applicant_dept_id` BIGINT NULL,
    `specialty` VARCHAR(255) NOT NULL,
    `professional_title` VARCHAR(128) NULL,
    `application_reason` TEXT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `dept_reviewer_user_id` BIGINT NULL,
    `dept_review_opinion` TEXT NULL,
    `dept_reviewed_at` DATETIME NULL,
    `science_reviewer_user_id` BIGINT NULL,
    `science_review_opinion` TEXT NULL,
    `science_reviewed_at` DATETIME NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`application_id`),
    KEY `idx_eqa_applicant` (`applicant_user_id`),
    KEY `idx_eqa_dept_status` (`applicant_dept_id`, `status`),
    KEY `idx_eqa_status` (`status`),
    KEY `idx_eqa_created_at` (`created_at`),
    CONSTRAINT `fk_eqa_applicant`
        FOREIGN KEY (`applicant_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT `fk_eqa_dept`
        FOREIGN KEY (`applicant_dept_id`) REFERENCES `department` (`dept_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT `fk_eqa_dept_reviewer`
        FOREIGN KEY (`dept_reviewer_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT `fk_eqa_science_reviewer`
        FOREIGN KEY (`science_reviewer_user_id`) REFERENCES `app_user` (`user_id`)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家资格申请表';
