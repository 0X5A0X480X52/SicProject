package com.amatrix.sic.workflow;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WorkflowDefinitionCatalog {
    private final JdbcTemplate jdbc;
    private final FlowableBpmnDefinitionParser parser;

    public WorkflowDefinitionCatalog(JdbcTemplate jdbc, FlowableBpmnDefinitionParser parser) {
        this.jdbc = jdbc;
        this.parser = parser;
    }

    public ActiveDefinition active(String moduleType) {
        List<ActiveDefinition> rows = jdbc.query("""
                SELECT id,bpmn_xml FROM workflow_definition
                 WHERE code=? AND published=TRUE ORDER BY version DESC,id DESC LIMIT 1
                """, (rs, row) -> new ActiveDefinition(rs.getLong("id"), parser.parse(rs.getString("bpmn_xml"))),
                moduleType);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Active " + moduleType + " workflow definition not found");
        }
        return rows.getFirst();
    }

    public WorkflowBpmnParseResult model(Long definitionId) {
        List<WorkflowBpmnParseResult> rows = jdbc.query(
                "SELECT bpmn_xml FROM workflow_definition WHERE id=?",
                (rs, row) -> parser.parse(rs.getString("bpmn_xml")), definitionId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow definition not found");
        }
        return rows.getFirst();
    }

    public record ActiveDefinition(Long id, WorkflowBpmnParseResult model) {
    }
}
