package com.amatrix.sic.workflow;

import java.time.Instant;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.UserSummary;

@FeignClient(name="iam-service",contextId="workflowServiceTokenClient")
public interface WorkflowServiceTokenClient {
    @PostMapping("/internal/tokens/service")
    ApiResponse<Token> token(@RequestHeader("X-Service-Bootstrap-Secret")String secret,
            @RequestParam("serviceName")String serviceName);
    @GetMapping("/internal/users/{userId}/summary")
    ApiResponse<UserSummary> summary(@RequestHeader("Authorization") String authorization,
            @PathVariable Long userId);
    record Token(String token, Instant expiresAt, String tokenId){}
}
