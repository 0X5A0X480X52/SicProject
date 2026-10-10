package com.amatrix.sic.material;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.MaterialReservationCommand;
import com.amatrix.sic.contracts.MaterialReservationResult;

@RestController
@RequestMapping("/internal/material-reservations")
public class InternalMaterialReservationController{
    private final MaterialReservationService service;public InternalMaterialReservationController(MaterialReservationService service){this.service=service;}
    @PostMapping ApiResponse<MaterialReservationResult> reserve(@RequestBody MaterialReservationCommand command){return ApiResponse.ok(service.reserve(command));}
    @PostMapping("/{id}/confirm") ApiResponse<Void> confirm(@PathVariable String id){service.confirm(id);return ApiResponse.ok(null);}
    @DeleteMapping("/{id}") ApiResponse<Void> release(@PathVariable String id){service.release(id);return ApiResponse.ok(null);}
}
