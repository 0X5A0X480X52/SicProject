package com.amatrix.sic.workflow.service.runtime;

import com.amatrix.sic.workflow.service.runtime.WorkflowTransitionActionService;
import com.amatrix.sic.workflow.workflow.parser.FlowableBpmnDefinitionParser;
import com.amatrix.sic.workflow.workflow.model.WorkflowBpmnParseResult;
import com.amatrix.sic.workflow.workflow.definition.WorkflowDefinitionNavigator;
import com.amatrix.sic.workflow.workflow.definition.WorkflowAssetCatalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class WorkflowDefinitionModelTest {
    private final FlowableBpmnDefinitionParser parser = new FlowableBpmnDefinitionParser();
    private final WorkflowDefinitionNavigator navigator = new WorkflowDefinitionNavigator();

    @Test
    void parsesAllBundledWorkflowAssets() {
        assertModel("项目申请_辅助标签版.bpmn", "APPLICATION", 10, 20);
        assertModel("纵向项目合同_辅助标签版.bpmn", "CONTRACT", 10, 10);
        assertModel("项目结题_辅助标签版.bpmn", "ACCEPTANCE", 15, 20);
    }

    @Test
    void resolvesApplicationTransitionsAndConditionalGateways() {
        WorkflowBpmnParseResult model = applicationModel();
        var started = navigator.start(model, transition -> true);
        assertThat(started.node().nodeId()).isEqualTo("SubmitApplicationTask");
        assertThat(started.node().stateCode()).isEqualTo("APPLICATION_DRAFT");
        assertThat(started.node().candidateRoleCode()).isEqualTo("PROJECT_LEADER");

        var submitted = navigator.transition(model, "SubmitApplicationTask", "USER_CONFIRMED_SUBMIT",
                transition -> true);
        assertThat(submitted.node().nodeId()).isEqualTo("DeptReviewTask");
        assertThat(submitted.node().stateCode()).isEqualTo("APPLICATION_DEPT_REVIEWING");

        Set<String> approvedNonLimited = Set.of("DEPT_APPROVE", "NON_LIMITED_PROJECT_SELECTED");
        var approved = navigator.transition(model, "DeptReviewTask", "DEPT_REVIEW_FINISHED",
                transition -> approvedNonLimited.contains(transition.eventType()));
        assertThat(approved.node().nodeId()).isEqualTo("ScienceOfficeInitialReviewTask");
        assertThat(approved.transitions()).extracting(WorkflowBpmnParseResult.TransitionConfig::eventType)
                .containsExactly("DEPT_REVIEW_FINISHED", "DEPT_APPROVE", "NON_LIMITED_PROJECT_SELECTED");

        var rejected = navigator.transition(model, "DeptReviewTask", "DEPT_REVIEW_FINISHED",
                transition -> "DEPT_REJECT".equals(transition.eventType()));
        assertThat(rejected.node().nodeId()).isEqualTo("SubmitApplicationTask");
        assertThat(rejected.node().stateCode()).isEqualTo("APPLICATION_DRAFT");
    }

    @Test
    void retainsMaterialRequirementsFromContractDefinition() {
        WorkflowBpmnParseResult model = parser.parse(
                WorkflowAssetCatalog.read("纵向项目合同_辅助标签版.bpmn"));
        var fillContract = model.nodes().stream().filter(node -> "FillContractTask".equals(node.nodeId()))
                .findFirst().orElseThrow();
        assertThat(fillContract.materialRequirements())
                .anySatisfy(requirement -> {
                    assertThat(requirement.materialTypeCode()).isEqualTo("CONTRACT_DRAFT");
                    assertThat(requirement.required()).isTrue();
                    assertThat(requirement.requirementTiming()).isEqualTo("BEFORE_SUBMIT");
                });
    }

    private WorkflowBpmnParseResult applicationModel() {
        return parser.parse(WorkflowAssetCatalog.read("项目申请_辅助标签版.bpmn"));
    }

    private void assertModel(String assetName, String moduleType, int minimumNodes, int minimumTransitions) {
        WorkflowBpmnParseResult model = parser.parse(WorkflowAssetCatalog.read(assetName));
        assertThat(model.processConfig().moduleType()).isEqualTo(moduleType);
        assertThat(model.nodes()).hasSizeGreaterThanOrEqualTo(minimumNodes);
        assertThat(model.transitions()).hasSizeGreaterThanOrEqualTo(minimumTransitions);
        assertThat(model.nodes()).anyMatch(node -> "START_EVENT".equals(node.nodeType()));
        assertThat(model.nodes()).anyMatch(node -> "END_EVENT".equals(node.nodeType()));
        assertThat(model.transitions().stream().flatMap(transition -> transition.actionKeys().stream()).toList())
                .allMatch(WorkflowTransitionActionService.OFFICIAL_ACTION_KEYS::contains);
    }
}
