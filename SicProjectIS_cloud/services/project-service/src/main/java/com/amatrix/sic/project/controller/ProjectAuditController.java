package com.amatrix.sic.project.controller;

import com.amatrix.sic.project.service.ProjectAuditService;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;

/**
 * GET /api/admin/overview 与 /api/admin/audit-logs，提供管理概览和筛选日志。
 */
@RestController
@RequestMapping("/api/admin")
public class ProjectAuditController {
    private final ProjectAuditService service;
    public ProjectAuditController(ProjectAuditService service) { this.service=service; }

    /**
     * {@code GET /api/admin/overview}：汇总后台管理首页的账号、权限、项目授权和审计统计。
     * <p>要求 PERM_ADMIN_OVERVIEW；查询 IAM 统计并组合 Project 本地有效授权数量、日志数量及类型分布。
     * <p>返回概览对象；依赖 IAM 的统计不可用时不伪造为零，不执行任何管理变更。
     */
    @GetMapping("/overview")
    ApiResponse<Map<String,Object>> overview(){return ApiResponse.ok(service.overview());}

    /**
     * {@code GET /api/admin/audit-logs}：按条件查询管理员操作与项目授权审计日志。
     * <p>要求 PERM_AUDIT_READ；可按 keyword、actionType、scopeType、projectId、operatorUserId 及日期区间筛选。
     * <p>dateFrom、dateTo 使用 YYYY-MM-DD，结束日期按次日零点排他上界处理，即包含 dateTo 当天。
     * <p>返回 logs，并组装操作人、目标用户摘要；此接口没有分页参数，调用方应使用条件缩小结果范围。
     */
    @GetMapping("/audit-logs")
    ApiResponse<Map<String,Object>> logs(@RequestParam(required=false)String keyword,
            @RequestParam(required=false)String actionType,@RequestParam(required=false)String scopeType,
            @RequestParam(required=false)Long projectId,@RequestParam(required=false)Long operatorUserId,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate dateFrom,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate dateTo){
        return ApiResponse.ok(service.logs(keyword,actionType,scopeType,projectId,operatorUserId,dateFrom,dateTo));
    }
}
