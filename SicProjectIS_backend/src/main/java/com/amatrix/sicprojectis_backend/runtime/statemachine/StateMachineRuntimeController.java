package com.amatrix.sicprojectis_backend.runtime.statemachine;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.RuntimeViewResponse;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StartModuleInstanceRequest;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionRequest;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api")
public class StateMachineRuntimeController {
    private final StateMachineRuntimeService service;

    public StateMachineRuntimeController(StateMachineRuntimeService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/module-instances")
    public ApiResponse<StateTransitionResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long projectId, @RequestBody StartModuleInstanceRequest request) {
        return ApiResponse.ok(service.startModule(user, projectId, request));
    }

    @PostMapping("/module-instances/{moduleInstanceId}/transitions")
    public ApiResponse<StateTransitionResponse> transition(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long moduleInstanceId, @RequestBody StateTransitionRequest request) {
        return ApiResponse.ok(service.transition(user, moduleInstanceId, request));
    }

    @GetMapping("/module-instances/{moduleInstanceId}/runtime-view")
    public ApiResponse<RuntimeViewResponse> runtimeView(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long moduleInstanceId) {
        return ApiResponse.ok(service.runtimeView(user, moduleInstanceId));
    }
}
