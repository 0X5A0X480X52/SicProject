package com.amatrix.sic.workflow.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.MaterialReservationCommand;
import com.amatrix.sic.contracts.MaterialReservationResult;

@FeignClient(name="material-service",contextId="workflowMaterialReservationClient")
public interface MaterialReservationClient {
    @PostMapping("/internal/material-reservations")
    ApiResponse<MaterialReservationResult> reserve(@RequestHeader("Authorization")String authorization,
            @RequestBody MaterialReservationCommand command);
    @DeleteMapping("/internal/material-reservations/{reservationId}")
    ApiResponse<Void> release(@RequestHeader("Authorization")String authorization,@PathVariable String reservationId);
}
