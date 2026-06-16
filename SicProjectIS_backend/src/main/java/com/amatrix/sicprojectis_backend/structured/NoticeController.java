package com.amatrix.sicprojectis_backend.structured;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dto.NoticeUpsertRequest;
import com.amatrix.sicprojectis_backend.structured.entity.NoticeRecord;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final NoticeService service;
    public NoticeController(NoticeService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<NoticeRecord>> list(@RequestParam(required = false) String moduleType) { return ApiResponse.ok(service.list(moduleType)); }
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeRecord> get(@PathVariable Long noticeId) { return ApiResponse.ok(service.get(noticeId)); }
    @PostMapping
    @PreAuthorize("@permissionService.hasRole(authentication.principal.userId(), 'SCIENCE_ADMIN')")
    public ApiResponse<NoticeRecord> create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody NoticeUpsertRequest request) { return ApiResponse.ok(service.create(user, request)); }
    @PutMapping("/{noticeId}")
    @PreAuthorize("@permissionService.hasRole(authentication.principal.userId(), 'SCIENCE_ADMIN')")
    public ApiResponse<NoticeRecord> update(@PathVariable Long noticeId, @AuthenticationPrincipal AuthenticatedUser user, @RequestBody NoticeUpsertRequest request) { return ApiResponse.ok(service.update(noticeId, user, request)); }
}
