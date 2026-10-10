package com.amatrix.sic.workflow.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.security.ApiExceptionHandler;
import com.amatrix.sic.workflow.controller.WorkflowDefinitionController;
import com.amatrix.sic.workflow.service.definition.WorkflowDefinitionService;
import com.amatrix.sic.workflow.controller.ExpertReviewController;
import com.amatrix.sic.workflow.service.review.ExpertReviewService;
import com.amatrix.sic.workflow.controller.ModuleBusinessDataController;
import com.amatrix.sic.workflow.controller.NodeFormController;
import com.amatrix.sic.workflow.service.form.NodeFormService;
import com.amatrix.sic.workflow.dto.WorkflowDtos.TransitionRequest;
import com.amatrix.sic.workflow.controller.WorkflowRuntimeController;
import com.amatrix.sic.workflow.service.runtime.WorkflowRuntimeService;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

public abstract class WorkflowContractBase {
    @BeforeEach
    void setUpContractProvider() {
        WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
        WorkflowDefinitionService definitions = mock(WorkflowDefinitionService.class);
        StateRecordContract state = new StateRecordContract(101L, 20L, 2, 1, "SUBMIT", "DRAFT",
                "DEPT_REVIEW", "application_draft", "department_review", "SUBMITTED", "Ready",
                LocalDateTime.parse("2030-01-02T03:04:05"));
        StateTransitionContract transition = new StateTransitionContract(
                state, "department_review", "DEPT_REVIEW", false);
        when(runtime.start(eq(10L), eq("APPLICATION"), any(ActorContext.class))).thenReturn(transition);
        when(runtime.transition(eq(20L), any(ActorContext.class), any(TransitionRequest.class))).thenReturn(transition);

        Map<String, Object> context = linked("moduleInstanceId", 20L, "projectId", 10L,
                "moduleType", "APPLICATION", "workflowDefinitionId", 7L, "currentSeq", 2,
                "currentRoundNo", 1, "currentState", "DEPT_REVIEW", "currentNodeId", "department_review",
                "currentWorkflowNodeId", 70L, "currentNodeName", "Department review", "currentNodeType", "USER_TASK",
                "currentCandidateRoleCode", "DEPT_ADMIN", "currentLaneName", null,
                "currentResponsibleActorName", null, "currentOperationMode", null,
                "currentRepresentedActorName", null, "lastEventType", "SUBMIT", "lastResult", "SUBMITTED",
                "lastSummary", "Ready", "lastTransitionTime", "2030-01-02T03:04:05",
                "startedAt", "2030-01-01T00:00:00", "finishedAt", null);
        when(runtime.runtimeView(eq(20L), any(ActorContext.class))).thenReturn(linked(
                "context", context, "canOperate", true,
                "availableTransitions", List.of(linked("transitionId", "approve", "eventType", "APPROVE",
                        "result", "APPROVED", "targetRef", "science_review", "targetStateCode", "SCIENCE_REVIEW",
                        "conditionType", null, "conditionKey", null, "conditionValue", null,
                        "conditionHandlerKey", null, "actionKeys", List.of())),
                "materialRequirements", List.of(), "nodeForms", List.of(), "openTasks", List.of(),
                "history", List.of(state)));
        when(runtime.workbench(any(ActorContext.class))).thenReturn(List.of(linked(
                "moduleInstanceId", 20L, "projectId", 10L, "projectCode", "P-001", "projectName", "Demo",
                "moduleType", "APPLICATION", "lifecycleStage", "APPLICATION", "workflowDefinitionId", 7L,
                "currentState", "DEPT_REVIEW", "currentNodeId", "department_review",
                "currentNodeName", "Department review", "candidateRoleCode", "DEPT_ADMIN",
                "currentSeq", 2, "currentRoundNo", 1, "lastTransitionTime", "2030-01-02T03:04:05",
                "finished", false, "todo", true, "canOperate", true)));

        when(definitions.bpmn(7L)).thenReturn(linked("workflowDefinitionId", 7L,
                "bpmnXml", "<definitions id=\"application\"/>"));
        when(definitions.nodes(7L)).thenReturn(List.of(linked("nodeId", "department_review",
                "nodeName", "Department review", "nodeType", "USER_TASK", "stateCode", "DEPT_REVIEW",
                "laneName", null, "responsibleActorCode", null, "responsibleActorName", null,
                "candidateRoleCode", "DEPT_ADMIN", "operationMode", null,
                "representedActorCode", null, "representedActorName", null,
                "materialRequirements", List.of(), "documentConfigs", List.of())));

        ExpertReviewService reviews = mock(ExpertReviewService.class);
        NodeFormService forms = mock(NodeFormService.class);
        Map<String, Object> reviewDetail = linked(
                "batch", linked("batchId", 30L, "moduleInstanceId", 20L, "reviewType", "TECHNICAL",
                        "reviewTitle", "Technical review", "status", "OPEN"),
                "assignments", List.of(linked("assignment", linked("assignmentId", 40L, "expertUserId", 7L,
                        "expertName", "Bob", "reviewStatus", "ASSIGNED"), "scores", List.of())));
        when(reviews.create(any(ActorContext.class), any())).thenReturn(reviewDetail);
        when(reviews.detail(any(ActorContext.class), eq(30L))).thenReturn(reviewDetail);
        when(reviews.assign(any(ActorContext.class), eq(30L), any())).thenReturn(reviewDetail);
        when(reviews.submit(any(ActorContext.class), eq(40L), any())).thenReturn(reviewDetail);
        when(reviews.remove(any(ActorContext.class), eq(40L))).thenReturn(reviewDetail);

        Map<String, Object> singleDefinition = linked("formCode", "PROJECT_APPLICATION_FORM",
                "moduleType", "APPLICATION", "nodeId", "SubmitApplicationTask",
                "stateCode", "APPLICATION_DRAFT", "title", "Project application",
                "dataKind", "APPLICATION_DRAFT", "writeMode", "SINGLE_INSTANCE", "supportsFiles", true,
                "materialTypeCodes", List.of());
        Map<String, Object> historyDefinition = linked("formCode", "APPLICATION_NOTICE_FORM",
                "moduleType", "APPLICATION", "nodeId", "PublishNoticeTask",
                "stateCode", "APPLICATION_NOTICE_PUBLISHING", "title", "Application notice",
                "dataKind", "NOTICE", "writeMode", "HISTORY_RECORD", "supportsFiles", true,
                "materialTypeCodes", List.of());
        Map<String, Object> formData = linked("definition", singleDefinition,
                "context", linked("projectId", 10L, "moduleInstanceId", 20L, "stateRecordId", 101L),
                "applicationDraft", linked("title", "Demo application"),
                "notices", List.of(), "checkItems", List.of(), "externalResults", List.of(),
                "sealRecords", List.of(), "submissionRecords", List.of(), "archiveRecords", List.of(),
                "publicities", List.of(), "financialSettlements", List.of(), "achievements", List.of(),
                "surplusReturns", List.of(), "materials", List.of());
        Map<String, Object> historyData = linked("definition", historyDefinition,
                "context", linked("projectId", 10L, "moduleInstanceId", 20L, "stateRecordId", 101L),
                "applicationDraft", null,
                "notices", List.of(linked("title", "Application notice")),
                "checkItems", List.of(), "externalResults", List.of(), "sealRecords", List.of(),
                "submissionRecords", List.of(), "archiveRecords", List.of(), "publicities", List.of(),
                "financialSettlements", List.of(), "achievements", List.of(), "surplusReturns", List.of(),
                "materials", List.of());
        Map<String, Object> formRecord = linked("definition", historyDefinition, "recordId", 5L,
                "data", historyData);
        when(forms.businessData(20L)).thenReturn(linked("moduleInstanceId", 20L,
                "applicationDraft", linked("title", "Demo application")));
        when(forms.definitions()).thenReturn(List.of(singleDefinition, historyDefinition));
        when(forms.get("PROJECT_APPLICATION_FORM", null, null, null)).thenReturn(formData);
        when(forms.save(eq(20L), eq("PROJECT_APPLICATION_FORM"), anyMap(), any(ActorContext.class))).thenReturn(formData);
        when(forms.createRecord(eq(20L), eq("APPLICATION_NOTICE_FORM"), anyMap(), any(ActorContext.class)))
                .thenReturn(formRecord);
        when(forms.updateRecord(eq(20L), eq("APPLICATION_NOTICE_FORM"), eq(5L), anyMap(), any(ActorContext.class)))
                .thenReturn(formRecord);
        doNothing().when(forms).deleteRecord(eq(20L), eq("APPLICATION_NOTICE_FORM"), eq(5L), any(ActorContext.class));

        Jwt jwt = Jwt.withTokenValue("contract.jwt.token").header("alg", "none").subject("1")
                .claim("username", "alice").claim("roles", List.of("PROJECT_LEADER", "DEPT_ADMIN"))
                .claim("permissions", List.of("PERM_WORKFLOW_OPERATE", "PERM_WORKFLOW_DEFINITION_VIEW"))
                .claim("ver", 3L).claim("jti", "contract-jti")
                .issuedAt(Instant.parse("2029-01-02T03:04:05Z")).expiresAt(Instant.parse("2030-01-02T03:04:05Z"))
                .build();
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(
                        new WorkflowRuntimeController(runtime), new WorkflowDefinitionController(definitions),
                        new ExpertReviewController(reviews), new NodeFormController(forms),
                        new ModuleBusinessDataController(forms))
                .setControllerAdvice(new ApiExceptionHandler()).setCustomArgumentResolvers(jwtResolver(jwt)).build());
    }

    private static HandlerMethodArgumentResolver jwtResolver(Jwt jwt) {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && Jwt.class.isAssignableFrom(parameter.getParameterType());
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory binderFactory) { return jwt; }
        };
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
