package com.amatrix.sicprojectis_backend.structured;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dto.TransitionRequest;
import com.amatrix.sicprojectis_backend.structured.dto.TransitionResponse;

@RestController
@RequestMapping("/api/legacy/module-instances")
public class StructuredStateMachineController {
    private final StructuredStateMachineService service;
    public StructuredStateMachineController(StructuredStateMachineService service) { this.service = service; }
    @PostMapping("/{moduleInstanceId}/transitions")
    public ApiResponse<TransitionResponse> transition(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long moduleInstanceId, @RequestBody TransitionRequest request) {
        return ApiResponse.ok(service.transition(user, moduleInstanceId, request));
    }
}
