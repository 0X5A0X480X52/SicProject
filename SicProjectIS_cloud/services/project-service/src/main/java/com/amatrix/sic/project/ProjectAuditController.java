package com.amatrix.sic.project;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;

@RestController
@RequestMapping("/api/admin")
public class ProjectAuditController {
    private final ProjectAuditService service;
    public ProjectAuditController(ProjectAuditService service) { this.service=service; }

    @GetMapping("/overview")
    ApiResponse<Map<String,Object>> overview(){return ApiResponse.ok(service.overview());}

    @GetMapping("/audit-logs")
    ApiResponse<Map<String,Object>> logs(@RequestParam(required=false)String keyword,
            @RequestParam(required=false)String actionType,@RequestParam(required=false)String scopeType,
            @RequestParam(required=false)Long projectId,@RequestParam(required=false)Long operatorUserId,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate dateFrom,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate dateTo){
        return ApiResponse.ok(service.logs(keyword,actionType,scopeType,projectId,operatorUserId,dateFrom,dateTo));
    }
}
