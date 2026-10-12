package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.business.NoticeService;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

/**
 * /api/notices 列表、详情、创建、更新 API。
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/notices}：查询当前账号可见的业务公告。
     * <p>可选 moduleType 按模块类型筛选；服务按 ALL、部门或用户公告范围过滤，科研/系统管理员具有管理可见范围。
     * <p>返回公告列表；模块类型会规范为大写，此查询不创建公告或流程待办。
     */
    @GetMapping
    ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String moduleType) {
        return ApiResponse.ok(service.list(ActorContextFactory.from(jwt), moduleType));
    }

    /**
     * {@code GET /api/notices/{noticeId}}：读取一个当前账号可见的公告详情。
     * <p>noticeId 为公告主键，服务检查公告范围；公告不存在或当前人员不可见均返回 404。
     * <p>返回公告内容、时间和目标范围等字段，不修改公告状态。
     */
    @GetMapping("/{noticeId}")
    ApiResponse<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long noticeId) {
        return ApiResponse.ok(service.get(ActorContextFactory.from(jwt), noticeId));
    }

    /**
     * {@code POST /api/notices}：创建申请、合同或验收链路使用的业务公告。
     * <p>要求 SCIENCE_ADMIN 或 SYSTEM_ADMIN；JSON 必填 moduleType、noticeType、noticeTitle，可配置范围、限额、内容和时间。
     * <p>deadlineTime 不得早于 startTime；返回新公告记录。公告创建与 Workflow 节点推进是不同操作。
     */
    @PostMapping
    ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.create(ActorContextFactory.from(jwt), request));
    }

    /**
     * {@code PUT /api/notices/{noticeId}}：更新已有公告的业务内容、时间与可见范围。
     * <p>要求 SCIENCE_ADMIN 或 SYSTEM_ADMIN；noticeId 指定公告，JSON 使用与创建相同的字段与必填校验。
     * <p>按请求重新写入公告配置，返回更新详情；这是完整字段更新入口，不能假设未提供字段都自动保留。
     */
    @PutMapping("/{noticeId}")
    ApiResponse<Map<String, Object>> update(@PathVariable Long noticeId, @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.update(noticeId, ActorContextFactory.from(jwt), request));
    }
}
