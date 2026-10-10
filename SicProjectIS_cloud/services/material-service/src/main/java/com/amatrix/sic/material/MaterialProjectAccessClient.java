package com.amatrix.sic.material;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import com.amatrix.sic.contracts.AccessDecision;
import com.amatrix.sic.contracts.ApiResponse;

@FeignClient(name="project-service",contextId="materialProjectAccessClient")
public interface MaterialProjectAccessClient{
    @GetMapping("/internal/projects/{projectId}/access") ApiResponse<AccessDecision> decide(@RequestHeader("Authorization")String authorization,@PathVariable Long projectId,@RequestParam Long userId,@RequestParam(required=false)Long deptId,@RequestParam String operation,@RequestParam List<String>roles);
}
