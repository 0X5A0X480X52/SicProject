package com.amatrix.sicprojectis_backend.nodeform;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormContext;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDataResponse;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormDefinition;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormRecordResponse;
import com.amatrix.sicprojectis_backend.nodeform.common.NodeFormSaveRequest;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/node-forms")
public class NodeFormController {
    private final NodeFormService service;

    public NodeFormController(NodeFormService service) {
        this.service = service;
    }

    @GetMapping("/definitions")
    public ApiResponse<List<NodeFormDefinition>> definitions() {
        return ApiResponse.ok(service.definitions());
    }

    @GetMapping("/{formCode}")
    public ApiResponse<NodeFormDataResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String formCode, @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long moduleInstanceId, @RequestParam(required = false) Long stateRecordId) {
        return ApiResponse.ok(service.get(user, formCode, new NodeFormContext(projectId, moduleInstanceId, stateRecordId)));
    }

    @PutMapping("/{formCode}")
    public ApiResponse<NodeFormDataResponse> save(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String formCode, @RequestBody NodeFormSaveRequest request) {
        return ApiResponse.ok(service.save(user, formCode, request));
    }

    @PostMapping("/{formCode}/records")
    public ApiResponse<NodeFormRecordResponse> createRecord(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String formCode, @RequestBody NodeFormSaveRequest request) {
        return ApiResponse.ok(service.createRecord(user, formCode, request));
    }

    @PutMapping("/{formCode}/records/{recordId}")
    public ApiResponse<NodeFormRecordResponse> updateRecord(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String formCode, @PathVariable Long recordId, @RequestBody NodeFormSaveRequest request) {
        return ApiResponse.ok(service.updateRecord(user, formCode, recordId, request));
    }

    @DeleteMapping("/{formCode}/records/{recordId}")
    public ApiResponse<Void> deleteRecord(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String formCode,
            @PathVariable Long recordId, @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long moduleInstanceId, @RequestParam(required = false) Long stateRecordId) {
        service.deleteRecord(user, formCode, recordId, new NodeFormContext(projectId, moduleInstanceId, stateRecordId));
        return ApiResponse.ok(null);
    }
}
