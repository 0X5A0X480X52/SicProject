package com.amatrix.sicprojectis_backend.runtime.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.material.MaterialService;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRuntimeRecordRequest;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormSaveRequest;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleStateRecordDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StartModuleInstanceRequest;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionRequest;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.workflow.FlowableBpmnDefinitionParser;
import com.amatrix.sicprojectis_backend.workflow.WorkflowAssetService;
import com.amatrix.sicprojectis_backend.workflow.WorkflowDefinitionService;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDao;
import com.amatrix.sicprojectis_backend.workflow.dto.UploadWorkflowDefinitionRequest;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StateMachineRuntimeIntegrationTest {
    @Autowired
    private StateMachineRuntimeService runtimeService;

    @Autowired
    private WorkflowDefinitionService workflowDefinitionService;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private ProjectModuleInstanceDao moduleDao;

    @Autowired
    private ModuleStateRecordDao stateRecordDao;

    @Autowired
    private WorkflowNodeDao workflowNodeDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowAssetService workflowAssetService;

    @Autowired
    private FlowableBpmnDefinitionParser parser;

    @Autowired
    private StateMachineExtensionRegistry extensionRegistry;

    private final AuthenticatedUser leader = new AuthenticatedUser(1L, "alice", List.of("PROJECT_LEADER"));
    private final AuthenticatedUser deptAdmin = new AuthenticatedUser(4L, "diana", List.of("DEPT_ADMIN"));
    private final AuthenticatedUser systemAdmin = new AuthenticatedUser(6L, "frank", List.of("SYSTEM_ADMIN"));

    @Test
    void daoMethodsShouldReturnRuntimeRows() {
        assertThat(moduleDao.selectByProjectIdAndModuleType(1L, "APPLICATION")).isNotNull();
        assertThat(moduleDao.selectByIdForUpdate(1L)).isNotNull();
        assertThat(stateRecordDao.selectLatestByModuleInstanceId(1L).getSeq()).isEqualTo(1);
        assertThat(workflowNodeDao.selectByWorkflowDefinitionIdAndNodeId(1L, "start_apply")).isNotNull();
        assertThat(workflowNodeDao.selectByWorkflowDefinitionIdAndStateCode(1L, "SUBMITTED")).isNotNull();
    }

    @Test
    void shouldStartValidateMaterialTransitionThroughGatewayAndGenerateDocument() {
        publishContractWorkflow();
        var started = runtimeService.startModule(leader, 1L, new StartModuleInstanceRequest("CONTRACT"));
        Long moduleInstanceId = started.stateRecord().getModuleInstanceId();
        assertThat(started.currentState()).isEqualTo("CONTRACT_DRAFT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_instance WHERE module_instance_id=? AND task_status='OPEN'",
                Integer.class, moduleInstanceId)).isEqualTo(1);

        assertThatThrownBy(() -> runtimeService.transition(leader, moduleInstanceId,
                new StateTransitionRequest("CONTRACT_CONFIRMED_SUBMIT", 1, "SUBMITTED", "submit", List.of(),
                        null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("Material is required");

        var uploaded = materialService.upload(leader, 1L, "CONTRACT_DRAFT_FILE",
                new MockMultipartFile("file", "contract.pdf", "application/pdf", "contract".getBytes()));
        var dept = runtimeService.transition(leader, moduleInstanceId,
                new StateTransitionRequest("CONTRACT_CONFIRMED_SUBMIT", 1, "SUBMITTED", "submit",
                        List.of(uploaded.version().getMaterialVersionId()), null, null));
        assertThat(dept.currentState()).isEqualTo("CONTRACT_DEPT_REVIEWING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_record_material WHERE state_record_id=?",
                Integer.class, dept.stateRecord().getStateRecordId())).isEqualTo(1);

        StateRecordCheckItem item = new StateRecordCheckItem();
        item.setItemCode("DEPT_OK");
        item.setItemName("Department review");
        item.setItemType("BOOLEAN");
        item.setRequired(true);
        item.setPassed(true);
        var finished = runtimeService.transition(deptAdmin, moduleInstanceId,
                new StateTransitionRequest("DEPT_CONTRACT_REVIEW_FINISHED", 2, "APPROVED", "approved", List.of(),
                        "CONTRACT_DEPT_REVIEW_FORM",
                        new NodeFormSaveRequest(null, null, null, null, null, null, null,
                                new NodeFormRuntimeRecordRequest(item, null, null, null, null),
                                null, null, null)));

        assertThat(finished.finished()).isTrue();
        assertThat(finished.currentState()).isEqualTo("CONTRACT_APPROVED");
        assertThat(finished.stateRecord().getToNodeId()).isEqualTo("ApprovedEndEvent");
        assertThat(finished.stateRecord().getToState()).isNotEqualTo("DeptReviewGateway");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM state_record_check_item WHERE state_record_id=?",
                Integer.class, finished.stateRecord().getStateRecordId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM process_document WHERE generated_state_record_id=?",
                Integer.class, finished.stateRecord().getStateRecordId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_instance WHERE module_instance_id=? AND task_status='OPEN'",
                Integer.class, moduleInstanceId)).isZero();
    }

    @Test
    void shouldRejectStaleExpectedSeq() {
        publishContractWorkflow();
        var started = runtimeService.startModule(systemAdmin, 2L, new StartModuleInstanceRequest("CONTRACT"));
        assertThatThrownBy(() -> runtimeService.transition(systemAdmin, started.stateRecord().getModuleInstanceId(),
                new StateTransitionRequest("CONTRACT_CONFIRMED_SUBMIT", 0, "SUBMITTED", "stale", List.of(),
                        null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }


    @Test
    void bundledWorkflowsShouldStartToFirstRealTask() {
        for (var asset : workflowAssetService.listAssets()) {
            workflowDefinitionService.publishAsset(asset.assetName());
            var started = runtimeService.startModule(systemAdmin, 2L, new StartModuleInstanceRequest(asset.moduleType()));

            assertThat(started.finished()).as(asset.assetName()).isFalse();
            assertThat(started.currentState()).as(asset.assetName()).isNotBlank();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task_instance WHERE module_instance_id=? AND task_status='OPEN'",
                    Integer.class, started.stateRecord().getModuleInstanceId())).as(asset.assetName()).isEqualTo(1);
        }
    }

    @Test
    void runtimeViewShouldExposeAvailableTransitionsRequirementsAndExtensionKeys() {
        publishContractWorkflow();
        var started = runtimeService.startModule(leader, 1L, new StartModuleInstanceRequest("CONTRACT"));
        var view = runtimeService.runtimeView(leader, started.stateRecord().getModuleInstanceId());

        assertThat(view.context().getCurrentState()).isEqualTo("CONTRACT_DRAFT");
        assertThat(view.availableTransitions()).extracting("eventType").contains("CONTRACT_CONFIRMED_SUBMIT");
        assertThat(view.availableTransitions().getFirst().actionKeys()).contains("CREATE_DEPT_CONTRACT_REVIEW_TASK");
        assertThat(view.materialRequirements()).extracting("materialTypeCode").contains("CONTRACT_DRAFT_FILE");
        assertThat(view.materialRequirements().getFirst().validatorKey()).isEqualTo("CONTRACT_FILE_VALIDATOR");
        assertThat(view.openTasks()).hasSize(1);
        assertThat(view.history()).hasSize(1);
    }

    @Test
    void bundledWorkflowExtensionKeysShouldBeRegistered() {
        for (var asset : workflowAssetService.listAssets()) {
            var parsed = parser.parse(workflowAssetService.readAssetXml(asset.assetName()));
            parsed.transitions().stream()
                    .flatMap(transition -> transition.actionKeys().stream())
                    .forEach(actionKey -> assertThat(extensionRegistry.actionKeyExists(actionKey))
                            .as(asset.assetName() + " actionKey " + actionKey)
                            .isTrue());
            parsed.transitions().stream()
                    .map(transition -> transition.conditionHandlerKey())
                    .filter(key -> key != null && !key.isBlank())
                    .forEach(key -> assertThat(extensionRegistry.conditionHandlerExists(key))
                            .as(asset.assetName() + " conditionHandlerKey " + key)
                            .isTrue());
            parsed.nodes().stream()
                    .flatMap(node -> node.materialRequirements().stream())
                    .map(requirement -> requirement.validatorKey())
                    .filter(key -> key != null && !key.isBlank())
                    .forEach(key -> assertThat(extensionRegistry.validatorExists(key))
                            .as(asset.assetName() + " validatorKey " + key)
                            .isTrue());
        }
    }

    private void publishContractWorkflow() {
        Long id = workflowDefinitionService.upload(new UploadWorkflowDefinitionRequest(contractBpmn())).workflowDefinitionId();
        workflowDefinitionService.publish(id);
    }

    private String contractBpmn() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:rm="http://example.com/research-management">
                  <process id="contract_process" name="Contract Runtime">
                    <extensionElements>
                      <rm:processConfig processKey="CONTRACT_PROCESS" processName="Contract Runtime" moduleType="CONTRACT" versionNo="99" status="ACTIVE"/>
                    </extensionElements>
                    <startEvent id="StartEvent" name="Start">
                      <extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_START" nodeType="START_EVENT" responsibleActorCode="SYSTEM" responsibleActorName="System" operationMode="SYSTEM_AUTO"/>
                      </extensionElements>
                    </startEvent>
                    <userTask id="FillContractTask" name="Fill Contract">
                      <extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_DRAFT" nodeType="USER_TASK" responsibleActorCode="PROJECT_LEADER" responsibleActorName="Project Leader" candidateRoleCode="PROJECT_LEADER" operationMode="SELF_OPERATE"/>
                        <rm:materialRequirement materialTypeCode="CONTRACT_DRAFT_FILE" materialTypeName="Contract Draft File" requirementTiming="BEFORE_SUBMIT" required="true" minCount="1" maxCount="1" usageType="SUBMITTED_FILE" validatorKey="CONTRACT_FILE_VALIDATOR" allowedFileTypes="pdf" maxFileSizeMb="20"/>
                      </extensionElements>
                    </userTask>
                    <userTask id="DeptReviewTask" name="Department Review">
                      <extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_DEPT_REVIEWING" nodeType="USER_TASK" responsibleActorCode="DEPARTMENT" responsibleActorName="Department" candidateRoleCode="DEPT_ADMIN" operationMode="SELF_OPERATE"/>
                        <rm:documentConfig documentTypeCode="CONTRACT_DEPT_REVIEW_DOC" documentTypeName="Contract Department Review" generateTiming="ON_NODE_COMPLETE" templateCode="tpl_contract_dept" required="true"/>
                      </extensionElements>
                    </userTask>
                    <exclusiveGateway id="DeptReviewGateway" name="Department Result">
                      <extensionElements>
                        <rm:workflowNode nodeType="GATEWAY" responsibleActorCode="SYSTEM" responsibleActorName="System" operationMode="SYSTEM_AUTO"/>
                      </extensionElements>
                    </exclusiveGateway>
                    <endEvent id="ApprovedEndEvent" name="Approved">
                      <extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_APPROVED" nodeType="END_EVENT" responsibleActorCode="SYSTEM" responsibleActorName="System" operationMode="SYSTEM_AUTO"/>
                        <rm:documentConfig documentTypeCode="CONTRACT_END_DOC" documentTypeName="Contract End" generateTiming="ON_PROCESS_END" templateCode="tpl_contract_end" required="true"/>
                      </extensionElements>
                    </endEvent>
                    <sequenceFlow id="Flow_Start_Fill" sourceRef="StartEvent" targetRef="FillContractTask">
                      <extensionElements><rm:transition eventType="CONTRACT_PROCESS_STARTED" result="START" conditionType="NONE" sourceRef="StartEvent" targetRef="FillContractTask" sourceStateCode="CONTRACT_START" targetStateCode="CONTRACT_DRAFT"/></extensionElements>
                    </sequenceFlow>
                    <sequenceFlow id="Flow_Fill_Dept" sourceRef="FillContractTask" targetRef="DeptReviewTask">
                      <extensionElements><rm:transition eventType="CONTRACT_CONFIRMED_SUBMIT" result="SUBMITTED" conditionType="NONE" actionKeys="CREATE_DEPT_CONTRACT_REVIEW_TASK" sourceRef="FillContractTask" targetRef="DeptReviewTask" sourceStateCode="CONTRACT_DRAFT" targetStateCode="CONTRACT_DEPT_REVIEWING"/></extensionElements>
                    </sequenceFlow>
                    <sequenceFlow id="Flow_Dept_Gateway" sourceRef="DeptReviewTask" targetRef="DeptReviewGateway">
                      <extensionElements><rm:transition eventType="DEPT_CONTRACT_REVIEW_FINISHED" result="REVIEW_FINISHED" conditionType="NONE" sourceRef="DeptReviewTask" targetRef="DeptReviewGateway" sourceStateCode="CONTRACT_DEPT_REVIEWING"/></extensionElements>
                    </sequenceFlow>
                    <sequenceFlow id="Flow_Dept_Approved" sourceRef="DeptReviewGateway" targetRef="ApprovedEndEvent">
                      <extensionElements><rm:transition eventType="DEPT_CONTRACT_APPROVE" result="APPROVED" conditionType="SIMPLE_BOOL" conditionKey="deptApproved" conditionValue="true" sourceRef="DeptReviewGateway" targetRef="ApprovedEndEvent" targetStateCode="CONTRACT_APPROVED"/></extensionElements>
                      <conditionExpression xsi:type="tFormalExpression">${deptApproved == true}</conditionExpression>
                    </sequenceFlow>
                  </process>
                </definitions>
                """;
    }
}
