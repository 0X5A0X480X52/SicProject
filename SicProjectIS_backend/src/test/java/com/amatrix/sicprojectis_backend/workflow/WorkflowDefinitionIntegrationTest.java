package com.amatrix.sicprojectis_backend.workflow;

import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowDefinitionIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserDao appUserDao;

    @Autowired
    private PasswordEncoder passwordEncoder;
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
                .andExpect(jsonPath("$.data.transitions[0].eventType").value("SUBMIT"));

        mockMvc.perform(get("/api/workflow-definitions").header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].workflowDefinitionId", hasItem(workflowDefinitionId.intValue())));

        mockMvc.perform(get("/api/workflow-definitions/" + workflowDefinitionId + "/nodes")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].materialRequirements", hasSize(1)))
                .andExpect(jsonPath("$.data[1].documentConfigs", hasSize(1)));
    }

    @Test
    void shouldRejectUnknownCandidateRoleDuringValidationAndPublish() throws Exception {
        String scienceAdminToken = login("carol", "password");
        Long workflowDefinitionId = upload(scienceAdminToken, invalidRoleBpmn());

        mockMvc.perform(post("/api/workflow-definitions/" + workflowDefinitionId + "/validate")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]").value(org.hamcrest.Matchers.containsString("Unknown candidateRoleCode")));

        mockMvc.perform(post("/api/workflow-definitions/" + workflowDefinitionId + "/publish")
                        .header("Authorization", "Bearer " + scienceAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unknown candidateRoleCode")));
    }

    @Test
    void shouldRejectWorkflowDefinitionAccessForDeptAdmin() throws Exception {
        String deptAdminToken = login("diana", "password");

        mockMvc.perform(get("/api/workflow-definitions").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden());
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
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:rm="http://example.com/research-management">
                  <bpmn:process id="contract_process" name="Contract Approval">
                    <bpmn:laneSet>
                      <bpmn:lane id="lane_science" name="Science Office">
                        <bpmn:flowNodeRef>submit_task</bpmn:flowNodeRef>
                      </bpmn:lane>
                    </bpmn:laneSet>
                    <bpmn:startEvent id="start_event" name="Start">
                      <bpmn:extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_DRAFT"
                                         responsibleActorCode="PROJECT_LEADER"
                                         responsibleActorName="Project Leader"
                                         candidateRoleCode="PROJECT_LEADER"
                                         operationMode="MANUAL"/>
                      </bpmn:extensionElements>
                    </bpmn:startEvent>
                    <bpmn:userTask id="submit_task" name="Science Review">
                      <bpmn:extensionElements>
                        <rm:workflowNode stateCode="CONTRACT_SCIENCE_REVIEW"
                                         responsibleActorCode="SCIENCE_ADMIN"
                                         responsibleActorName="Science Admin"
                                         candidateRoleCode="SCIENCE_ADMIN"
                                         operationMode="MANUAL">
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
                        </rm:workflowNode>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                    <bpmn:sequenceFlow id="flow_submit" sourceRef="start_event" targetRef="submit_task">
                      <bpmn:extensionElements>
                        <rm:transition eventType="SUBMIT"
                                       result="PASS"
                                       conditionType="ALWAYS"
                                       sourceStateCode="CONTRACT_DRAFT"
                                       targetStateCode="CONTRACT_SCIENCE_REVIEW"
                                       actionKeys="notify,record"/>
                      </bpmn:extensionElements>
                    </bpmn:sequenceFlow>
                  </bpmn:process>
                  <bpmn:extensionElements>
                    <rm:processConfig processKey="CONTRACT_PROCESS"
                                      processName="Contract Approval"
                                      moduleType="CONTRACT"
                                      versionNo="2"
                                      status="ACTIVE"/>
                  </bpmn:extensionElements>
                </bpmn:definitions>
                """;
    }

    private String invalidRoleBpmn() {
        return validBpmn().replace("candidateRoleCode=\"SCIENCE_ADMIN\"", "candidateRoleCode=\"MISSING_ROLE\"");
    }
}
