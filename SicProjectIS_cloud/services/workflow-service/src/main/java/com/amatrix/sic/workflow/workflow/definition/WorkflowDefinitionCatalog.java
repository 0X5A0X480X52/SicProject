package com.amatrix.sic.workflow.workflow.definition;

import com.amatrix.sic.workflow.workflow.parser.FlowableBpmnDefinitionParser;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;

import java.util.List;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.workflow.dao.WorkflowCatalogMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WorkflowDefinitionCatalog {
    private final WorkflowCatalogMapper mapper;
    private final FlowableBpmnDefinitionParser parser;

    public WorkflowDefinitionCatalog(WorkflowCatalogMapper mapper, FlowableBpmnDefinitionParser parser) {
        this.mapper = mapper;
        this.parser = parser;
    }

    public ActiveDefinition active(String moduleType) {
        var source = mapper.active(moduleType);
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Active " + moduleType + " workflow definition not found");
        }
        return new ActiveDefinition(source.id(), parser.parse(source.bpmnXml()));
    }

    public WorkflowBpmnParseResult model(Long definitionId) {
        String bpmn = mapper.bpmn(definitionId);
        if (bpmn == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow definition not found");
        }
        return parser.parse(bpmn);
    }

    public record ActiveDefinition(Long id, WorkflowBpmnParseResult model) {
    }
}
