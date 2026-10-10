package com.amatrix.sic.workflow;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.amatrix.sic.contracts.AccessDecision;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.contracts.ProjectAccessBatchRequest;
import com.amatrix.sic.contracts.ProjectAccessResult;
import java.util.Map;

@FeignClient(name="project-service",contextId="workflowProjectAccessClient")
public interface ProjectAccessClient {
    @GetMapping("/internal/projects/{projectId}/access")
    ApiResponse<AccessDecision> decide(@RequestHeader("Authorization")String authorization,@PathVariable Long projectId,
            @RequestParam Long userId,@RequestParam(required=false)Long deptId,@RequestParam String operation,
            @RequestParam List<String> roles);

    @PostMapping("/internal/projects/access-decisions")
    ApiResponse<List<ProjectAccessResult>> decideBatch(@RequestHeader("Authorization") String authorization,
            @RequestBody ProjectAccessBatchRequest request);

    @PostMapping("/internal/projects/{projectId}/expert-grants/sync")
    ApiResponse<Map<String, Object>> syncExpertGrant(@RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId, @RequestBody ExpertProjectGrantCommand command);

    @PostMapping("/internal/projects/{projectId}/expert-grants/revoke")
    ApiResponse<Map<String, Object>> revokeExpertGrant(@RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId, @RequestBody ExpertProjectGrantCommand command);
}
