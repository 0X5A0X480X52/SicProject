package com.amatrix.sic.workflow;

import com.amatrix.sic.workflow.service.form.NodeFormService;
import com.amatrix.sic.workflow.workflow.parser.FlowableBpmnDefinitionParser;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.workflow.definition.WorkflowAssetCatalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class NodeFormCatalogTest {
    @Test
    void exposesTheCompleteFrontendNodeFormCatalog() {
        var definitions = new NodeFormService(null, null, null,null).definitions();

        assertThat(definitions).hasSize(43);
        assertThat(definitions).extracting(item -> item.get("formCode")).doesNotHaveDuplicates()
                .contains("PROJECT_APPLICATION_FORM", "CONTRACT_DRAFT_FORM", "ACCEPTANCE_APPLICATION_FORM",
                        "DEPT_EXPERT_REVIEW_FORM", "EXPERT_ACCEPTANCE_REVIEW_FORM",
                        "ACCEPTANCE_CERTIFICATE_DOCUMENT");
        assertThat(definitions).allSatisfy(item -> {
            assertThat(item.get("nodeId")).isNotNull();
            assertThat(item.get("dataKind")).isNotNull();
            assertThat(item.get("writeMode")).isIn("SINGLE_INSTANCE", "HISTORY_RECORD", "READ_ONLY");
        });
    }

    @Test
    void everyBundledUserTaskHasAtLeastOneFormDefinition() {
        NodeFormService forms = new NodeFormService(null, null, null,null);
        FlowableBpmnDefinitionParser parser = new FlowableBpmnDefinitionParser();
        assertUserTasksCovered(forms, parser, "项目申请_辅助标签版.bpmn", "APPLICATION");
        assertUserTasksCovered(forms, parser, "纵向项目合同_辅助标签版.bpmn", "CONTRACT");
        assertUserTasksCovered(forms, parser, "项目结题_辅助标签版.bpmn", "ACCEPTANCE");
    }

    private static void assertUserTasksCovered(NodeFormService forms, FlowableBpmnDefinitionParser parser,
            String asset, String moduleType) {
        Set<String> configured = forms.definitions().stream()
                .filter(item -> moduleType.equals(item.get("moduleType")))
                .map(item -> String.valueOf(item.get("nodeId"))).collect(Collectors.toSet());
        Set<String> tasks = parser.parse(WorkflowAssetCatalog.read(asset)).nodes().stream()
                .filter(node -> "USER_TASK".equals(node.nodeType()))
                .map(WorkflowBpmnParseResult.NodeConfig::nodeId).collect(Collectors.toSet());
        assertThat(configured).as("node form catalog for %s", moduleType).containsAll(tasks);
        assertThat(forms.forNode(moduleType, tasks.iterator().next(), null)).isNotEmpty();
    }
}
