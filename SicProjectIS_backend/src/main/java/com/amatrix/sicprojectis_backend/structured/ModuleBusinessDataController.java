package com.amatrix.sicprojectis_backend.structured;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dto.ModuleBusinessDataResponse;

@RestController
@RequestMapping("/api/module-instances")
public class ModuleBusinessDataController {
    private final ModuleBusinessDataService service;
    public ModuleBusinessDataController(ModuleBusinessDataService service) { this.service = service; }
    @GetMapping("/{moduleInstanceId}/business-data")
    public ApiResponse<ModuleBusinessDataResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long moduleInstanceId) {
        return ApiResponse.ok(service.get(user, moduleInstanceId));
    }
}
