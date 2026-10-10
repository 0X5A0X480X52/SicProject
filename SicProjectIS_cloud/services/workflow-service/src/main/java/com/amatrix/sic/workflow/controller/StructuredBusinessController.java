package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.business.StructuredBusinessService;

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
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class StructuredBusinessController {
    private final StructuredBusinessService service;

    public StructuredBusinessController(StructuredBusinessService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    ApiResponse<Map<String, Object>> application(@PathVariable Long projectId) {
        return ApiResponse.ok(service.application(projectId));
    }

    @PutMapping("/applications")
    ApiResponse<Map<String, Object>> saveApplication(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.saveApplication(projectId, ActorContextFactory.from(jwt), request));
    }

    @GetMapping("/contracts")
    ApiResponse<Map<String, Object>> contract(@PathVariable Long projectId) {
        return ApiResponse.ok(service.contract(projectId));
    }

    @PutMapping("/contracts")
    ApiResponse<Map<String, Object>> saveContract(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.saveContract(projectId, ActorContextFactory.from(jwt), request));
    }

    @GetMapping("/acceptances")
    ApiResponse<Map<String, Object>> acceptance(@PathVariable Long projectId) {
        return ApiResponse.ok(service.acceptance(projectId));
    }

    @PutMapping("/acceptances")
    ApiResponse<Map<String, Object>> saveAcceptance(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.saveAcceptance(projectId, ActorContextFactory.from(jwt), request));
    }

    @GetMapping("/achievements")
    ApiResponse<List<Map<String, Object>>> achievements(@PathVariable Long projectId) {
        return ApiResponse.ok(service.achievements(projectId));
    }

    @PostMapping("/achievements")
    ApiResponse<Map<String, Object>> addAchievement(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.addAchievement(projectId, ActorContextFactory.from(jwt), request));
    }

    @GetMapping("/acceptance-financial-settlements")
    ApiResponse<List<Map<String, Object>>> settlements(@PathVariable Long projectId) {
        return ApiResponse.ok(service.settlements(projectId));
    }

    @PostMapping("/acceptance-financial-settlements")
    ApiResponse<Map<String, Object>> addSettlement(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.addSettlement(projectId, ActorContextFactory.from(jwt), request));
    }
}
