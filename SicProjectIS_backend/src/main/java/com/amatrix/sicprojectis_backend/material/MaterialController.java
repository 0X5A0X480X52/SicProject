package com.amatrix.sicprojectis_backend.material;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.material.dto.MaterialUploadResponse;
import com.amatrix.sicprojectis_backend.material.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;

@RestController
public class MaterialController {
    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/projects/{projectId}/materials/{materialTypeCode}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MaterialUploadResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long projectId, @PathVariable String materialTypeCode, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(user, projectId, materialTypeCode, file));
    }

    @GetMapping("/api/projects/{projectId}/materials")
    public ApiResponse<List<MaterialContextView>> list(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long projectId) {
        return ApiResponse.ok(service.listProjectMaterials(user, projectId));
    }

    @GetMapping("/api/material-versions/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        Resource resource = service.download(user, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + service.downloadFileName(id))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/api/material-versions/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        service.deleteVersion(user, id);
        return ApiResponse.ok(null);
    }
}
