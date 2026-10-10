package com.amatrix.sic.project.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;

@FeignClient(name = "workflow-service", contextId = "projectWorkflowSagaClient")
public interface WorkflowSagaClient {
    @PostMapping("/internal/application-sagas")
    ApiResponse<ApplicationSagaResult> create(@RequestHeader("Authorization") String serviceAuthorization,
            @RequestHeader("X-User-Authorization") String userAuthorization,
            @RequestBody ApplicationSagaCommand command);

    @GetMapping("/internal/application-sagas/{sagaId}")
    ApiResponse<ApplicationSagaResult> status(@RequestHeader("Authorization") String serviceAuthorization,
            @PathVariable UUID sagaId);

    @DeleteMapping("/internal/application-sagas/{sagaId}")
    ApiResponse<Void> compensate(@RequestHeader("Authorization") String serviceAuthorization,
            @PathVariable UUID sagaId);
}
