package com.amatrix.sic.workflow;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.workflow.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sic.workflow.WorkflowBpmnParseResult.TransitionConfig;

import tools.jackson.databind.ObjectMapper;

@Service
public class ApplicationSagaWorkflowService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OutboxService outbox;
    private final WorkflowDefinitionCatalog definitions;
    private final WorkflowDefinitionNavigator navigator;
    public ApplicationSagaWorkflowService(JdbcTemplate jdbc,ObjectMapper objectMapper,OutboxService outbox,
            WorkflowDefinitionCatalog definitions,WorkflowDefinitionNavigator navigator){this.jdbc=jdbc;this.objectMapper=objectMapper;this.outbox=outbox;this.definitions=definitions;this.navigator=navigator;}

    @Transactional
    public ApplicationSagaResult create(ApplicationSagaCommand command){
        ApplicationSagaResult existing=find(command.sagaId());
        if(existing!=null)return existing;
        WorkflowDefinitionCatalog.ActiveDefinition definition=definitions.active("APPLICATION");
        var resolved=navigator.start(definition.model(),ApplicationSagaWorkflowService::unconditional);
        NodeConfig target=resolved.node();TransitionConfig startTransition=resolved.initialTransition();
        boolean finished="END_EVENT".equals(target.nodeType());
        GeneratedKeyHolder moduleHolder=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO module_instance(saga_id,project_id,started_by_user_id,module_code,definition_id,current_node_id,
                  current_state,current_seq,current_round,finished) VALUES (?,?,?,'APPLICATION',?,?,?,1,1,?)
                """,Statement.RETURN_GENERATED_KEYS);ps.setString(1,command.sagaId().toString());ps.setLong(2,command.projectId());ps.setLong(3,command.actorUserId());ps.setLong(4,definition.id());ps.setString(5,target.nodeId());ps.setString(6,target.stateCode());ps.setBoolean(7,finished);return ps;},moduleHolder);
        Long moduleId=Objects.requireNonNull(moduleHolder.getKey()).longValue();
        jdbc.update("""
                INSERT INTO project_snapshot(project_id,project_code,project_name,owner_user_id,lifecycle_stage,source_version)
                VALUES (?,?,?,?,'APPLICATION',1) ON DUPLICATE KEY UPDATE project_code=VALUES(project_code),
                  project_name=VALUES(project_name),owner_user_id=VALUES(owner_user_id),lifecycle_stage=VALUES(lifecycle_stage)
                """,command.projectId(),command.projectCode(),command.projectName(),command.actorUserId());
        jdbc.update("INSERT INTO project_application(module_instance_id,project_id,title,limited_project,summary,payload_json) VALUES (?,?,?,?,?,?)",
                moduleId,command.projectId(),command.applicationTitle(),Boolean.TRUE.equals(command.limitedProject()),command.applicationSummary(),write(command));
        GeneratedKeyHolder stateHolder=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement ps=connection.prepareStatement("""
                INSERT INTO state_record(module_instance_id,seq,round_no,event_type,from_state,to_state,from_node_id,to_node_id,result,summary,actor_user_id,actor_name_snapshot)
                VALUES (?,1,1,?,?,?,?,?,?, 'Project application started by applicant',?,?)
                """,Statement.RETURN_GENERATED_KEYS);ps.setLong(1,moduleId);ps.setString(2,startTransition.eventType());ps.setString(3,startTransition.sourceStateCode());ps.setString(4,target.stateCode());ps.setString(5,startTransition.sourceRef());ps.setString(6,target.nodeId());ps.setString(7,startTransition.result());ps.setLong(8,command.actorUserId());ps.setString(9,"user-"+command.actorUserId());return ps;},stateHolder);
        Long stateId=Objects.requireNonNull(stateHolder.getKey()).longValue();
        if(!finished)jdbc.update("INSERT INTO workflow_task(module_instance_id,node_id,assignee_user_id,candidate_role,status,round_no) VALUES (?,?,?,?,'OPEN',1)",moduleId,target.nodeId(),command.actorUserId(),target.candidateRoleCode());
        outbox.enqueue("workflow.module-state-changed",EventEnvelope.create("MODULE_STATE_CHANGED","MODULE",moduleId.toString(),command.sagaId().toString(),null,command.actorUserId(),
                java.util.Map.of("type","MODULE_STATE_CHANGED","projectId",command.projectId(),"moduleInstanceId",moduleId,"moduleType","APPLICATION","fromState",startTransition.sourceStateCode(),"toState",target.stateCode(),"seq",1,"eventType",startTransition.eventType(),"occurredAt",LocalDateTime.now().toString())));
        StateRecordContract record=new StateRecordContract(stateId,moduleId,1,1,startTransition.eventType(),startTransition.sourceStateCode(),target.stateCode(),startTransition.sourceRef(),target.nodeId(),startTransition.result(),"Project application started by applicant",LocalDateTime.now());
        return new ApplicationSagaResult(command.sagaId(),command.projectId(),moduleId,new StateTransitionContract(record,target.nodeId(),target.stateCode(),finished),"CREATED");
    }

    public ApplicationSagaResult find(UUID sagaId){
        List<ApplicationSagaResult> rows=jdbc.query("""
                SELECT m.id module_id,m.project_id,m.current_node_id,m.current_state,m.finished,s.id state_id,s.seq,s.round_no,
                  s.event_type,s.from_state,s.to_state,s.from_node_id,s.to_node_id,s.result,s.summary,s.created_at
                  FROM module_instance m JOIN state_record s ON s.module_instance_id=m.id
                 WHERE m.saga_id=? ORDER BY s.seq DESC LIMIT 1
                """,(rs,row)->new ApplicationSagaResult(sagaId,rs.getLong("project_id"),rs.getLong("module_id"),
                        new StateTransitionContract(new StateRecordContract(rs.getLong("state_id"),rs.getLong("module_id"),rs.getInt("seq"),rs.getInt("round_no"),rs.getString("event_type"),rs.getString("from_state"),rs.getString("to_state"),rs.getString("from_node_id"),rs.getString("to_node_id"),rs.getString("result"),rs.getString("summary"),rs.getObject("created_at",LocalDateTime.class)),rs.getString("current_node_id"),rs.getString("current_state"),rs.getBoolean("finished")),"CREATED"),sagaId.toString());
        return rows.isEmpty()?null:rows.getFirst();
    }

    @Transactional
    public void compensate(UUID sagaId){
        List<Long> modules=jdbc.query("SELECT id FROM module_instance WHERE saga_id=?",(rs,row)->rs.getLong(1),sagaId.toString());
        if(modules.isEmpty())return;Long id=modules.getFirst();
        jdbc.update("DELETE FROM expert_review_score WHERE assignment_id IN (SELECT id FROM expert_review_assignment WHERE batch_id IN (SELECT id FROM expert_review_batch WHERE module_instance_id=?))",id);
        jdbc.update("DELETE FROM expert_review_assignment WHERE batch_id IN (SELECT id FROM expert_review_batch WHERE module_instance_id=?)",id);
        jdbc.update("DELETE FROM expert_review_batch WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM workflow_material_reference WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM expert_review WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM acceptance_form WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM contract_form WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM project_application WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM node_form WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM workflow_task WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM state_record WHERE module_instance_id=?",id);
        jdbc.update("DELETE FROM module_instance WHERE id=?",id);
    }

    private String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static boolean unconditional(TransitionConfig transition){String type=transition.conditionType();return type==null||type.isBlank()||"NONE".equalsIgnoreCase(type)||"ALWAYS".equalsIgnoreCase(type);}
}
