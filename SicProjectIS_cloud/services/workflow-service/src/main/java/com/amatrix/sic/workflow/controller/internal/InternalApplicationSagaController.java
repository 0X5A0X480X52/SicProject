package com.amatrix.sic.workflow.controller.internal;

import com.amatrix.sic.workflow.service.saga.ApplicationSagaWorkflowService;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.ApplicationSagaCommand;
import com.amatrix.sic.contracts.ApplicationSagaResult;

@RestController
@RequestMapping("/internal/application-sagas")
public class InternalApplicationSagaController {
    private final ApplicationSagaWorkflowService service;
    public InternalApplicationSagaController(ApplicationSagaWorkflowService service){this.service=service;}
    @PostMapping ApiResponse<ApplicationSagaResult> create(@RequestBody ApplicationSagaCommand command){return ApiResponse.ok(service.create(command));}
    @GetMapping("/{sagaId}") ApiResponse<ApplicationSagaResult> status(@PathVariable UUID sagaId){ApplicationSagaResult result=service.find(sagaId);if(result==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Application saga not found");return ApiResponse.ok(result);}
    @DeleteMapping("/{sagaId}") ApiResponse<Void> compensate(@PathVariable UUID sagaId){service.compensate(sagaId);return ApiResponse.ok(null);}
}
