package com.amatrix.sic.workflow;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.workflow.WorkflowDtos.UploadWorkflowDefinitionRequest;

@RestController
@RequestMapping("/api/workflow-definitions")
public class WorkflowDefinitionController {
    private final WorkflowDefinitionService service;

    public WorkflowDefinitionController(WorkflowDefinitionService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    ApiResponse<Map<String, Object>> upload(@RequestBody UploadWorkflowDefinitionRequest request) {
        return ApiResponse.ok(service.upload(request == null ? null : request.bpmnXml()));
    }

    @GetMapping("/assets")
    ApiResponse<List<Map<String, Object>>> assets() {
        return ApiResponse.ok(service.listAssets());
    }

    @PostMapping("/assets/{assetName}/upload")
    ApiResponse<Map<String, Object>> uploadAsset(@PathVariable String assetName) {
        return ApiResponse.ok(service.uploadAsset(assetName));
    }

    @PostMapping("/assets/{assetName}/publish")
    ApiResponse<Map<String, Object>> publishAsset(@PathVariable String assetName) {
        return ApiResponse.ok(service.publishAsset(assetName));
    }

    @PostMapping("/{id}/validate")
    ApiResponse<Map<String, Object>> validate(@PathVariable Long id) {
        return ApiResponse.ok(service.validate(id));
    }

    @PostMapping("/{id}/publish")
    ApiResponse<Map<String, Object>> publish(@PathVariable Long id) {
        return ApiResponse.ok(service.publish(id));
    }

    @GetMapping
    ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/latest")
    ApiResponse<Map<String, Object>> latest(@RequestParam String moduleType) {
        return ApiResponse.ok(service.latest(moduleType));
    }

    @GetMapping("/{id}")
    ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/{id}/bpmn")
    ApiResponse<Map<String, Object>> bpmn(@PathVariable Long id) {
        return ApiResponse.ok(service.bpmn(id));
    }

    @GetMapping("/{id}/nodes")
    ApiResponse<List<Map<String, Object>>> nodes(@PathVariable Long id) {
        return ApiResponse.ok(service.nodes(id));
    }

    @GetMapping("/{id}/transitions")
    ApiResponse<List<Map<String, Object>>> transitions(@PathVariable Long id) {
        return ApiResponse.ok(service.transitions(id));
    }
}
