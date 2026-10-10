package com.amatrix.sic.workflow;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String moduleType) {
        return ApiResponse.ok(service.list(ActorContextFactory.from(jwt), moduleType));
    }

    @GetMapping("/{noticeId}")
    ApiResponse<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long noticeId) {
        return ApiResponse.ok(service.get(ActorContextFactory.from(jwt), noticeId));
    }

    @PostMapping
    ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.create(ActorContextFactory.from(jwt), request));
    }

    @PutMapping("/{noticeId}")
    ApiResponse<Map<String, Object>> update(@PathVariable Long noticeId, @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.update(noticeId, ActorContextFactory.from(jwt), request));
    }
}
