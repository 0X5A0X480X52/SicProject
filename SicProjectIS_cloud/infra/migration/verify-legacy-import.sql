-- Read-only row-count and orphan verification for the legacy cutover.
-- This script raises SQLSTATE 45000 if any required comparison fails.

SET NAMES utf8mb4;
USE sic_legacy;
DROP TEMPORARY TABLE IF EXISTS migration_verification;
CREATE TEMPORARY TABLE migration_verification (
    check_name VARCHAR(100) PRIMARY KEY,
    source_count BIGINT NOT NULL,
    target_count BIGINT NOT NULL
);

INSERT INTO migration_verification VALUES
('iam.department',(SELECT COUNT(*) FROM sic_legacy.department),(SELECT COUNT(*) FROM sic_iam.department)),
('iam.user',(SELECT COUNT(*) FROM sic_legacy.app_user),(SELECT COUNT(*) FROM sic_iam.app_user)),
('iam.role',(SELECT COUNT(*) FROM sic_legacy.role),(SELECT COUNT(*) FROM sic_iam.app_role)),
('iam.permission',(SELECT COUNT(*) FROM sic_legacy.permission),(SELECT COUNT(*) FROM sic_iam.app_permission)),
('iam.user_role',(SELECT COUNT(DISTINCT user_id,role_id) FROM sic_legacy.user_role),(SELECT COUNT(*) FROM sic_iam.user_role)),
('iam.role_permission',(SELECT COUNT(DISTINCT role_id,permission_id) FROM sic_legacy.role_permission),(SELECT COUNT(*) FROM sic_iam.role_permission)),
('iam.expert_qualification',(SELECT COUNT(*) FROM sic_legacy.expert_qualification_application),(SELECT COUNT(*) FROM sic_iam.expert_qualification)),
('project.project',(SELECT COUNT(*) FROM sic_legacy.project),(SELECT COUNT(*) FROM sic_project.project)),
('project.member',(SELECT COUNT(*) FROM sic_legacy.project_member),(SELECT COUNT(*) FROM sic_project.project_member)),
('project.grant',(SELECT COUNT(*) FROM sic_legacy.project_role_grant),(SELECT COUNT(*) FROM sic_project.project_access_grant)),
('project.admin_log',
 (SELECT (SELECT COUNT(*) FROM sic_legacy.admin_operation_log)+(SELECT COUNT(*) FROM sic_legacy.project_role_grant_log)),
 (SELECT COUNT(*) FROM sic_project.admin_operation_log)),
('project.user_snapshot',(SELECT COUNT(*) FROM sic_legacy.app_user),(SELECT COUNT(*) FROM sic_project.user_snapshot)),
('workflow.definition',(SELECT COUNT(*) FROM sic_legacy.workflow_definition),(SELECT COUNT(*) FROM sic_workflow.workflow_definition WHERE id IN (SELECT workflow_definition_id FROM sic_legacy.workflow_definition))),
('workflow.node',(SELECT COUNT(*) FROM sic_legacy.workflow_node),(SELECT COUNT(*) FROM sic_workflow.workflow_node_config WHERE workflow_definition_id IN (SELECT workflow_definition_id FROM sic_legacy.workflow_definition))),
('workflow.module',(SELECT COUNT(*) FROM sic_legacy.project_module_instance),(SELECT COUNT(*) FROM sic_workflow.module_instance)),
('workflow.state_record',(SELECT COUNT(*) FROM sic_legacy.module_state_record),(SELECT COUNT(*) FROM sic_workflow.state_record)),
('workflow.task',(SELECT COUNT(*) FROM sic_legacy.task_instance),(SELECT COUNT(*) FROM sic_workflow.workflow_task)),
('workflow.application',(SELECT COUNT(*) FROM sic_legacy.project_application),(SELECT COUNT(*) FROM sic_workflow.project_application)),
('workflow.contract',(SELECT COUNT(*) FROM sic_legacy.project_contract),(SELECT COUNT(*) FROM sic_workflow.contract_form)),
('workflow.acceptance',(SELECT COUNT(*) FROM sic_legacy.project_acceptance),(SELECT COUNT(*) FROM sic_workflow.acceptance_form)),
('workflow.review_batch',(SELECT COUNT(*) FROM sic_legacy.expert_review_batch),(SELECT COUNT(*) FROM sic_workflow.expert_review_batch)),
('workflow.review_assignment',(SELECT COUNT(*) FROM sic_legacy.expert_review_assignment),(SELECT COUNT(*) FROM sic_workflow.expert_review_assignment)),
('workflow.review_score',(SELECT COUNT(*) FROM sic_legacy.expert_review_score),(SELECT COUNT(*) FROM sic_workflow.expert_review_score)),
('workflow.notice',(SELECT COUNT(*) FROM sic_legacy.notice_record),(SELECT COUNT(*) FROM sic_workflow.notice_record)),
('workflow.financial_settlement',(SELECT COUNT(*) FROM sic_legacy.acceptance_financial_settlement),(SELECT COUNT(*) FROM sic_workflow.acceptance_financial_settlement)),
('workflow.achievement',(SELECT COUNT(*) FROM sic_legacy.project_achievement),(SELECT COUNT(*) FROM sic_workflow.project_achievement)),
('workflow.user_snapshot',(SELECT COUNT(*) FROM sic_legacy.app_user),(SELECT COUNT(*) FROM sic_workflow.user_snapshot)),
('workflow.project_snapshot',(SELECT COUNT(*) FROM sic_legacy.project),(SELECT COUNT(*) FROM sic_workflow.project_snapshot)),
('workflow.node_form_history',
 (SELECT (SELECT COUNT(*) FROM sic_legacy.state_record_check_item)
       +(SELECT COUNT(*) FROM sic_legacy.external_result_record)
       +(SELECT COUNT(*) FROM sic_legacy.seal_record)
       +(SELECT COUNT(*) FROM sic_legacy.submission_record)
       +(SELECT COUNT(*) FROM sic_legacy.archive_record)
       +(SELECT COUNT(*) FROM sic_legacy.project_application_publicity)
       +(SELECT COUNT(*) FROM sic_legacy.acceptance_financial_settlement)
       +(SELECT COUNT(*) FROM sic_legacy.project_achievement WHERE module_instance_id IS NOT NULL)
       +(SELECT COUNT(*) FROM sic_legacy.surplus_funds_return_record)
       +(SELECT COUNT(*) FROM sic_legacy.notice_record WHERE module_instance_id IS NOT NULL)
       +(SELECT COUNT(*) FROM sic_legacy.expert_review_batch)),
 (SELECT COUNT(*) FROM sic_workflow.node_form)),
('material.type',(SELECT COUNT(*) FROM sic_legacy.material_type),(SELECT COUNT(*) FROM sic_material.material_type)),
('material.material',(SELECT COUNT(*) FROM sic_legacy.material),(SELECT COUNT(*) FROM sic_material.material)),
('material.version',(SELECT COUNT(*) FROM sic_legacy.material_version),(SELECT COUNT(*) FROM sic_material.material_version)),
('material.generated_document',(SELECT COUNT(*) FROM sic_legacy.process_document),(SELECT COUNT(*) FROM sic_material.generated_document)),
('material.file_manifest',(SELECT COUNT(*) FROM sic_legacy.migration_file_manifest),
 (SELECT COUNT(*) FROM sic_material.material_version WHERE object_key LIKE 'legacy/material-versions/%')),
('workflow.material_reference',
 (SELECT COUNT(*) FROM (SELECT sr.module_instance_id,sr.seq,rm.material_version_id
    FROM sic_legacy.state_record_material rm
    JOIN sic_legacy.module_state_record sr ON sr.state_record_id=rm.state_record_id
    GROUP BY sr.module_instance_id,sr.seq,rm.material_version_id) expected_references),
 (SELECT COUNT(*) FROM sic_workflow.workflow_material_reference));

SELECT check_name,source_count,target_count,
       CASE WHEN source_count=target_count THEN 'OK' ELSE 'MISMATCH' END AS result
  FROM migration_verification ORDER BY check_name;

DROP TEMPORARY TABLE IF EXISTS migration_orphan_verification;
CREATE TEMPORARY TABLE migration_orphan_verification (
    check_name VARCHAR(100) PRIMARY KEY,
    orphan_count BIGINT NOT NULL
);

INSERT INTO migration_orphan_verification VALUES
('iam.user.department',(SELECT COUNT(*) FROM sic_iam.app_user u LEFT JOIN sic_iam.department d ON d.id=u.dept_id WHERE u.dept_id IS NOT NULL AND d.id IS NULL)),
('iam.user_role.user',(SELECT COUNT(*) FROM sic_iam.user_role x LEFT JOIN sic_iam.app_user u ON u.id=x.user_id WHERE u.id IS NULL)),
('iam.user_role.role',(SELECT COUNT(*) FROM sic_iam.user_role x LEFT JOIN sic_iam.app_role r ON r.id=x.role_id WHERE r.id IS NULL)),
('project.owner',(SELECT COUNT(*) FROM sic_project.project p LEFT JOIN sic_iam.app_user u ON u.id=p.owner_user_id WHERE u.id IS NULL)),
('project.member.project',(SELECT COUNT(*) FROM sic_project.project_member m LEFT JOIN sic_project.project p ON p.id=m.project_id WHERE p.id IS NULL)),
('project.member.user',(SELECT COUNT(*) FROM sic_project.project_member m LEFT JOIN sic_iam.app_user u ON u.id=m.user_id WHERE u.id IS NULL)),
('project.grant.project',(SELECT COUNT(*) FROM sic_project.project_access_grant g LEFT JOIN sic_project.project p ON p.id=g.project_id WHERE p.id IS NULL)),
('project.grant.user',(SELECT COUNT(*) FROM sic_project.project_access_grant g LEFT JOIN sic_iam.app_user u ON u.id=g.grantee_user_id WHERE u.id IS NULL)),
('workflow.module.project',(SELECT COUNT(*) FROM sic_workflow.module_instance m LEFT JOIN sic_project.project p ON p.id=m.project_id WHERE p.id IS NULL)),
('workflow.module.definition',(SELECT COUNT(*) FROM sic_workflow.module_instance m LEFT JOIN sic_workflow.workflow_definition d ON d.id=m.definition_id WHERE m.definition_id IS NOT NULL AND d.id IS NULL)),
('workflow.state.module',(SELECT COUNT(*) FROM sic_workflow.state_record r LEFT JOIN sic_workflow.module_instance m ON m.id=r.module_instance_id WHERE m.id IS NULL)),
('workflow.task.module',(SELECT COUNT(*) FROM sic_workflow.workflow_task t LEFT JOIN sic_workflow.module_instance m ON m.id=t.module_instance_id WHERE m.id IS NULL)),
('workflow.form.module',(SELECT COUNT(*) FROM sic_workflow.node_form f LEFT JOIN sic_workflow.module_instance m ON m.id=f.module_instance_id WHERE m.id IS NULL)),
('workflow.review.module',(SELECT COUNT(*) FROM sic_workflow.expert_review_batch b LEFT JOIN sic_workflow.module_instance m ON m.id=b.module_instance_id WHERE m.id IS NULL)),
('workflow.review.assignment',(SELECT COUNT(*) FROM sic_workflow.expert_review_assignment a LEFT JOIN sic_workflow.expert_review_batch b ON b.id=a.batch_id WHERE b.id IS NULL)),
('workflow.review.score',(SELECT COUNT(*) FROM sic_workflow.expert_review_score s LEFT JOIN sic_workflow.expert_review_assignment a ON a.id=s.assignment_id WHERE a.id IS NULL)),
('material.material.project',(SELECT COUNT(*) FROM sic_material.material m LEFT JOIN sic_project.project p ON p.id=m.project_id WHERE p.id IS NULL)),
('material.version.material',(SELECT COUNT(*) FROM sic_material.material_version v LEFT JOIN sic_material.material m ON m.id=v.material_id WHERE m.id IS NULL)),
('workflow.material.version',(SELECT COUNT(*) FROM sic_workflow.workflow_material_reference r LEFT JOIN sic_material.material_version v ON v.id=r.material_version_id WHERE v.id IS NULL));

SELECT check_name,orphan_count,CASE WHEN orphan_count=0 THEN 'OK' ELSE 'ORPHAN' END AS result
  FROM migration_orphan_verification ORDER BY check_name;

DELIMITER //
CREATE PROCEDURE sic_legacy.assert_cutover_verified()
BEGIN
    IF EXISTS (SELECT 1 FROM migration_verification WHERE source_count<>target_count) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy import row-count verification failed';
    END IF;
    IF EXISTS (SELECT 1 FROM migration_orphan_verification WHERE orphan_count<>0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy import orphan verification failed';
    END IF;
    IF EXISTS (SELECT 1 FROM sic_workflow.module_instance m
                LEFT JOIN sic_workflow.state_record r
                  ON r.module_instance_id=m.id AND r.seq=m.current_seq
               WHERE m.current_seq>0 AND r.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A module current sequence has no matching state record';
    END IF;
    IF EXISTS (SELECT 1 FROM sic_material.material_version
                WHERE CHAR_LENGTH(sha256)<>64 OR size_bytes<0 OR object_key='') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migrated material metadata is invalid';
    END IF;
END//
DELIMITER ;

CALL sic_legacy.assert_cutover_verified();
DROP PROCEDURE sic_legacy.assert_cutover_verified;

SELECT 'database verification complete' AS status,
       (SELECT COUNT(*) FROM migration_verification) AS row_count_checks,
       (SELECT COUNT(*) FROM migration_orphan_verification) AS orphan_checks;
