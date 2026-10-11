package com.amatrix.sic.workflow.controller;

import java.util.Map;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;
import com.amatrix.sic.workflow.service.runtime.WorkflowWorkbenchQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkflowWorkbenchController {
    private final WorkflowWorkbenchQueryService service;
    public WorkflowWorkbenchController(WorkflowWorkbenchQueryService service) {this.service=service;}
    @GetMapping("/api/workflow-workbench/query")
    ApiResponse<Map<String,Object>> query(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="VISIBLE") String view,@RequestParam(required=false) String moduleType,
            @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.query(ActorContextFactory.from(jwt),view,moduleType,keyword,page,pageSize));
    }
}
