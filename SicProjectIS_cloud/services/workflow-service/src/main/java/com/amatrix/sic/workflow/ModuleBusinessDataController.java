package com.amatrix.sic.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.amatrix.sic.contracts.ApiResponse;

@RestController
@RequestMapping("/api/module-instances")
public class ModuleBusinessDataController{
    private final NodeFormService service;public ModuleBusinessDataController(NodeFormService service){this.service=service;}
    @GetMapping("/{moduleInstanceId}/business-data") ApiResponse<Object> data(@PathVariable Long moduleInstanceId){return ApiResponse.ok(service.businessData(moduleInstanceId));}
}
