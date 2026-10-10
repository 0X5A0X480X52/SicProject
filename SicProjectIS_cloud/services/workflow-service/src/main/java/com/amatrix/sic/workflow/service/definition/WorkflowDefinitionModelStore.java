package com.amatrix.sic.workflow.service.definition;

import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.amatrix.sic.workflow.dao.WorkflowDefinitionMapper;
import tools.jackson.databind.ObjectMapper;

/** Definition model writes participate in the caller's local transaction. */
@Component
public class WorkflowDefinitionModelStore {
    private final WorkflowDefinitionMapper mapper;
    private final ObjectMapper json;
    public WorkflowDefinitionModelStore(WorkflowDefinitionMapper mapper,ObjectMapper json) { this.mapper=mapper;this.json=json; }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void persist(Long id,WorkflowBpmnParseResult model) {
        mapper.deleteTransitions(id);mapper.deleteNodes(id);
        for(var node:model.nodes())mapper.insertNode(Map.of("definition",id,"node",node,"materials",write(node.materialRequirements()),"documents",write(node.documentConfigs())));
        for(var transition:model.transitions())mapper.insertTransition(Map.of("definition",id,"transition",transition,"actions",write(transition.actionKeys())));
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception ex) { throw new IllegalStateException("Unable to serialize workflow model",ex); } }
}
