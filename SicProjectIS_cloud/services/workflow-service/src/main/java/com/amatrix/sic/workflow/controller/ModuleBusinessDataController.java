package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.form.NodeFormService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.amatrix.sic.contracts.ApiResponse;

/**
 * GET /api/module-instances/{moduleInstanceId}/business-data，返回模块聚合业务数据。
 */
@RestController
@RequestMapping("/api/module-instances")
public class ModuleBusinessDataController{
    private final NodeFormService service;public ModuleBusinessDataController(NodeFormService service){this.service=service;}
    /**
     * {@code GET /api/module-instances/{moduleInstanceId}/business-data}：读取一个工作流模块聚合的业务草稿与评审数据。
     * <p>moduleInstanceId 是模块运行实例 ID；CanAccessModule 校验项目/模块可读范围。
     * <p>返回申请、合同或验收业务草稿并附 expertReviews，评审内容按当前访问身份裁剪。
     * <p>本接口不提供任意项目的聚合查询，也不保存草稿或修改运行时状态。
     */
    @GetMapping("/{moduleInstanceId}/business-data") ApiResponse<Object> data(@PathVariable Long moduleInstanceId){return ApiResponse.ok(service.businessData(moduleInstanceId));}
}
