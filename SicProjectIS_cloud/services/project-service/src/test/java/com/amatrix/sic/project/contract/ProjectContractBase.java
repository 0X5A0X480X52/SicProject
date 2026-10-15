package com.amatrix.sic.project.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.project.service.saga.ProjectApplicationSagaService;
import com.amatrix.sic.project.controller.ProjectAdminController;
import com.amatrix.sic.project.controller.ProjectAuditController;
import com.amatrix.sic.project.service.ProjectAuditService;
import com.amatrix.sic.project.controller.ProjectAuthorizationController;
import com.amatrix.sic.project.service.ProjectAuthorizationService;
import com.amatrix.sic.project.controller.ProjectController;
import com.amatrix.sic.project.dto.ProjectDtos.StartProjectApplicationRequest;
import com.amatrix.sic.project.dto.ProjectDtos.StartProjectApplicationResponse;
import com.amatrix.sic.project.service.ProjectQueryService;
import com.amatrix.sic.security.ApiExceptionHandler;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

public abstract class ProjectContractBase {
    @BeforeEach
    void setUpContractProvider() {
        ProjectQueryService queries = mock(ProjectQueryService.class);
        ProjectApplicationSagaService sagas = mock(ProjectApplicationSagaService.class);
        Map<String, Object> project = linked("projectId", 10L, "projectCode", "P-001", "projectName", "Demo",
                "deptId", null, "deptName", null, "leaderUserId", 1L, "leaderRealName", "Alice",
                "projectType", null, "projectLevel", null, "lifecycleStage", "APPLICATION");
        when(queries.accessibleProjects(eq(1L), eq(null), anyList())).thenReturn(List.of(project));

        StateRecordContract state = new StateRecordContract(101L, 20L, 1, 1, "START", null,
                "DRAFT", null, "application_draft", null, null,
                LocalDateTime.parse("2030-01-02T03:04:05"));
        StateTransitionContract transition = new StateTransitionContract(state, "application_draft", "DRAFT", false);
        when(sagas.start(eq(1L), eq("Alice"), eq(null), eq(null), any(),
                any(StartProjectApplicationRequest.class)))
                .thenReturn(new StartProjectApplicationResponse(10L, 20L, transition));

        ProjectAuthorizationService authorizations = mock(ProjectAuthorizationService.class);
        ProjectAuditService audits = mock(ProjectAuditService.class);
        Map<String, Object> user = linked("userId", 7L, "username", "bob", "realName", "Bob",
                "deptId", 3L, "deptName", "Science", "roleCodes", List.of("EXPERT"));
        Map<String, Object> detail = linked("project", project, "leader", user,
                "members", List.of(), "expertGrants", List.of(), "financeGrants", List.of(),
                "proxyGrants", List.of(), "users", List.of(user),
                "capabilities", linked("canManageLeader", true, "canManageMembers", true,
                        "canManageExperts", true, "canManageFinance", false, "canManageProxy", true));
        Map<String, Object> mutation = linked("detail", detail,
                "diff", linked("added", List.of("7"), "removed", List.of()),
                "affectedCount", 1, "summaryMessage", "Updated 1 record");
        Map<String, Object> overview = linked("totalUsers", 12L, "enabledUsers", 11L,
                "totalRoles", 6L, "totalPermissions", 20L, "activeProjectGrants", 3L,
                "auditLogCount", 40L, "grantTypeCounts", List.of(), "roleCounts", List.of());
        Map<String, Object> auditLog = linked("logId", "101", "scopeType", "PROJECT",
                "actionType", "PROJECT_MEMBER_UPDATED", "projectId", 10L, "projectName", "Demo",
                "operatorUser", linked("userId", 1L, "username", "alice", "realName", "Alice"),
                "targetUser", linked("userId", 7L, "username", "bob", "realName", "Bob"),
                "grantType", null, "roleCode", null, "permissionCode", null,
                "beforeSnapshot", null, "afterSnapshot", "{}", "remark", "Updated",
                "createdAt", "2030-01-02T03:04:05");

        when(authorizations.detail(eq(10L), any(ActorContext.class))).thenReturn(detail);
        when(authorizations.changeLeader(eq(10L), any(ActorContext.class), eq(7L), eq("Handover")))
                .thenReturn(detail);
        when(authorizations.upsertMember(eq(10L), any(ActorContext.class), eq(7L), eq("Reviewer")))
                .thenReturn(detail);
        when(authorizations.upsertMembers(eq(10L), any(ActorContext.class), eq(List.of(7L)), eq("Reviewer")))
                .thenReturn(mutation);
        when(authorizations.removeMember(eq(10L), any(ActorContext.class), eq(7L))).thenReturn(detail);
        when(authorizations.assignGrant(eq(10L), any(ActorContext.class), any(), eq(7L),
                isNull(), isNull(), isNull(), eq("Assign"))).thenReturn(detail);
        when(authorizations.assignGrant(eq(10L), any(ActorContext.class),
                eq("PROJECT_MODULE_EXPERT_ASSIGNMENT"), eq(7L), eq("APPLICATION"), eq(1), eq("review"),
                eq("Assign"))).thenReturn(detail);
        when(authorizations.assignGrants(eq(10L), any(ActorContext.class), any(), eq(List.of(7L)),
                isNull(), isNull(), isNull(), eq("Assign"))).thenReturn(mutation);
        when(authorizations.assignGrants(eq(10L), any(ActorContext.class),
                eq("PROJECT_MODULE_EXPERT_ASSIGNMENT"), eq(List.of(7L)), eq("APPLICATION"), eq(1),
                eq("review"), eq("Assign"))).thenReturn(mutation);
        when(authorizations.revoke(eq(10L), any(ActorContext.class), eq(5L), eq("Revoke")))
                .thenReturn(detail);
        when(authorizations.revokeBatch(eq(10L), any(ActorContext.class), eq(List.of(5L)), eq("Revoke")))
                .thenReturn(mutation);
        when(authorizations.manageableProjects(any(ActorContext.class), isNull()))
                .thenReturn(linked("projects", List.of(project), "grantTypeCounts", List.of()));
        when(audits.overview()).thenReturn(overview);
        when(audits.logs(null, null, null, null, null, null, null))
                .thenReturn(linked("logs", List.of(auditLog)));

        Jwt jwt = Jwt.withTokenValue("contract.jwt.token").header("alg", "none").subject("1")
                .claim("realName", "Alice").claim("roles", List.of("PROJECT_LEADER"))
                .issuedAt(Instant.parse("2029-01-02T03:04:05Z")).expiresAt(Instant.parse("2030-01-02T03:04:05Z"))
                .build();
        var departments=mock(com.amatrix.sic.project.service.ProjectDepartmentService.class);
        when(departments.assignMissing(eq(10L),any(),eq(3L),eq("Historical department repair")))
                .thenReturn(linked("projectId",10L,"deptId",3L,"deptName","Science"));
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new ProjectController(queries, sagas),
                        new com.amatrix.sic.project.controller.ProjectDepartmentController(departments),
                        new ProjectAuthorizationController(authorizations), new ProjectAdminController(authorizations),
                        new ProjectAuditController(audits))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(jwtResolver(jwt)).build());
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
