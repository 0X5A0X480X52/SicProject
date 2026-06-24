package com.amatrix.sicprojectis_backend.workbench;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.workbench.dto.WorkflowWorkbenchItemResponse;

@RestController
@RequestMapping("/api/workflow-workbench")
public class WorkflowWorkbenchController {
    private final WorkflowWorkbenchService service;

    public WorkflowWorkbenchController(WorkflowWorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/items")
    public ApiResponse<List<WorkflowWorkbenchItemResponse>> items(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.listItems(user));
    }
}
