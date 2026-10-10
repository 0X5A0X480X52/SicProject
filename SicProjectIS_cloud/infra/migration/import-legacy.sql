-- One-shot import from the legacy monolith schema (sic_legacy) into the five
-- service-owned schemas. Run as MySQL root while all business services are stopped.
-- Flyway must already have created the destination schemas and tables.

SET NAMES utf8mb4;
SET @previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

DELIMITER //
CREATE PROCEDURE sic_legacy.assert_cutover_ready()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = 'sic_legacy' AND table_name = 'app_user') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sic_legacy is not a valid legacy dump';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema IN ('sic_iam','sic_project','sic_workflow','sic_material','sic_notification')
           AND table_name IN ('flyway_schema_history')) <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Run all destination Flyway migrations before importing';
    END IF;
    IF (SELECT COUNT(*) FROM sic_project.project) > 0
       OR (SELECT COUNT(*) FROM sic_workflow.module_instance) > 0
       OR (SELECT COUNT(*) FROM sic_material.material) > 0
       OR (SELECT COUNT(*) FROM sic_iam.app_user) > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Destination business tables are not empty';
    END IF;
    IF (SELECT COUNT(*) FROM sic_legacy.material_version) <>
       (SELECT COUNT(*) FROM sic_legacy.migration_file_manifest) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Run migration-tool prepare-files before the SQL import';
    END IF;
    IF EXISTS (SELECT 1 FROM sic_legacy.app_user GROUP BY username HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.role GROUP BY role_code HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.permission GROUP BY permission_code HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.project GROUP BY project_code HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.project_member GROUP BY project_id,user_id HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.material_type GROUP BY material_type_code HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.workflow_definition
                   GROUP BY COALESCE(NULLIF(module_type,''),process_key),COALESCE(version_no,1)
                  HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.workflow_node
                   GROUP BY workflow_definition_id,node_id HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM sic_legacy.material_version
                   GROUP BY material_id,version_no HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy unique business keys contain duplicates';
    END IF;
    IF EXISTS (SELECT 1 FROM sic_legacy.app_user
                WHERE username IS NULL OR username='' OR password_hash IS NULL OR password_hash='')
       OR EXISTS (SELECT 1 FROM sic_legacy.project
                   WHERE project_code IS NULL OR project_code='' OR project_name IS NULL OR project_name=''
                      OR leader_user_id IS NULL)
       OR EXISTS (SELECT 1 FROM sic_legacy.workflow_definition
                   WHERE bpmn_xml IS NULL OR bpmn_xml='' OR COALESCE(NULLIF(module_type,''),process_key) IS NULL)
       OR EXISTS (SELECT 1 FROM sic_legacy.material m
                   LEFT JOIN sic_legacy.material_type mt ON mt.material_type_id=m.material_type_id
                  WHERE mt.material_type_code IS NULL OR mt.material_type_code='') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy required business fields are missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sic_legacy.project_application a
         WHERE COALESCE((SELECT e.module_instance_id FROM sic_legacy.project_application_ext e
                          WHERE e.application_id=a.application_id LIMIT 1),
                        (SELECT MIN(m.module_instance_id) FROM sic_legacy.project_module_instance m
                          WHERE m.project_id=a.project_id AND m.module_type='APPLICATION')) IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A legacy application has no APPLICATION module instance';
    END IF;
    IF EXISTS (SELECT 1 FROM sic_legacy.project_contract c
                WHERE NOT EXISTS (SELECT 1 FROM sic_legacy.project_contract_ext e
                                   WHERE e.contract_id=c.contract_id AND e.module_instance_id IS NOT NULL)
                  AND NOT EXISTS (SELECT 1 FROM sic_legacy.project_module_instance m
                                  WHERE m.project_id=c.project_id AND m.module_type='CONTRACT'))
       OR EXISTS (SELECT 1 FROM sic_legacy.project_acceptance a
                   WHERE NOT EXISTS (SELECT 1 FROM sic_legacy.project_acceptance_ext e
                                      WHERE e.acceptance_id=a.acceptance_id AND e.module_instance_id IS NOT NULL)
                     AND NOT EXISTS (SELECT 1 FROM sic_legacy.project_module_instance m
                                     WHERE m.project_id=a.project_id AND m.module_type='ACCEPTANCE')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A legacy contract or acceptance has no matching module instance';
    END IF;
    IF EXISTS (SELECT 1 FROM sic_legacy.process_document d
                WHERE NOT EXISTS (SELECT 1 FROM sic_legacy.process_document_file f
                                  JOIN sic_legacy.migration_file_manifest mf
                                    ON mf.material_version_id=f.material_version_id
                                  WHERE f.document_id=d.document_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A legacy generated document has no migrated file';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sic_legacy.process_document d
        LEFT JOIN sic_legacy.module_state_record sr ON sr.state_record_id=d.generated_state_record_id
        GROUP BY d.module_instance_id,COALESCE(sr.seq,0),d.document_type_code HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy generated documents collide on the new idempotency key';
    END IF;
END//
DELIMITER ;

CALL sic_legacy.assert_cutover_ready();
DROP PROCEDURE sic_legacy.assert_cutover_ready;

START TRANSACTION;

-- Remove only Flyway/bootstrap seed data. The guard above prevents replacement
-- of a destination that already contains business data.
DELETE FROM sic_iam.role_permission;
DELETE FROM sic_iam.user_role;
DELETE FROM sic_iam.expert_qualification;
DELETE FROM sic_iam.app_permission;
DELETE FROM sic_iam.app_role;
DELETE FROM sic_iam.app_user;
DELETE FROM sic_iam.department;

DELETE FROM sic_workflow.workflow_transition_config;
DELETE FROM sic_workflow.workflow_node_config;
DELETE FROM sic_workflow.workflow_definition;

-- IAM -----------------------------------------------------------------------
INSERT INTO sic_iam.department(id,parent_id,name,code,enabled,created_at,updated_at)
SELECT dept_id,parent_dept_id,COALESCE(NULLIF(dept_name,''),CONCAT('Department ',dept_id)),
       COALESCE(NULLIF(dept_code,''),CONCAT('LEGACY_DEPT_',dept_id)),COALESCE(enabled,TRUE),
       COALESCE(created_at,CURRENT_TIMESTAMP(6)),COALESCE(updated_at,created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.department;

INSERT INTO sic_iam.app_user(id,username,password_hash,real_name,email,phone,dept_id,enabled,
                             auth_version,created_at,updated_at)
SELECT user_id,username,password_hash,COALESCE(NULLIF(real_name,''),username),email,phone,dept_id,
       COALESCE(enabled,TRUE),1,COALESCE(created_at,CURRENT_TIMESTAMP(6)),
       COALESCE(updated_at,created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.app_user;

INSERT INTO sic_iam.app_role(id,code,name,description,enabled,built_in)
SELECT role_id,role_code,COALESCE(NULLIF(role_name,''),role_code),LEFT(role_desc,255),
       COALESCE(enabled,TRUE),role_code IN
       ('SYSTEM_ADMIN','SCIENCE_ADMIN','DEPT_ADMIN','PROJECT_LEADER','EXPERT','FINANCE_ADMIN')
  FROM sic_legacy.role;

INSERT INTO sic_iam.app_permission(id,code,name,permission_group,description)
SELECT permission_id,permission_code,COALESCE(NULLIF(permission_name,''),permission_code),
       COALESCE(NULLIF(permission_type,''),'API'),LEFT(permission_desc,255)
  FROM sic_legacy.permission;

INSERT INTO sic_iam.user_role(user_id,role_id)
SELECT DISTINCT user_id,role_id FROM sic_legacy.user_role;

INSERT INTO sic_iam.role_permission(role_id,permission_id)
SELECT DISTINCT role_id,permission_id FROM sic_legacy.role_permission;

INSERT INTO sic_iam.expert_qualification(
    id,user_id,applicant_dept_id,specialty,professional_title,application_reason,status,
    dept_reviewer_user_id,dept_review_opinion,dept_review_remark,dept_reviewed_at,
    science_reviewer_user_id,science_review_opinion,science_review_remark,science_reviewed_at,
    created_at,updated_at)
SELECT application_id,applicant_user_id,applicant_dept_id,specialty,professional_title,
       application_reason,status,dept_reviewer_user_id,dept_review_opinion,dept_review_remark,
       dept_reviewed_at,science_reviewer_user_id,science_review_opinion,science_review_remark,
       science_reviewed_at,created_at,COALESCE(updated_at,created_at)
  FROM sic_legacy.expert_qualification_application;

-- Project -------------------------------------------------------------------
INSERT INTO sic_project.project(
    id,project_code,project_name,project_type,project_level,approved_amount,start_date,end_date,
    owner_user_id,owner_name_snapshot,dept_id,dept_name_snapshot,lifecycle_status,business_status,
    version,created_at,updated_at)
SELECT p.project_id,p.project_code,p.project_name,p.project_type,p.project_level,p.approved_amount,
       p.start_date,p.end_date,p.leader_user_id,COALESCE(NULLIF(u.real_name,''),u.username,
       CONCAT('Legacy user ',p.leader_user_id)),p.dept_id,d.dept_name,'ACTIVE',
       COALESCE(NULLIF(p.lifecycle_stage,''),'APPLICATION'),1,
       COALESCE(p.created_at,CURRENT_TIMESTAMP(6)),COALESCE(p.updated_at,p.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project p
  LEFT JOIN sic_legacy.app_user u ON u.user_id=p.leader_user_id
  LEFT JOIN sic_legacy.department d ON d.dept_id=p.dept_id;

INSERT INTO sic_project.project_member(
    id,project_id,user_id,member_role,responsibility,user_name_snapshot,created_at)
SELECT m.project_member_id,m.project_id,m.user_id,COALESCE(NULLIF(m.member_role,''),'MEMBER'),
       m.responsibility,COALESCE(NULLIF(u.real_name,''),u.username,CONCAT('Legacy user ',m.user_id)),
       COALESCE(m.joined_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project_member m
  LEFT JOIN sic_legacy.app_user u ON u.user_id=m.user_id;

INSERT INTO sic_project.project_access_grant(
    id,project_id,grantee_user_id,grantee_name_snapshot,grant_role_code,grant_scope,module_type,
    round_no,task_node_id,status,grant_reason,effective_from,effective_to,granted_by,
    granted_by_name_snapshot,created_at)
SELECT g.project_role_grant_id,g.project_id,g.grantee_user_id,
       COALESCE(NULLIF(gu.real_name,''),gu.username,CONCAT('Legacy user ',g.grantee_user_id)),
       g.grant_role_code,g.grant_scope,g.module_type,g.round_no,g.task_node_id,
       COALESCE(NULLIF(g.status,''),'ACTIVE'),g.grant_reason,
       COALESCE(g.effective_from,g.created_at,CURRENT_TIMESTAMP(6)),g.effective_to,
       COALESCE(g.granted_by_user_id,p.leader_user_id),
       COALESCE(NULLIF(byu.real_name,''),byu.username,NULLIF(owner.real_name,''),owner.username,'Legacy migration'),
       COALESCE(g.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project_role_grant g
  LEFT JOIN sic_legacy.project p ON p.project_id=g.project_id
  LEFT JOIN sic_legacy.app_user gu ON gu.user_id=g.grantee_user_id
  LEFT JOIN sic_legacy.app_user byu ON byu.user_id=g.granted_by_user_id
  LEFT JOIN sic_legacy.app_user owner ON owner.user_id=p.leader_user_id;

INSERT INTO sic_project.admin_operation_log(
    id,scope_type,action_type,project_id,project_name_snapshot,operator_user_id,
    operator_name_snapshot,target_user_id,target_name_snapshot,grant_type,role_code,permission_code,
    before_snapshot,after_snapshot,remark,created_at)
SELECT CONCAT('00000000-0000-0000-0001-',LPAD(l.admin_operation_log_id,12,'0')),
       COALESCE(NULLIF(l.scope_type,''),'SYSTEM'),COALESCE(NULLIF(l.action_type,''),'LEGACY_ACTION'),
       l.project_id,p.project_name,l.operator_user_id,
       COALESCE(NULLIF(op.real_name,''),op.username),l.target_user_id,
       COALESCE(NULLIF(tu.real_name,''),tu.username),l.grant_type,l.role_code,l.permission_code,
       l.before_snapshot_json,l.after_snapshot_json,l.remark,COALESCE(l.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.admin_operation_log l
  LEFT JOIN sic_legacy.project p ON p.project_id=l.project_id
  LEFT JOIN sic_legacy.app_user op ON op.user_id=l.operator_user_id
  LEFT JOIN sic_legacy.app_user tu ON tu.user_id=l.target_user_id;

INSERT INTO sic_project.admin_operation_log(
    id,scope_type,action_type,project_id,project_name_snapshot,operator_user_id,
    operator_name_snapshot,target_user_id,target_name_snapshot,grant_type,role_code,
    before_snapshot,after_snapshot,remark,created_at)
SELECT CONCAT('00000000-0000-0000-0002-',LPAD(l.grant_log_id,12,'0')),
       'PROJECT_GRANT',COALESCE(NULLIF(l.action_type,''),'LEGACY_GRANT_ACTION'),g.project_id,
       p.project_name,l.operator_user_id,COALESCE(NULLIF(op.real_name,''),op.username),
       g.grantee_user_id,COALESCE(NULLIF(target.real_name,''),target.username),g.grant_scope,
       g.grant_role_code,l.before_snapshot_json,l.after_snapshot_json,l.remark,
       COALESCE(l.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project_role_grant_log l
  JOIN sic_legacy.project_role_grant g ON g.project_role_grant_id=l.project_role_grant_id
  LEFT JOIN sic_legacy.project p ON p.project_id=g.project_id
  LEFT JOIN sic_legacy.app_user op ON op.user_id=l.operator_user_id
  LEFT JOIN sic_legacy.app_user target ON target.user_id=g.grantee_user_id;

INSERT INTO sic_project.user_snapshot(
    user_id,username,real_name,dept_id,dept_name,role_codes_json,enabled,source_version,updated_at)
SELECT u.user_id,u.username,COALESCE(NULLIF(u.real_name,''),u.username),u.dept_id,d.dept_name,
       COALESCE((SELECT JSON_ARRAYAGG(r.role_code)
                   FROM sic_legacy.user_role ur JOIN sic_legacy.role r ON r.role_id=ur.role_id
                  WHERE ur.user_id=u.user_id),JSON_ARRAY()),
       COALESCE(u.enabled,TRUE),1,COALESCE(u.updated_at,u.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.app_user u LEFT JOIN sic_legacy.department d ON d.dept_id=u.dept_id;

-- Workflow definitions and read snapshots -----------------------------------
INSERT INTO sic_workflow.workflow_definition(
    id,code,name,version,status,published,bpmn_xml,definition_json,created_at)
SELECT w.workflow_definition_id,COALESCE(NULLIF(w.module_type,''),w.process_key),
       COALESCE(NULLIF(w.process_name,''),w.process_key),COALESCE(w.version_no,1)+1000,
       CASE WHEN COALESCE(w.version_no,1)=(SELECT MAX(COALESCE(w2.version_no,1))
              FROM sic_legacy.workflow_definition w2
             WHERE COALESCE(NULLIF(w2.module_type,''),w2.process_key)=
                   COALESCE(NULLIF(w.module_type,''),w.process_key)) THEN 'ACTIVE' ELSE 'INACTIVE' END,
       COALESCE(w.version_no,1)=(SELECT MAX(COALESCE(w2.version_no,1))
              FROM sic_legacy.workflow_definition w2
             WHERE COALESCE(NULLIF(w2.module_type,''),w2.process_key)=
                   COALESCE(NULLIF(w.module_type,''),w.process_key)),
       w.bpmn_xml,
       CASE WHEN JSON_VALID(w.state_machine_rules_json) THEN CAST(w.state_machine_rules_json AS JSON)
            ELSE JSON_OBJECT('legacyStateMachineRules',w.state_machine_rules_json) END,
       COALESCE(w.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.workflow_definition w;

INSERT INTO sic_workflow.workflow_node_config(
    id,workflow_definition_id,node_id,node_name,node_type,state_code,lane_name,
    responsible_actor_code,responsible_actor_name,candidate_role_code,operation_mode,
    represented_actor_code,represented_actor_name,material_requirements_json,document_configs_json)
SELECT n.workflow_node_id,n.workflow_definition_id,n.node_id,
       COALESCE(NULLIF(n.node_name,''),n.node_id),COALESCE(NULLIF(n.node_type,''),'userTask'),
       n.state_code,n.lane_name,n.responsible_actor_code,n.responsible_actor_name,
       n.candidate_role_code,n.operation_mode,n.represented_actor_code,n.represented_actor_name,
       COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT(
           'requirementId',r.requirement_id,'materialTypeCode',mt.material_type_code,
           'materialTypeName',mt.material_type_name,'requirementTiming',r.requirement_timing,
           'required',r.required,'minCount',r.min_count,'maxCount',r.max_count,
           'usageType',r.usage_type,'validatorKey',r.validator_key,'description',r.description))
         FROM sic_legacy.workflow_node_material_requirement r
         LEFT JOIN sic_legacy.material_type mt ON mt.material_type_id=r.material_type_id
        WHERE r.workflow_node_id=n.workflow_node_id),JSON_ARRAY()),
       COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT(
           'documentConfigId',c.document_config_id,'documentTypeCode',c.document_type_code,
           'documentTypeName',c.document_type_name,'generateTiming',c.generate_timing,
           'templateCode',c.template_code,'snapshotSchemaJson',c.snapshot_schema_json,
           'snapshotViewName',c.snapshot_view_name,'outputMaterialTypeCode',mt.material_type_code,
           'required',c.required,'enabled',c.enabled))
         FROM sic_legacy.workflow_node_document_config c
         LEFT JOIN sic_legacy.material_type mt ON mt.material_type_id=c.output_material_type_id
        WHERE c.workflow_node_id=n.workflow_node_id),JSON_ARRAY())
  FROM sic_legacy.workflow_node n;

INSERT INTO sic_workflow.user_snapshot(user_id,username,real_name,enabled,source_version,updated_at)
SELECT user_id,username,COALESCE(NULLIF(real_name,''),username),COALESCE(enabled,TRUE),1,
       COALESCE(updated_at,created_at,CURRENT_TIMESTAMP(6)) FROM sic_legacy.app_user;

INSERT INTO sic_workflow.project_snapshot(
    project_id,project_code,project_name,lifecycle_stage,source_version,updated_at)
SELECT project_id,project_code,project_name,lifecycle_stage,1,
       COALESCE(updated_at,created_at,CURRENT_TIMESTAMP(6)) FROM sic_legacy.project;

-- Workflow runtime -----------------------------------------------------------
INSERT INTO sic_workflow.module_instance(
    id,saga_id,project_id,started_by_user_id,module_code,definition_id,current_node_id,
    current_state,current_seq,current_round,finished,version,created_at,updated_at)
SELECT m.module_instance_id,NULL,m.project_id,p.leader_user_id,m.module_type,m.workflow_definition_id,
       COALESCE(last_state.to_node_id,(SELECT n.node_id FROM sic_legacy.workflow_node n
          WHERE n.workflow_definition_id=m.workflow_definition_id ORDER BY n.workflow_node_id LIMIT 1),'StartEvent'),
       COALESCE(last_state.to_state,CONCAT(m.module_type,'_STARTED')),COALESCE(last_state.seq,0),
       COALESCE(last_state.round_no,1),m.finished_at IS NOT NULL,COALESCE(last_state.seq,0),
       COALESCE(m.started_at,m.created_at,CURRENT_TIMESTAMP(6)),
       COALESCE(m.updated_at,m.finished_at,m.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project_module_instance m
  LEFT JOIN sic_legacy.project p ON p.project_id=m.project_id
  LEFT JOIN sic_legacy.module_state_record last_state
    ON last_state.state_record_id=(SELECT sr.state_record_id FROM sic_legacy.module_state_record sr
       WHERE sr.module_instance_id=m.module_instance_id ORDER BY sr.seq DESC,sr.state_record_id DESC LIMIT 1);

INSERT INTO sic_workflow.state_record(
    id,module_instance_id,seq,round_no,event_type,from_state,to_state,from_node_id,to_node_id,
    result,summary,actor_user_id,actor_name_snapshot,created_at)
SELECT sr.state_record_id,sr.module_instance_id,sr.seq,COALESCE(sr.round_no,1),
       COALESCE(NULLIF(sr.event_type,''),'LEGACY_TRANSITION'),sr.from_state,
       COALESCE(NULLIF(sr.to_state,''),'UNKNOWN'),sr.from_node_id,
       COALESCE(NULLIF(sr.to_node_id,''),'UnknownNode'),sr.result,
       COALESCE(NULLIF(sr.summary,''),operator_remark.remark_content),
       COALESCE(operator_remark.participant_user_id,p.leader_user_id,0),
       COALESCE(NULLIF(actor.real_name,''),actor.username,NULLIF(owner.real_name,''),owner.username,'Legacy migration'),
       COALESCE(sr.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.module_state_record sr
  JOIN sic_legacy.project_module_instance m ON m.module_instance_id=sr.module_instance_id
  LEFT JOIN sic_legacy.project p ON p.project_id=m.project_id
  LEFT JOIN sic_legacy.state_record_remark operator_remark
    ON operator_remark.remark_id=(SELECT r.remark_id FROM sic_legacy.state_record_remark r
       WHERE r.state_record_id=sr.state_record_id ORDER BY COALESCE(r.is_operator,FALSE) DESC,r.sort_no,r.remark_id LIMIT 1)
  LEFT JOIN sic_legacy.app_user actor ON actor.user_id=operator_remark.participant_user_id
  LEFT JOIN sic_legacy.app_user owner ON owner.user_id=p.leader_user_id;

INSERT INTO sic_workflow.workflow_task(
    id,module_instance_id,node_id,assignee_user_id,candidate_role,status,round_no,created_at,completed_at)
SELECT task_instance_id,module_instance_id,node_id,assignee_user_id,candidate_role_code,
       COALESCE(NULLIF(task_status,''),'OPEN'),COALESCE(round_no,1),
       COALESCE(created_at,CURRENT_TIMESTAMP(6)),completed_at
  FROM sic_legacy.task_instance;

-- Primary application/contract/acceptance documents remain queryable through
-- the unchanged business-data API. JSON keeps all legacy structured fields.
INSERT INTO sic_workflow.project_application(
    id,module_instance_id,project_id,title,limited_project,summary,payload_json,created_at)
SELECT a.application_id,COALESCE(e.module_instance_id,(SELECT MIN(m.module_instance_id)
       FROM sic_legacy.project_module_instance m WHERE m.project_id=a.project_id AND m.module_type='APPLICATION')),
       a.project_id,COALESCE(NULLIF(a.application_title,''),p.project_name),
       COALESCE(e.is_limited_project,a.is_limited_project,FALSE),a.application_summary,
       JSON_OBJECT('applicationId',a.application_id,'projectId',a.project_id,
         'applicationTitle',a.application_title,'isLimitedProject',COALESCE(e.is_limited_project,a.is_limited_project,FALSE),
         'submittedAt',a.submitted_at,'approvedAt',a.approved_at,'applicationSummary',a.application_summary,
         'applicationCategory',e.application_category,'applicationBatchNo',e.application_batch_no,
         'applicationNoticeId',e.application_notice_id,'applicationNoticeNo',e.application_notice_no,
         'limitGroup',e.limit_group,'expectedBudget',e.expected_budget,'expectedStartDate',e.expected_start_date,
         'expectedEndDate',e.expected_end_date,'deptRecommendRank',e.dept_recommend_rank,
         'deptRecommendScore',e.dept_recommend_score,'deptRecommendResult',e.dept_recommend_result,
         'scienceRecommendRank',e.science_recommend_rank,'scienceRecommendScore',e.science_recommend_score,
         'scienceRecommendResult',e.science_recommend_result,'authorityApprovalNo',e.authority_approval_no,
         'authorityApprovalDate',e.authority_approval_date,'authorityResult',e.authority_result,
         'authorityApprovedAmount',e.authority_approved_amount,'finalSubmissionNo',e.final_submission_no,
         'finalSubmissionAt',e.final_submission_at,'researchBackground',d.research_background,
         'researchObjective',d.research_objective,'researchContent',d.research_content,
         'innovationPoints',d.innovation_points,'technicalRoute',d.technical_route,
         'schedulePlan',d.schedule_plan,'budgetDescription',d.budget_description,
         'expectedOutcomes',d.expected_outcomes,'feasibilityAnalysis',d.feasibility_analysis,
         'riskAnalysis',d.risk_analysis,'applicantCommitment',d.applicant_commitment),
       COALESCE(a.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project_application a
  LEFT JOIN sic_legacy.project p ON p.project_id=a.project_id
  LEFT JOIN sic_legacy.project_application_ext e ON e.application_id=a.application_id
  LEFT JOIN sic_legacy.project_application_detail d ON d.application_id=a.application_id;

INSERT INTO sic_workflow.contract_form(id,module_instance_id,round_no,payload_json,created_at)
SELECT c.contract_id,COALESCE(e.module_instance_id,(SELECT MIN(m.module_instance_id)
       FROM sic_legacy.project_module_instance m WHERE m.project_id=c.project_id AND m.module_type='CONTRACT')),
       COALESCE(mi.current_round,1),
       JSON_OBJECT('contractId',c.contract_id,'projectId',c.project_id,'contractCode',c.contract_code,
         'contractName',c.contract_name,'contractAmount',c.contract_amount,'contractStartDate',c.contract_start_date,
         'contractEndDate',c.contract_end_date,'sealStatus',c.seal_status,'submittedAt',c.submitted_at,
         'signedAt',c.signed_at,'archivedAt',c.archived_at,'contractSource',e.contract_source,
         'partyAName',e.party_a_name,'partyAContact',e.party_a_contact,'partyAPhone',e.party_a_phone,
         'partyBName',e.party_b_name,'partyBContact',e.party_b_contact,'partyBPhone',e.party_b_phone,
         'authorityReviewResult',e.authority_review_result,'authorityReviewDate',e.authority_review_date,
         'authorityReviewOpinion',e.authority_review_opinion,'leaderSignedAt',e.leader_signed_at,
         'schoolSealedAt',e.school_sealed_at,'authoritySealedAt',e.authority_sealed_at,
         'effectiveDate',e.effective_date,'archiveNo',e.archive_no,'archiveLocation',e.archive_location,
         'archiveCopies',e.archive_copies),COALESCE(c.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project_contract c
  LEFT JOIN sic_legacy.project_contract_ext e ON e.contract_id=c.contract_id
  LEFT JOIN sic_workflow.module_instance mi ON mi.id=COALESCE(e.module_instance_id,
       (SELECT MIN(m.module_instance_id) FROM sic_legacy.project_module_instance m
         WHERE m.project_id=c.project_id AND m.module_type='CONTRACT'))
 WHERE COALESCE(e.module_instance_id,(SELECT MIN(m.module_instance_id)
       FROM sic_legacy.project_module_instance m WHERE m.project_id=c.project_id AND m.module_type='CONTRACT')) IS NOT NULL;

INSERT INTO sic_workflow.acceptance_form(id,module_instance_id,round_no,payload_json,created_at)
SELECT a.acceptance_id,COALESCE(e.module_instance_id,(SELECT MIN(m.module_instance_id)
       FROM sic_legacy.project_module_instance m WHERE m.project_id=a.project_id AND m.module_type='ACCEPTANCE')),
       COALESCE(mi.current_round,1),
       JSON_OBJECT('acceptanceId',a.acceptance_id,'projectId',a.project_id,'submittedAt',a.submitted_at,
         'completedAt',a.completed_at,'certificateNo',COALESCE(e.certificate_no,a.certificate_no),
         'conclusion',a.conclusion,'isSchoolLevelAcceptance',e.is_school_level_acceptance,
         'acceptanceType',e.acceptance_type,'acceptanceBatchNo',e.acceptance_batch_no,
         'taskCompletionRate',e.task_completion_rate,'paperCount',e.paper_count,'patentCount',e.patent_count,
         'softwareCopyrightCount',e.software_copyright_count,'otherAchievementCount',e.other_achievement_count,
         'scienceReviewResult',e.science_review_result,'authorityReviewResult',e.authority_review_result,
         'authorityReviewDate',e.authority_review_date,'authorityFileNo',e.authority_file_no,
         'expertFinalScore',e.expert_final_score,'expertFinalResult',e.expert_final_result,
         'certificateIssueDate',e.certificate_issue_date,'surplusAmount',e.surplus_amount,
         'surplusReturnRequired',e.surplus_return_required,'surplusReturnStatus',e.surplus_return_status),
       COALESCE(a.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.project_acceptance a
  LEFT JOIN sic_legacy.project_acceptance_ext e ON e.acceptance_id=a.acceptance_id
  LEFT JOIN sic_workflow.module_instance mi ON mi.id=COALESCE(e.module_instance_id,
       (SELECT MIN(m.module_instance_id) FROM sic_legacy.project_module_instance m
         WHERE m.project_id=a.project_id AND m.module_type='ACCEPTANCE'))
 WHERE COALESCE(e.module_instance_id,(SELECT MIN(m.module_instance_id)
       FROM sic_legacy.project_module_instance m WHERE m.project_id=a.project_id AND m.module_type='ACCEPTANCE')) IS NOT NULL;

-- Rich expert review data ----------------------------------------------------
INSERT INTO sic_workflow.expert_review_batch(
    id,module_instance_id,workflow_node_id,round_no,review_type,review_title,rule_type,
    min_expert_count,pass_score,recommend_score,remove_highest_lowest,expected_expert_count,
    submitted_expert_count,valid_expert_count,lowest_score,highest_score,final_score,final_result,
    status,created_by,created_at,updated_at,completed_at)
SELECT batch_id,module_instance_id,workflow_node_id,COALESCE(round_no,1),review_type,review_title,
       COALESCE(NULLIF(rule_type,''),'AVERAGE'),COALESCE(min_expert_count,3),pass_score,recommend_score,
       COALESCE(remove_highest_lowest,FALSE),expected_expert_count,COALESCE(submitted_expert_count,0),
       COALESCE(valid_expert_count,0),lowest_score,highest_score,final_score,final_result,
       COALESCE(NULLIF(status,''),'IN_PROGRESS'),COALESCE(created_by,0),
       COALESCE(created_at,CURRENT_TIMESTAMP(6)),COALESCE(updated_at,created_at,CURRENT_TIMESTAMP(6)),completed_at
  FROM sic_legacy.expert_review_batch;

INSERT INTO sic_workflow.expert_review_assignment(
    id,batch_id,expert_user_id,expert_name_snapshot,expert_org_snapshot,expert_title_snapshot,
    review_status,conflict_of_interest,valid,total_score,review_result,review_comment,
    assigned_at,submitted_at,updated_at)
SELECT assignment_id,batch_id,expert_user_id,expert_name,expert_org,expert_title,
       COALESCE(NULLIF(review_status,''),'ASSIGNED'),COALESCE(conflict_of_interest,FALSE),
       COALESCE(is_valid,TRUE),total_score,review_result,review_comment,
       COALESCE(assigned_at,CURRENT_TIMESTAMP(6)),submitted_at,
       COALESCE(updated_at,submitted_at,assigned_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.expert_review_assignment;

INSERT INTO sic_workflow.expert_review_score(
    id,assignment_id,item_code,item_name,weight,max_score,score_value,comment,created_at)
SELECT score_id,assignment_id,score_item_code,score_item_name,COALESCE(weight,1),
       COALESCE(max_score,100),score_value,comment,COALESCE(created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.expert_review_score;

-- Structured direct tables --------------------------------------------------
INSERT INTO sic_workflow.notice_record(
    id,module_instance_id,state_record_id,module_type,notice_type,notice_title,notice_no,
    publish_unit,publish_user_id,publish_time,notice_scope,target_dept_scope,target_user_scope,
    project_category,limited_project,limit_count,start_time,deadline_time,
    material_requirement_summary,content_summary,remark,created_at,updated_at)
SELECT notice_id,module_instance_id,state_record_id,module_type,notice_type,notice_title,notice_no,
       publish_unit,COALESCE(publish_user_id,0),publish_time,notice_scope,target_dept_scope,
       target_user_scope,project_category,is_limited_project,limit_count,start_time,deadline_time,
       material_requirement_summary,content_summary,remark,created_at,COALESCE(updated_at,created_at)
  FROM sic_legacy.notice_record;

INSERT INTO sic_workflow.acceptance_financial_settlement(
    id,acceptance_id,project_id,module_instance_id,state_record_id,approved_amount,received_amount,
    spent_amount,surplus_amount,execution_rate,settlement_result,finance_operator_id,
    finance_review_comment,settled_at,created_at,updated_at)
SELECT settlement_id,acceptance_id,project_id,module_instance_id,state_record_id,approved_amount,
       COALESCE(received_amount,0),COALESCE(spent_amount,0),COALESCE(surplus_amount,0),
       COALESCE(execution_rate,0),settlement_result,COALESCE(finance_operator_id,0),
       finance_review_comment,COALESCE(settled_at,created_at),created_at,COALESCE(updated_at,created_at)
  FROM sic_legacy.acceptance_financial_settlement;

INSERT INTO sic_workflow.project_achievement(
    id,project_id,module_instance_id,acceptance_id,achievement_type,achievement_title,author_list,
    achievement_level,publish_or_grant_date,proof_material_version_id,remark,created_at,updated_at)
SELECT achievement_id,project_id,module_instance_id,acceptance_id,achievement_type,achievement_title,
       author_list,achievement_level,publish_or_grant_date,proof_material_version_id,remark,
       created_at,COALESCE(updated_at,created_at) FROM sic_legacy.project_achievement;

-- Convert legacy record tables into the generic node_form history model.
INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT c.module_instance_id,CONCAT(CASE m.module_code
         WHEN 'CONTRACT' THEN CASE WHEN c.node_id='ScienceOfficeReviewTask' THEN 'CONTRACT_SCIENCE_REVIEW_FORM' ELSE 'CONTRACT_DEPT_REVIEW_FORM' END
         WHEN 'ACCEPTANCE' THEN CASE WHEN c.node_id='ScienceOfficeReviewTask' THEN 'ACCEPTANCE_SCIENCE_REVIEW_FORM'
              WHEN c.node_id='DeptNotifyLeaderTask' THEN 'ACCEPTANCE_NOTIFY_LEADER_FORM' ELSE 'ACCEPTANCE_DEPT_REVIEW_FORM' END
         ELSE CASE WHEN c.node_id='ScienceOfficeInitialReviewTask' THEN 'SCIENCE_INITIAL_REVIEW_FORM'
              WHEN c.node_id='ScienceOfficeSubmitTask' THEN 'APPLICATION_SUBMISSION_REVIEW_FORM' ELSE 'DEPT_APPLICATION_REVIEW_FORM' END
       END,'-LEGACY-',c.check_item_id),COALESCE(sr.round_no,1),
       JSON_OBJECT('checkItemId',c.check_item_id,'stateRecordId',c.state_record_id,'nodeId',c.node_id,
         'stateCode',c.state_code,'itemCode',c.item_code,'itemName',c.item_name,'itemType',c.item_type,
         'itemValue',c.item_value,'itemResult',c.item_result,'required',c.required,'passed',c.passed,
         'remark',c.remark,'sortNo',c.sort_no),COALESCE(sr.actor_user_id,0),c.created_at
  FROM sic_legacy.state_record_check_item c
  JOIN sic_workflow.module_instance m ON m.id=c.module_instance_id
  LEFT JOIN sic_workflow.state_record sr ON sr.id=c.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT(CASE r.module_type WHEN 'CONTRACT' THEN
         CASE WHEN sr.to_node_id='AuthorityApproveProjectTask' THEN 'PROJECT_APPROVAL_FORM'
              WHEN sr.to_node_id='AuthoritySealTask' THEN 'CONTRACT_AUTHORITY_SEAL_FORM'
              ELSE 'CONTRACT_AUTHORITY_REVIEW_FORM' END
         WHEN 'ACCEPTANCE' THEN 'ACCEPTANCE_AUTHORITY_REVIEW_FORM'
         ELSE 'APPLICATION_AUTHORITY_RESULT_FORM' END,'-LEGACY-',r.external_result_id),
       COALESCE(sr.round_no,1),JSON_OBJECT('externalResultId',r.external_result_id,
         'stateRecordId',r.state_record_id,'moduleType',r.module_type,'resultType',r.result_type,
         'externalActorCode',r.external_actor_code,'externalActorName',r.external_actor_name,
         'externalResult',r.external_result,'externalResultDate',r.external_result_date,
         'externalFileNo',r.external_file_no,'externalSystemNo',r.external_system_no,
         'approvedAmount',r.approved_amount,'effectiveDate',r.effective_date,'summary',r.summary,
         'registeredBy',r.registered_by,'registeredAt',r.registered_at),
       COALESCE(r.registered_by,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.external_result_record r
  LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT(CASE r.module_type WHEN 'CONTRACT' THEN
         CASE WHEN r.seal_subject LIKE '%LEADER%' THEN 'CONTRACT_LEADER_SIGN_FORM' ELSE 'CONTRACT_SCHOOL_SEAL_FORM' END
         WHEN 'ACCEPTANCE' THEN 'ACCEPTANCE_SEAL_FORM' ELSE 'APPLICATION_SEAL_FORM' END,
         '-LEGACY-',r.seal_record_id),COALESCE(sr.round_no,1),
       JSON_OBJECT('sealRecordId',r.seal_record_id,'stateRecordId',r.state_record_id,
         'moduleType',r.module_type,'sealSubject',r.seal_subject,'sealType',r.seal_type,
         'sealReason',r.seal_reason,'copyCount',r.copy_count,'applicantUserId',r.applicant_user_id,
         'handledBy',r.handled_by,'leaderSigned',r.leader_signed,'leaderSignedAt',r.leader_signed_at,
         'schoolSealed',r.school_sealed,'schoolSealedAt',r.school_sealed_at,
         'externalSealed',r.external_sealed,'externalActorName',r.external_actor_name,
         'externalSealedAt',r.external_sealed_at,'sealStatus',r.seal_status,'remark',r.remark),
       COALESCE(r.handled_by,r.applicant_user_id,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.seal_record r LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT(CASE r.module_type WHEN 'ACCEPTANCE' THEN
       'ACCEPTANCE_FINAL_SUBMISSION_FORM' ELSE 'APPLICATION_FINAL_SUBMISSION_FORM' END,
       '-LEGACY-',r.submission_id),COALESCE(sr.round_no,1),
       JSON_OBJECT('submissionId',r.submission_id,'stateRecordId',r.state_record_id,
         'moduleType',r.module_type,'submissionType',r.submission_type,
         'targetActorCode',r.target_actor_code,'targetActorName',r.target_actor_name,
         'submissionMethod',r.submission_method,'submissionNo',r.submission_no,
         'externalSystemNo',r.external_system_no,'receiptNo',r.receipt_no,
         'submittedBy',r.submitted_by,'submittedAt',r.submitted_at,'remark',r.remark),
       COALESCE(r.submitted_by,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.submission_record r LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT('CONTRACT_ARCHIVE_FORM-LEGACY-',r.archive_id),
       COALESCE(sr.round_no,1),JSON_OBJECT('archiveId',r.archive_id,'stateRecordId',r.state_record_id,
         'moduleType',r.module_type,'archiveType',r.archive_type,'archiveNo',r.archive_no,
         'archiveLocation',r.archive_location,'paperCopyCount',r.paper_copy_count,
         'electronicCopyCount',r.electronic_copy_count,'archivedBy',r.archived_by,
         'archivedAt',r.archived_at,'archiveStatus',r.archive_status,'remark',r.remark),
       COALESCE(r.archived_by,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.archive_record r LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT('APPLICATION_PUBLICITY_FORM-LEGACY-',r.publicity_id),
       COALESCE(sr.round_no,1),JSON_OBJECT('publicityId',r.publicity_id,'applicationId',r.application_id,
         'projectId',r.project_id,'stateRecordId',r.state_record_id,'publicityTitle',r.publicity_title,
         'publicityScope',r.publicity_scope,'publicityStartDate',r.publicity_start_date,
         'publicityEndDate',r.publicity_end_date,'recommendedRank',r.recommended_rank,
         'recommendedReason',r.recommended_reason,'hasObjection',r.has_objection,
         'objectionContent',r.objection_content,'objectionHandlingResult',r.objection_handling_result,
         'objectionHandlingComment',r.objection_handling_comment,'publicityResult',r.publicity_result,
         'confirmedBy',r.confirmed_by,'confirmedAt',r.confirmed_at),
       COALESCE(r.confirmed_by,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.project_application_publicity r
  LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT('FINANCIAL_SETTLEMENT_FORM-LEGACY-',r.settlement_id),
       COALESCE(sr.round_no,1),JSON_OBJECT('settlementId',r.settlement_id,
         'acceptanceId',r.acceptance_id,'projectId',r.project_id,'stateRecordId',r.state_record_id,
         'approvedAmount',r.approved_amount,'receivedAmount',r.received_amount,
         'spentAmount',r.spent_amount,'surplusAmount',r.surplus_amount,'executionRate',r.execution_rate,
         'settlementResult',r.settlement_result,'financeOperatorId',r.finance_operator_id,
         'financeReviewComment',r.finance_review_comment,'settledAt',r.settled_at),
       COALESCE(r.finance_operator_id,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.acceptance_financial_settlement r
  LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT('ACHIEVEMENT_LIST_FORM-LEGACY-',r.achievement_id),
       COALESCE(m.current_round,1),JSON_OBJECT('achievementId',r.achievement_id,
         'projectId',r.project_id,'acceptanceId',r.acceptance_id,'achievementType',r.achievement_type,
         'achievementTitle',r.achievement_title,'authorList',r.author_list,
         'achievementLevel',r.achievement_level,'publishOrGrantDate',r.publish_or_grant_date,
         'proofMaterialVersionId',r.proof_material_version_id,'remark',r.remark),
       COALESCE(p.leader_user_id,0),r.created_at
  FROM sic_legacy.project_achievement r
  JOIN sic_workflow.module_instance m ON m.id=r.module_instance_id
  LEFT JOIN sic_legacy.project p ON p.project_id=r.project_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT('SURPLUS_FUNDS_RETURN_FORM-LEGACY-',r.return_id),
       COALESCE(sr.round_no,1),JSON_OBJECT('returnId',r.return_id,'acceptanceId',r.acceptance_id,
         'projectId',r.project_id,'stateRecordId',r.state_record_id,'surplusAmount',r.surplus_amount,
         'returnRequired',r.return_required,'returnAccountName',r.return_account_name,
         'returnAccountNo',r.return_account_no,'returnBankName',r.return_bank_name,
         'returnStatus',r.return_status,'returnedAmount',r.returned_amount,'returnedAt',r.returned_at,
         'financeOperatorId',r.finance_operator_id,'remark',r.remark),
       COALESCE(r.finance_operator_id,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.surplus_funds_return_record r
  LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT r.module_instance_id,CONCAT(CASE r.module_type WHEN 'ACCEPTANCE' THEN 'ACCEPTANCE_NOTICE_FORM'
       ELSE 'APPLICATION_NOTICE_FORM' END,'-LEGACY-',r.notice_id),COALESCE(sr.round_no,1),
       JSON_OBJECT('noticeId',r.notice_id,'stateRecordId',r.state_record_id,'moduleType',r.module_type,
         'noticeType',r.notice_type,'noticeTitle',r.notice_title,'noticeNo',r.notice_no,
         'publishUnit',r.publish_unit,'publishUserId',r.publish_user_id,'publishTime',r.publish_time,
         'noticeScope',r.notice_scope,'targetDeptScope',r.target_dept_scope,
         'targetUserScope',r.target_user_scope,'projectCategory',r.project_category,
         'isLimitedProject',r.is_limited_project,'limitCount',r.limit_count,'startTime',r.start_time,
         'deadlineTime',r.deadline_time,'materialRequirementSummary',r.material_requirement_summary,
         'contentSummary',r.content_summary,'remark',r.remark),COALESCE(r.publish_user_id,sr.actor_user_id,0),r.created_at
  FROM sic_legacy.notice_record r LEFT JOIN sic_workflow.state_record sr ON sr.id=r.state_record_id
 WHERE r.module_instance_id IS NOT NULL;

INSERT INTO sic_workflow.node_form(module_instance_id,node_id,round_no,form_json,submitted_by,created_at)
SELECT b.module_instance_id,CONCAT(CASE m.module_code WHEN 'ACCEPTANCE' THEN
         CASE WHEN n.node_id='ExpertSummaryTask' THEN 'EXPERT_ACCEPTANCE_REVIEW_SUMMARY_FORM'
              WHEN n.node_id='ExpertAssignTask' THEN 'EXPERT_ACCEPTANCE_ASSIGN_FORM' ELSE 'EXPERT_ACCEPTANCE_REVIEW_FORM' END
       ELSE CASE WHEN n.node_id='DeptExpertAssignTask' THEN 'DEPT_EXPERT_ASSIGN_FORM'
              WHEN n.node_id='DeptExpertSummaryTask' THEN 'DEPT_EXPERT_SUMMARY_FORM'
              WHEN n.node_id='ScienceExpertAssignTask' THEN 'SCIENCE_EXPERT_ASSIGN_FORM'
              WHEN n.node_id='ScienceExpertSummaryTask' THEN 'SCIENCE_EXPERT_SUMMARY_FORM'
              WHEN n.node_id='ScienceExpertReviewTask' THEN 'SCIENCE_EXPERT_REVIEW_FORM'
              ELSE 'DEPT_EXPERT_REVIEW_FORM' END END,'-LEGACY-',b.batch_id),COALESCE(b.round_no,1),
       JSON_OBJECT('batchId',b.batch_id,'reviewType',b.review_type,'reviewTitle',b.review_title,
         'ruleType',b.rule_type,'minExpertCount',b.min_expert_count,'passScore',b.pass_score,
         'recommendScore',b.recommend_score,'removeHighestLowest',b.remove_highest_lowest,
         'expectedExpertCount',b.expected_expert_count,'submittedExpertCount',b.submitted_expert_count,
         'validExpertCount',b.valid_expert_count,'lowestScore',b.lowest_score,'highestScore',b.highest_score,
         'finalScore',b.final_score,'finalResult',b.final_result,'status',b.status,'completedAt',b.completed_at),
       COALESCE(b.created_by,0),COALESCE(b.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.expert_review_batch b
  JOIN sic_workflow.module_instance m ON m.id=b.module_instance_id
  LEFT JOIN sic_legacy.workflow_node n ON n.workflow_node_id=b.workflow_node_id;

-- Material metadata; object keys and hashes come only from the verified file manifest.
INSERT INTO sic_material.material_type(
    id,code,name,description,module_type,allowed_file_types,max_file_size_mb,enabled,version)
SELECT material_type_id,material_type_code,material_type_name,NULL,module_type,allowed_file_types,
       COALESCE(max_file_size_mb,50),COALESCE(enabled,TRUE),1 FROM sic_legacy.material_type;

INSERT INTO sic_material.material(id,project_id,material_type_code,title,created_by,created_at)
SELECT m.material_id,m.project_id,mt.material_type_code,
       COALESCE((SELECT mv.file_name FROM sic_legacy.material_version mv WHERE mv.material_id=m.material_id
                  ORDER BY mv.version_no DESC,mv.material_version_id DESC LIMIT 1),mt.material_type_name,
                CONCAT('Legacy material ',m.material_id)),COALESCE(m.created_by,0),
       COALESCE(m.created_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.material m LEFT JOIN sic_legacy.material_type mt ON mt.material_type_id=m.material_type_id;

INSERT INTO sic_material.material_version(
    id,material_id,version_no,original_filename,content_type,size_bytes,sha256,object_key,
    file_status,uploaded_by,created_at)
SELECT v.material_version_id,v.material_id,COALESCE(v.version_no,1),v.file_name,f.content_type,
       f.size_bytes,f.sha256,f.object_key,CASE WHEN COALESCE(v.is_current,FALSE) THEN 'ACTIVE' ELSE 'SUPERSEDED' END,
       COALESCE(v.uploaded_by,0),COALESCE(v.uploaded_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.material_version v
  JOIN sic_legacy.migration_file_manifest f ON f.material_version_id=v.material_version_id;

INSERT INTO sic_workflow.workflow_material_reference(
    module_instance_id,seq,material_version_id,material_type_code,material_type_snapshot)
SELECT sr.module_instance_id,sr.seq,rm.material_version_id,mt.material_type_code,
       JSON_OBJECT('code',mt.material_type_code,'name',mt.material_type_name,
         'moduleType',mt.module_type,'allowedFileTypes',mt.allowed_file_types,
         'maxFileSizeMb',mt.max_file_size_mb,'usageType',rm.material_usage,'required',rm.is_required)
  FROM sic_legacy.state_record_material rm
  JOIN sic_legacy.module_state_record sr ON sr.state_record_id=rm.state_record_id
  JOIN sic_legacy.material_version mv ON mv.material_version_id=rm.material_version_id
  JOIN sic_legacy.material m ON m.material_id=mv.material_id
  JOIN sic_legacy.material_type mt ON mt.material_type_id=m.material_type_id
 GROUP BY sr.module_instance_id,sr.seq,rm.material_version_id,mt.material_type_code,
          mt.material_type_name,mt.module_type,mt.allowed_file_types,mt.max_file_size_mb,
          rm.material_usage,rm.is_required;

INSERT INTO sic_material.generated_document(
    id,command_id,project_id,module_instance_id,state_seq,state_record_id,document_type,
    document_name,template_code,material_version_id,object_key,original_filename,content_type,
    size_bytes,sha256,snapshot_json,created_at)
SELECT d.document_id,CONCAT('legacy-document:',d.document_id),mi.project_id,d.module_instance_id,
       COALESCE(sr.seq,0),d.generated_state_record_id,d.document_type_code,d.document_title,NULL,
       df.material_version_id,f.object_key,mv.file_name,f.content_type,f.size_bytes,f.sha256,
       CASE WHEN JSON_VALID(d.snapshot_json) THEN CAST(d.snapshot_json AS JSON)
            ELSE JSON_OBJECT('legacySnapshot',d.snapshot_json,'documentNo',d.document_no,
                             'documentStatus',d.document_status) END,
       COALESCE(d.generated_at,CURRENT_TIMESTAMP(6))
  FROM sic_legacy.process_document d
  JOIN sic_legacy.project_module_instance mi ON mi.module_instance_id=d.module_instance_id
  LEFT JOIN sic_legacy.module_state_record sr ON sr.state_record_id=d.generated_state_record_id
  JOIN sic_legacy.process_document_file df ON df.document_file_id=(SELECT f2.document_file_id
       FROM sic_legacy.process_document_file f2 WHERE f2.document_id=d.document_id
       ORDER BY COALESCE(f2.is_main_file,FALSE) DESC,f2.document_file_id LIMIT 1)
  JOIN sic_legacy.material_version mv ON mv.material_version_id=df.material_version_id
  JOIN sic_legacy.migration_file_manifest f ON f.material_version_id=mv.material_version_id;

COMMIT;
SET FOREIGN_KEY_CHECKS = @previous_foreign_key_checks;

SELECT 'legacy import complete' AS status,
       (SELECT COUNT(*) FROM sic_iam.app_user) AS users,
       (SELECT COUNT(*) FROM sic_project.project) AS projects,
       (SELECT COUNT(*) FROM sic_workflow.module_instance) AS module_instances,
       (SELECT COUNT(*) FROM sic_material.material_version) AS material_versions;
