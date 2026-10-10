package com.amatrix.sic.workflow.service.saga;

import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionCatalog;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionNavigator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.workflow.dao.WorkflowSagaMapper;
import com.amatrix.sic.workflow.dao.WorkflowRuntimeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult.TransitionConfig;

import tools.jackson.databind.ObjectMapper;

@Service
public class ApplicationSagaWorkflowService {
    private final WorkflowSagaMapper mapper;
    private final WorkflowRuntimeMapper runtime;
    private final ObjectMapper objectMapper;
    private final OutboxService outbox;
    private final WorkflowDefinitionCatalog definitions;
    private final WorkflowDefinitionNavigator navigator;
    public ApplicationSagaWorkflowService(WorkflowSagaMapper mapper,WorkflowRuntimeMapper runtime,ObjectMapper objectMapper,OutboxService outbox,
            WorkflowDefinitionCatalog definitions,WorkflowDefinitionNavigator navigator){this.mapper=mapper;this.runtime=runtime;this.objectMapper=objectMapper;this.outbox=outbox;this.definitions=definitions;this.navigator=navigator;}

    @Transactional
    public ApplicationSagaResult create(ApplicationSagaCommand command){
        ApplicationSagaResult existing=find(command.sagaId());
        if(existing!=null)return existing;
        WorkflowDefinitionCatalog.ActiveDefinition definition=definitions.active("APPLICATION");
        var resolved=navigator.start(definition.model(),ApplicationSagaWorkflowService::unconditional);
        NodeConfig target=resolved.node();TransitionConfig startTransition=resolved.initialTransition();
        boolean finished="END_EVENT".equals(target.nodeType());
        java.util.Map<String,Object> module=linked("saga",command.sagaId().toString(),"project",command.projectId(),"actor",command.actorUserId(),
                "definition",definition.id(),"node",target.nodeId(),"state",target.stateCode(),"finished",finished);
        mapper.insertModule(module);Long moduleId=((Number)Objects.requireNonNull(module.get("id"))).longValue();
        mapper.snapshot(linked("project",command.projectId(),"code",command.projectCode(),"name",command.projectName(),"actor",command.actorUserId()));
        mapper.application(linked("module",moduleId,"project",command.projectId(),"title",command.applicationTitle(),"limited",Boolean.TRUE.equals(command.limitedProject()),"summary",command.applicationSummary(),"json",write(command)));
        java.util.Map<String,Object> state=linked("module",moduleId,"seq",1,"round",1,"event",startTransition.eventType(),"fromState",startTransition.sourceStateCode(),"toState",target.stateCode(),
                "fromNode",startTransition.sourceRef(),"toNode",target.nodeId(),"result",startTransition.result(),"summary","Project application started by applicant","actor",command.actorUserId(),"name","user-"+command.actorUserId());
        runtime.insertState(state);Long stateId=((Number)Objects.requireNonNull(state.get("id"))).longValue();
        if(!finished)runtime.insertTask(moduleId,target.nodeId(),command.actorUserId(),target.candidateRoleCode(),1);
        outbox.enqueue("workflow.module-state-changed",EventEnvelope.create("MODULE_STATE_CHANGED","MODULE",moduleId.toString(),command.sagaId().toString(),null,command.actorUserId(),
                java.util.Map.of("type","MODULE_STATE_CHANGED","projectId",command.projectId(),"moduleInstanceId",moduleId,"moduleType","APPLICATION","fromState",startTransition.sourceStateCode(),"toState",target.stateCode(),"seq",1,"eventType",startTransition.eventType(),"occurredAt",LocalDateTime.now().toString())));
        StateRecordContract record=new StateRecordContract(stateId,moduleId,1,1,startTransition.eventType(),startTransition.sourceStateCode(),target.stateCode(),startTransition.sourceRef(),target.nodeId(),startTransition.result(),"Project application started by applicant",LocalDateTime.now());
        return new ApplicationSagaResult(command.sagaId(),command.projectId(),moduleId,new StateTransitionContract(record,target.nodeId(),target.stateCode(),finished),"CREATED");
    }

    public ApplicationSagaResult find(UUID sagaId){
        var row=mapper.find(sagaId.toString());
        if(row==null)return null;
        var record=new StateRecordContract(row.stateId(),row.moduleId(),row.seq(),row.round(),row.event(),row.fromState(),row.toState(),row.fromNode(),row.toNode(),row.result(),row.summary(),row.createdAt());
        return new ApplicationSagaResult(sagaId,row.projectId(),row.moduleId(),new StateTransitionContract(record,row.node(),row.state(),row.finished()),"CREATED");
    }

    @Transactional
    public void compensate(UUID sagaId){
        Long id=mapper.module(sagaId.toString());
        if(id==null)return;
        mapper.deleteScores(id);mapper.deleteAssignments(id);
        mapper.deleteBatches(id);
        mapper.deleteMaterials(id);
        mapper.deleteReviews(id);
        mapper.deleteAcceptance(id);
        mapper.deleteContract(id);
        mapper.deleteApplication(id);
        mapper.deleteForms(id);
        mapper.deleteTasks(id);
        mapper.deleteStates(id);
        mapper.deleteModule(id);
    }

    private String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static boolean unconditional(TransitionConfig transition){String type=transition.conditionType();return type==null||type.isBlank()||"NONE".equalsIgnoreCase(type)||"ALWAYS".equalsIgnoreCase(type);}
    private static java.util.Map<String,Object> linked(Object... pairs) {
        java.util.Map<String,Object> result=new java.util.LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);
        return result;
    }
}
