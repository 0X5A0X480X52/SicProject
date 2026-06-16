package com.amatrix.sicprojectis_backend.workflow;

import java.util.List;

import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDocumentConfigDao;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeMaterialRequirementDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeDocumentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowDefinitionIntegrationTest {
    private static final String APPLICATION_ASSET = "\u9879\u76ee\u7533\u8bf7_\u8f85\u52a9\u6807\u7b7e\u7248.bpmn";
    private static final String CONTRACT_ASSET = "\u7eb5\u5411\u9879\u76ee\u5408\u540c_\u8f85\u52a9\u6807\u7b7e\u7248.bpmn";
    private static final String ACCEPTANCE_ASSET = "\u9879\u76ee\u7ed3\u9898_\u8f85\u52a9\u6807\u7b7e\u7248.bpmn";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserDao appUserDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WorkflowNodeDao workflowNodeDao;

    @Autowired
    private WorkflowNodeMaterialRequirementDao workflowNodeMaterialRequirementDao;

    @Autowired
    private WorkflowNodeDocumentConfigDao workflowNodeDocumentConfigDao;

    @BeforeEach
    void encodePasswords() {
        for (AppUser user : appUserDao.selectAll()) {
            user.setPasswordHash(passwordEncoder.encode(
                    "bootstrap_admin".equals(user.getUsername()) ? "Bootstrap123456" : "password"));
            user.setEnabled(true);
            appUserDao.updateById(user);
        }
    }

    @Test
    void shouldListAndPublishBundledWorkflowAssets() throws Exception {
        String scienceAdminToken = login("carol", "password");

        mockMvc.perform(get("/api/workflow-definitions/assets").header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].moduleType", hasItem("APPLICATION")))
                .andExpect(jsonPath("$.data[*].moduleType", hasItem("CONTRACT")))
                .andExpect(jsonPath("$.data[*].moduleType", hasItem("ACCEPTANCE")));

        assertPublishedAsset(scienceAdminToken, APPLICATION_ASSET, "APPLICATION", 20, 12, 2, 26,
                "APPLICATION_APPROVAL_FORM");
        assertPublishedAsset(scienceAdminToken, CONTRACT_ASSET, "CONTRACT", 15, 10, 2, 17,
                "CONTRACT_ARCHIVE_FORM", "SIGNED_CONTRACT_DOCUMENT");
        assertPublishedAsset(scienceAdminToken, ACCEPTANCE_ASSET, "ACCEPTANCE", 21, 14, 3, 24,
                "ACCEPTANCE_CERTIFICATE_DOCUMENT", "ACCEPTANCE_FAIL_DOCUMENT", "ACCEPTANCE_SUMMARY_FORM");
    }

    @Test
    void shouldUploadValidateAndPublishWorkflowDefinition() throws Exception {
        String scienceAdminToken = login("carol", "password");
        Long workflowDefinitionId = upload(scienceAdminToken, validBpmn());

        mockMvc.perform(post("/api/workflow-definitions/" + workflowDefinitionId + "/validate")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.moduleType").value("CONTRACT"))
                .andExpect(jsonPath("$.data.roleCodes[*]", hasItem("SCIENCE_ADMIN")));

        mockMvc.perform(post("/api/workflow-definitions/" + workflowDefinitionId + "/publish")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.definition.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.nodes", hasSize(2)))
                .andExpect(jsonPath("$.data.nodes[1].candidateRoleCode").value("SCIENCE_ADMIN"))
                .andExpect(jsonPath("$.data.transitions", hasSize(1)))
                .andExpect(jsonPath("$.data.transitions[0].transitionId").value("flow_submit"))
                .andExpect(jsonPath("$.data.transitions[0].eventType").value("SUBMIT"))
                .andExpect(jsonPath("$.data.transitions[0].conditionExpression").value("${approved == true}"));

        mockMvc.perform(get("/api/workflow-definitions/latest")
                        .header("Authorization", "Bearer " + scienceAdminToken)
                        .param("moduleType", "CONTRACT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowDefinitionId").value(workflowDefinitionId));
    }

    @Test
    void shouldRejectUnknownCandidateRoleDuringValidationAndPublish() throws Exception {
        String scienceAdminToken = login("carol", "password");
        Long workflowDefinitionId = upload(scienceAdminToken, invalidRoleBpmn());

        mockMvc.perform(post("/api/workflow-definitions/" + workflowDefinitionId + "/validate")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]")
                        .value(org.hamcrest.Matchers.containsString("Unknown candidateRoleCode")));

        mockMvc.perform(post("/api/workflow-definitions/" + workflowDefinitionId + "/publish")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Unknown candidateRoleCode")));
    }

    @Test
    void shouldRejectWorkflowDefinitionAccessForDeptAdmin() throws Exception {
        String deptAdminToken = login("diana", "password");

        mockMvc.perform(get("/api/workflow-definitions").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/workflow-definitions/assets").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden());
    }

    private void assertPublishedAsset(
            String token,
            String assetName,
            String moduleType,
            int nodeCount,
            int materialRequirementCount,
            int documentConfigCount,
            int transitionCount,
            String... requiredDocumentTypeCodes) throws Exception {
        Long workflowDefinitionId = publishAsset(token, assetName, moduleType, nodeCount, transitionCount);
        mockMvc.perform(get("/api/workflow-definitions/latest")
                        .header("Authorization", "Bearer " + token)
                        .param("moduleType", moduleType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowDefinitionId").value(workflowDefinitionId));

        List<WorkflowNode> nodes = workflowNodeDao.selectByWorkflowDefinitionId(workflowDefinitionId);
        int materialCount = nodes.stream()
                .mapToInt(node -> workflowNodeMaterialRequirementDao.selectByWorkflowNodeId(node.getWorkflowNodeId()).size())
                .sum();
        int documentCount = nodes.stream()
                .mapToInt(node -> workflowNodeDocumentConfigDao.selectByWorkflowNodeId(node.getWorkflowNodeId()).size())
                .sum();
        assertThat(materialCount).isEqualTo(materialRequirementCount);
        assertThat(documentCount).isEqualTo(documentConfigCount);
        assertThat(nodes).anySatisfy(node -> {
            assertThat(node.getNodeType()).isEqualTo("GATEWAY");
            assertThat(node.getStateCode()).isNull();
        });

        List<String> documentTypeCodes = nodes.stream()
                .flatMap(node -> workflowNodeDocumentConfigDao.selectByWorkflowNodeId(node.getWorkflowNodeId()).stream())
                .map(WorkflowNodeDocumentConfig::getDocumentTypeCode)
                .toList();
        assertThat(documentTypeCodes).contains(requiredDocumentTypeCodes);
    }

    private Long publishAsset(String token, String assetName, String moduleType, int nodeCount, int transitionCount) throws Exception {
        String content = mockMvc.perform(post("/api/workflow-definitions/assets/{assetName}/publish", assetName)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.definition.moduleType").value(moduleType))
                .andExpect(jsonPath("$.data.definition.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.nodes", hasSize(nodeCount)))
                .andExpect(jsonPath("$.data.transitions", hasSize(transitionCount)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Long.valueOf(content.replaceAll(".*\"workflowDefinitionId\":(\\d+).*", "$1"));
    }

    private Long upload(String token, String bpmnXml) throws Exception {
        String body = "{\"bpmnXml\":\"" + escapeJson(bpmnXml) + "\"}";
        String content = mockMvc.perform(post("/api/workflow-definitions/upload")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowDefinitionId", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Long.valueOf(content.replaceAll(".*\"workflowDefinitionId\":(\\d+).*", "$1"));
    }

    private String login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String validBpmn() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:rm="http://example.com/research-management">
                  <process id="contract_process" name="Contract Approval">
                    <extensionElements>
                      <rm:processConfig processKey="CONTRACT_PROCESS"
                                        processName="Contract Approval"
                                        moduleType="CONTRACT"
                                        versionNo="2"
                                        status="ACTIVE"/>
                    </extensionElements>
                    <laneSet>
                      <lane id="lane_science" name="Science Office">
                        <flowNodeRef>submit_task</flowNodeRef>
                      </lane>
                    </laneSet>
                    <startEvent id="start_event" name="Start">
                      <extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_DRAFT"
                                         nodeType="START_EVENT"
                                         responsibleActorCode="PROJECT_LEADER"
                                         responsibleActorName="Project Leader"
                                         candidateRoleCode="PROJECT_LEADER"
                                         operationMode="MANUAL"/>
                      </extensionElements>
                    </startEvent>
                    <userTask id="submit_task" name="Science Review">
                      <extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_SCIENCE_REVIEW"
                                         nodeType="USER_TASK"
                                         responsibleActorCode="SCIENCE_ADMIN"
                                         responsibleActorName="Science Admin"
                                         candidateRoleCode="SCIENCE_ADMIN"
                                         operationMode="MANUAL"/>
                        <rm:materialRequirement materialTypeCode="CONTRACT_FILE"
                                                materialTypeName="Contract File"
                                                requirementTiming="BEFORE_SUBMIT"
                                                required="true"
                                                minCount="1"
                                                usageType="INPUT"
                                                allowedFileTypes="pdf,docx"
                                                maxFileSizeMb="20"/>
                        <rm:documentConfig documentTypeCode="CONTRACT_NOTICE"
                                           documentTypeName="Contract Notice"
                                           generateTiming="AFTER_APPROVE"
                                           templateCode="contract-notice"
                                           outputMaterialTypeCode="CONTRACT_NOTICE_FILE"
                                           outputMaterialTypeName="Contract Notice File"
                                           required="true"/>
                      </extensionElements>
                    </userTask>
                    <sequenceFlow id="flow_submit" sourceRef="start_event" targetRef="submit_task">
                      <extensionElements>
                        <rm:transition eventType="SUBMIT"
                                       result="PASS"
                                       conditionType="SIMPLE_BOOL"
                                       conditionKey="approved"
                                       conditionValue="true"
                                       sourceStateCode="CONTRACT_DRAFT"
                                       targetStateCode="CONTRACT_SCIENCE_REVIEW"
                                       actionKeys="SYSTEM_AUTO"/>
                      </extensionElements>
                      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
                    </sequenceFlow>
                  </process>
                </definitions>
                """;
    }

    private String invalidRoleBpmn() {
        return validBpmn().replace("candidateRoleCode=\"SCIENCE_ADMIN\"", "candidateRoleCode=\"MISSING_ROLE\"");
    }
}
