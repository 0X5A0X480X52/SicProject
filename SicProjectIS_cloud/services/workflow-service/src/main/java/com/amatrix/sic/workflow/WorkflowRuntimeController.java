package com.amatrix.sic.workflow;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.StateTransitionContract;
import com.amatrix.sic.security.ActorContextFactory;
import com.amatrix.sic.workflow.WorkflowDtos.StartModuleRequest;
import com.amatrix.sic.workflow.WorkflowDtos.TransitionRequest;

@RestController
public class WorkflowRuntimeController {
    private final WorkflowRuntimeService service;
    public WorkflowRuntimeController(WorkflowRuntimeService service){this.service=service;}

    @PostMapping("/api/projects/{projectId}/module-instances")
    ApiResponse<StateTransitionContract> start(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody StartModuleRequest request){return ApiResponse.ok(service.start(projectId,request.moduleType(),ActorContextFactory.from(jwt)));}

    @PostMapping("/api/module-instances/{moduleInstanceId}/transitions")
    ApiResponse<StateTransitionContract> transition(@AuthenticationPrincipal Jwt jwt,@PathVariable Long moduleInstanceId,
            @RequestBody TransitionRequest request){return ApiResponse.ok(service.transition(moduleInstanceId,ActorContextFactory.from(jwt),request));}

    @PostMapping("/api/legacy/module-instances/{moduleInstanceId}/transitions")
    ApiResponse<StateTransitionContract> legacyTransition(@AuthenticationPrincipal Jwt jwt,@PathVariable Long moduleInstanceId,
            @RequestBody TransitionRequest request){return ApiResponse.ok(service.transition(moduleInstanceId,ActorContextFactory.from(jwt),request));}

    @GetMapping("/api/module-instances/{moduleInstanceId}/runtime-view")
    ApiResponse<Map<String,Object>> view(@AuthenticationPrincipal Jwt jwt,@PathVariable Long moduleInstanceId){return ApiResponse.ok(service.runtimeView(moduleInstanceId,ActorContextFactory.from(jwt)));}

    @GetMapping("/api/workflow-workbench/items")
    ApiResponse<List<Map<String,Object>>> workbench(@AuthenticationPrincipal Jwt jwt){return ApiResponse.ok(service.workbench(ActorContextFactory.from(jwt)));}
}
