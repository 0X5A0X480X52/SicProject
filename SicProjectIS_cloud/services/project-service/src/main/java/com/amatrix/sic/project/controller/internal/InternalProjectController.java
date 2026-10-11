package com.amatrix.sic.project.controller.internal;

import com.amatrix.sic.project.service.ProjectAuthorizationService;
import com.amatrix.sic.project.policy.ProjectAccessPolicy;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.AccessDecision;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.contracts.ProjectAccessBatchRequest;
import com.amatrix.sic.contracts.ProjectAccessResult;

import java.util.Map;

@RestController
@RequestMapping("/internal/projects")
public class InternalProjectController {
    private final ProjectAccessPolicy policy;
    private final ProjectAuthorizationService authorizations;
    private final com.amatrix.sic.project.service.ProjectFinanceReadinessService finance;
    public InternalProjectController(ProjectAccessPolicy policy, ProjectAuthorizationService authorizations,
            com.amatrix.sic.project.service.ProjectFinanceReadinessService finance) {
        this.policy = policy;
        this.authorizations = authorizations;
        this.finance = finance;
    }

    @GetMapping("/{projectId}/finance-readiness")
    ApiResponse<Map<String,Object>> financeReadiness(@PathVariable Long projectId, @RequestParam String moduleType,
            @RequestParam Integer roundNo, @RequestParam String taskNodeId) {
        return ApiResponse.ok(finance.readiness(projectId,moduleType,roundNo,taskNodeId));
    }

    @GetMapping("/{projectId}/access")
    ApiResponse<AccessDecision> access(@PathVariable Long projectId, @RequestParam Long userId,
            @RequestParam(required = false) Long deptId, @RequestParam(defaultValue = "READ") String operation,
            @RequestParam(defaultValue = "") List<String> roles) {
        return ApiResponse.ok(policy.decide(userId, deptId, roles, projectId, operation));
    }

    @PostMapping("/access-decisions")
    ApiResponse<List<ProjectAccessResult>> accessDecisions(@RequestBody ProjectAccessBatchRequest request) {
        return ApiResponse.ok(policy.decideBatch(request == null ? null : request.actor(),
                request == null ? List.of() : request.targets()));
    }

    @PostMapping("/{projectId}/expert-grants/sync")
    ApiResponse<Map<String, Object>> syncExpertGrant(@PathVariable Long projectId,
            @RequestBody ExpertProjectGrantCommand command) {
        return ApiResponse.ok(authorizations.syncExpertGrant(projectId, command));
    }

    @PostMapping("/{projectId}/expert-grants/revoke")
    ApiResponse<Map<String, Object>> revokeExpertGrant(@PathVariable Long projectId,
            @RequestBody ExpertProjectGrantCommand command) {
        return ApiResponse.ok(authorizations.revokeExpertGrant(projectId, command));
    }
}
