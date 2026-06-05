package com.amatrix.sicprojectis_backend.workflow;

import java.util.List;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.UploadWorkflowDefinitionRequest;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowAssetResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowBpmnResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowDefinitionDetailResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowDefinitionSummaryResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowNodeResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowTransitionResponse;
import com.amatrix.sicprojectis_backend.workflow.dto.WorkflowValidationResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-definitions")
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService workflowDefinitionService;

    public WorkflowDefinitionController(WorkflowDefinitionService workflowDefinitionService) {
        this.workflowDefinitionService = workflowDefinitionService;
    }

    @PostMapping("/upload")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:upload')")
    public ApiResponse<WorkflowDefinitionSummaryResponse> upload(@RequestBody UploadWorkflowDefinitionRequest request) {
        return ApiResponse.ok(workflowDefinitionService.upload(request));
    }

    @GetMapping("/assets")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:view')")
    public ApiResponse<List<WorkflowAssetResponse>> assets() {
        return ApiResponse.ok(workflowDefinitionService.listAssets());
    }

    @PostMapping("/assets/{assetName}/upload")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:upload')")
    public ApiResponse<WorkflowDefinitionSummaryResponse> uploadAsset(@PathVariable String assetName) {
        return ApiResponse.ok(workflowDefinitionService.uploadAsset(assetName));
    }

    @PostMapping("/assets/{assetName}/publish")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:publish')")
    public ApiResponse<WorkflowDefinitionDetailResponse> publishAsset(@PathVariable String assetName) {
        return ApiResponse.ok(workflowDefinitionService.publishAsset(assetName));
    }

    @PostMapping("/{workflowDefinitionId}/validate")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:validate')")
    public ApiResponse<WorkflowValidationResponse> validate(@PathVariable Long workflowDefinitionId) {
        return ApiResponse.ok(workflowDefinitionService.validate(workflowDefinitionId));
    }

    @PostMapping("/{workflowDefinitionId}/publish")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:publish')")
    public ApiResponse<WorkflowDefinitionDetailResponse> publish(@PathVariable Long workflowDefinitionId) {
        return ApiResponse.ok(workflowDefinitionService.publish(workflowDefinitionId));
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:view')")
    public ApiResponse<List<WorkflowDefinitionSummaryResponse>> list() {
        return ApiResponse.ok(workflowDefinitionService.listDefinitions());
    }

    @GetMapping("/latest")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:view')")
    public ApiResponse<WorkflowDefinitionSummaryResponse> latest(@RequestParam String moduleType) {
        return ApiResponse.ok(workflowDefinitionService.getLatestActiveDefinition(moduleType));
    }

    @GetMapping("/{workflowDefinitionId}")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:view')")
    public ApiResponse<WorkflowDefinitionDetailResponse> detail(@PathVariable Long workflowDefinitionId) {
        return ApiResponse.ok(workflowDefinitionService.getDefinition(workflowDefinitionId));
    }

    @GetMapping("/{workflowDefinitionId}/bpmn")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:view')")
    public ApiResponse<WorkflowBpmnResponse> bpmn(@PathVariable Long workflowDefinitionId) {
        return ApiResponse.ok(workflowDefinitionService.getBpmn(workflowDefinitionId));
    }

    @GetMapping("/{workflowDefinitionId}/nodes")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:view')")
    public ApiResponse<List<WorkflowNodeResponse>> nodes(@PathVariable Long workflowDefinitionId) {
        return ApiResponse.ok(workflowDefinitionService.getNodes(workflowDefinitionId));
    }

    @GetMapping("/{workflowDefinitionId}/transitions")
    @PreAuthorize("@permissionService.hasPermission(authentication.principal.userId(), 'workflow:definition:view')")
    public ApiResponse<List<WorkflowTransitionResponse>> transitions(@PathVariable Long workflowDefinitionId) {
        return ApiResponse.ok(workflowDefinitionService.getTransitions(workflowDefinitionId));
    }
}
