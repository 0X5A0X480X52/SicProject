package com.amatrix.sic.iam.contract;

import com.amatrix.sic.iam.dto.IamDtos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;

import com.amatrix.sic.iam.service.AuthApplicationService;
import com.amatrix.sic.iam.controller.AuthController;
import com.amatrix.sic.iam.controller.ExpertQualificationController;
import com.amatrix.sic.iam.service.ExpertQualificationService;
import com.amatrix.sic.iam.controller.IamAdminController;
import com.amatrix.sic.iam.service.IamAdminService;
import com.amatrix.sic.iam.dto.IamDtos.CurrentUser;
import com.amatrix.sic.iam.dto.IamDtos.LoginResponse;
import com.amatrix.sic.iam.dto.IamDtos.RegisterRequest;
import com.amatrix.sic.iam.dto.IamDtos.UserAccount;
import com.amatrix.sic.security.ApiExceptionHandler;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

public abstract class AuthContractBase {
    private static final Instant EXPIRES_AT = Instant.parse("2030-01-02T03:04:05Z");

    @BeforeEach
    void setUpContractProvider() {
        AuthApplicationService service = mock(AuthApplicationService.class);
        CurrentUser current = new CurrentUser(1L, "alice", "Alice", null, null,
                List.of("PROJECT_LEADER"), List.of("PERM_PROJECT_CREATE"));
        LoginResponse response = new LoginResponse("contract.jwt.token", EXPIRES_AT, current);
        UserAccount account = new UserAccount(1L, "alice", "unused", "Alice", null, null,
                null, null, true, 3L, current.roleCodes(), current.permissionCodes());

        when(service.login("alice", "secret123")).thenReturn(response);
        when(service.login("invalid", "wrong")).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        when(service.register(any(RegisterRequest.class))).thenReturn(response);
        when(service.current(1L)).thenReturn(account);
        doNothing().when(service).logout(any(), any());

        IamAdminService admin = mock(IamAdminService.class);
        ExpertQualificationService expert = mock(ExpertQualificationService.class);
        Map<String, Object> role = linked("roleId", 2L, "roleCode", "PROJECT_LEADER",
                "roleName", "Project leader", "roleDesc", null, "enabled", true);
        Map<String, Object> user = linked("userId", 7L, "username", "bob", "realName", "Bob",
                "deptId", 3L, "deptName", "Science", "enabled", true,
                "roleCodes", List.of("PROJECT_LEADER"), "canEditRoles", true, "canToggleStatus", true);
        Map<String, Object> userDetail = new LinkedHashMap<>(user);
        userDetail.put("phone", "13800000000");
        userDetail.put("email", null);
        Map<String, Object> userQuery = linked("roles", List.of(role), "users", List.of(user));
        Map<String, Object> permission = linked("permissionId", 5L, "permissionCode", "project:create",
                "permissionName", "Create project", "permissionGroup", "API", "permissionDesc", null);
        Map<String, Object> matrix = linked("roles", List.of(role), "permissions", List.of(permission),
                "matrix", List.of(linked("roleCode", "PROJECT_LEADER", "roleName", "Project leader",
                        "enabled", true, "permissionCodes", List.of("project:create"))));
        Map<String, Object> department = linked("deptId", 3L, "deptCode", "SCI",
                "deptName", "Science", "parentDeptId", null, "enabled", true);
        Map<String, Object> member = linked("userId", 7L, "username", "bob", "realName", "Bob",
                "deptId", 3L, "deptName", "Science", "phone", "13800000000", "email", null,
                "enabled", true, "roleCodes", List.of("PROJECT_LEADER"),
                "editableRoleCodes", List.of("PROJECT_LEADER", "EXPERT", "FINANCE_ADMIN"));
        Map<String, Object> diff = linked("added", List.of("PROJECT_LEADER"), "removed", List.of());
        Map<String, Object> application = linked("applicationId", 11L,
                "applicant", linked("userId", 7L, "username", "bob", "realName", "Bob", "deptId", 3L,
                        "deptName", "Science", "roleCodes", List.of()),
                "applicantDeptId", 3L, "applicantDeptName", "Science", "specialty", "AI",
                "professionalTitle", "Engineer", "applicationReason", "Project review", "status", "PENDING_DEPT_REVIEW",
                "deptReviewer", null, "deptReviewOpinion", null, "deptReviewRemark", null,
                "deptReviewedAt", null, "scienceReviewer", null, "scienceReviewOpinion", null,
                "scienceReviewRemark", null, "scienceReviewedAt", null,
                "createdAt", "2030-01-02T03:04:05", "updatedAt", "2030-01-02T03:04:05");

        when(admin.queryUsers(null, null, null, null, null)).thenReturn(userQuery);
        when(admin.userDetail(7L)).thenReturn(userDetail);
        when(admin.updateRoles(1L, 7L, List.of("PROJECT_LEADER")))
                .thenReturn(linked("user", userDetail, "diff", diff, "query", userQuery));
        when(admin.updateStatus(1L, 7L, false)).thenReturn(userDetail);
        when(admin.permissionMatrix()).thenReturn(matrix);
        when(admin.permissions()).thenReturn(List.of(permission));
        when(admin.updateRolePermissions(1L, "PROJECT_LEADER", List.of("project:create")))
                .thenReturn(linked("roleCode", "PROJECT_LEADER", "diff", diff, "matrix", matrix));
        when(admin.departments(1L)).thenReturn(List.of(department));
        when(admin.createDepartment(org.mockito.ArgumentMatchers.eq(1L), anyMap())).thenReturn(department);
        when(admin.updateDepartment(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(3L), anyMap()))
                .thenReturn(department);
        doNothing().when(admin).deleteDepartment(1L, 3L);
        when(admin.departmentMembers(1L, 3L)).thenReturn(linked("departments", List.of(department),
                "assignableRoleCodes", List.of("PROJECT_LEADER", "EXPERT", "FINANCE_ADMIN"),
                "members", List.of(member), "canManageDepartments", true,
                "memberManagementDisabled", false, "disabledReason", null));
        when(admin.departmentMembers(1L, null)).thenReturn(linked("departments", List.of(department),
                "assignableRoleCodes", List.of("PROJECT_LEADER", "EXPERT", "FINANCE_ADMIN"),
                "members", List.of(member), "canManageDepartments", true,
                "memberManagementDisabled", false, "disabledReason", null));
        when(admin.candidates(1L, "bob")).thenReturn(linked("users", List.of(member)));
        when(admin.candidates(1L, null)).thenReturn(linked("users", List.of(member)));
        when(admin.upsertMember(1L, 3L, 7L, List.of("PROJECT_LEADER"))).thenReturn(member);
        when(admin.updateMemberRoles(1L, 7L, List.of("PROJECT_LEADER"))).thenReturn(member);
        when(admin.removeMember(1L, 7L)).thenReturn(member);

        when(expert.submit(org.mockito.ArgumentMatchers.eq(1L), anyMap())).thenReturn(application);
        when(expert.mine(1L)).thenReturn(linked("expert", false, "hasActiveApplication", true,
                "applications", List.of(application)));
        when(expert.query(1L)).thenReturn(linked("applications", List.of(application)));
        when(expert.departmentReview(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(11L), anyMap()))
                .thenReturn(application);
        when(expert.scienceReview(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(11L), anyMap()))
                .thenReturn(application);

        Jwt jwt = Jwt.withTokenValue("contract.jwt.token").header("alg", "none").subject("1")
                .claim("jti", "contract-jti").issuedAt(Instant.parse("2029-01-02T03:04:05Z"))
                .expiresAt(EXPIRES_AT).build();
        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && Jwt.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory binderFactory) {
                return jwt;
            }
        };
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new AuthController(service),
                        new IamAdminController(admin), new ExpertQualificationController(expert))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(principalResolver).build());
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}
