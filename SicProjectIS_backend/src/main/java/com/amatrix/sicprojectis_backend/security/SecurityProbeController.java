package com.amatrix.sicprojectis_backend.security;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security/probe")
public class SecurityProbeController {
    @GetMapping("/project-view")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'project:view')")
    public ApiResponse<String> projectView() {
        return ApiResponse.ok("allowed");
    }
}
