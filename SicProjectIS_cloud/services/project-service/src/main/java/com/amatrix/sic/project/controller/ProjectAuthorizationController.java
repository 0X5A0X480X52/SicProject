package com.amatrix.sic.project.controller;

import com.amatrix.sic.project.service.ProjectAuthorizationService;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

/**
 * 项目授权详情、负责人更换、成员单条/批量变更、专家/财务/代录单条/批量授权及撤销 API。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectAuthorizationController {
    private final ProjectAuthorizationService service;

    public ProjectAuthorizationController(ProjectAuthorizationService service) { this.service = service; }

    /**
     * {@code GET /api/projects/{projectId}/authorization}：读取项目授权详情及当前操作人的管理能力。
     * <p>要求可访问该项目；返回项目、负责人、成员、各类授权、候选用户及 capabilities，供授权页展示和控制操作入口。
     * <p>capabilities 分别对应负责人、成员、专家、财务、代录管理；具备项目读取权限不等于具备全部变更能力。
     */
    @GetMapping("/{projectId}/authorization")
    ApiResponse<Map<String, Object>> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId) {
        return ApiResponse.ok(service.detail(projectId, actor(jwt)));
    }

    /**
     * {@code PUT /api/projects/{projectId}/leader}：更换项目负责人并同步负责人/成员关系。
     * <p>仅 SYSTEM_ADMIN 或 SCIENCE_ADMIN 可管理负责人；JSON userId 指定新负责人，可提供 reason 记录变更原因。
     * <p>新负责人必须存在且启用；服务更新项目与成员、记录审计及事件，返回更新后的授权详情。
     */
    @PutMapping("/{projectId}/leader")
    ApiResponse<Map<String, Object>> leader(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.changeLeader(projectId, actor(jwt), number(request.get("userId")),
                string(request.get("reason"))));
    }

    /**
     * {@code PUT /api/projects/{projectId}/members}：新增或更新一个项目成员及其承担职责。
     * <p>要求项目可访问，且为全局管理员、负责人或同部门 DEPT_ADMIN；JSON userId 指定成员，responsibility 为职责说明。
     * <p>服务写成员关系并记录审计及项目变更，返回授权详情；这里配置项目成员，不改变该用户的全局角色或部门。
     */
    @PutMapping("/{projectId}/members")
    ApiResponse<Map<String, Object>> member(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.upsertMember(projectId, actor(jwt), number(request.get("userId")),
                string(request.get("responsibility"))));
    }

    /**
     * {@code PUT /api/projects/{projectId}/members/batch}：在一个本地事务中批量新增或更新项目成员。
     * <p>管理资格与单条成员接口相同；JSON userIds 为成员集合，responsibility 应用于本次集合。
     * <p>统一校验候选用户后写入，最后组装一次详情；返回 detail、diff、affectedCount、summaryMessage。
     * <p>批量操作保持整体事务语义，不逐条独立提交；affectedCount 是处理的去重目标数，不等同于新增行数。
     */
    @PutMapping("/{projectId}/members/batch")
    ApiResponse<Map<String, Object>> members(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.upsertMembers(projectId, actor(jwt), numbers(request.get("userIds")),
                string(request.get("responsibility"))));
    }

    /**
     * {@code DELETE /api/projects/{projectId}/members/{userId}}：移除项目成员关系，收回该关系带来的项目访问资格。
     * <p>要求成员管理能力；projectId、userId 分别指定项目和成员，当前负责人不能通过此接口删除。
     * <p>返回更新后的授权详情并记录审计及事件；用户若另有管理员身份或有效项目授权，仍可能可访问该项目。
     */
    @DeleteMapping("/{projectId}/members/{userId}")
    ApiResponse<Map<String, Object>> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @PathVariable Long userId) {
        return ApiResponse.ok(service.removeMember(projectId, actor(jwt), userId));
    }

    /**
     * {@code POST /api/projects/{projectId}/expert-grants}：为指定专家配置项目范围的临时评审授权。
     * <p>要求专家管理能力（全局管理员、负责人或本部门 DEPT_ADMIN），目标 userId 必须具有 EXPERT 角色。
     * <p>JSON 可包含 moduleType、roundNo、taskNodeId、reason，分别约束模块、轮次、节点及说明；不传范围字段按服务匹配规则处理。
     * <p>返回授权详情；这里只建立访问授权，不创建 Workflow 评审批次或 assignment，正式指派使用评审接口。
     */
    @PostMapping("/{projectId}/expert-grants")
    ApiResponse<Map<String, Object>> expert(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grant(projectId, jwt, "PROJECT_MODULE_EXPERT_ASSIGNMENT", request));
    }

    /**
     * {@code POST /api/projects/{projectId}/expert-grants/batch}：批量配置同一范围的专家项目授权。
     * <p>要求专家管理能力；JSON userIds 全部须具有 EXPERT 角色，moduleType、roundNo、taskNodeId、reason 作用于全部目标。
     * <p>一个事务内统一校验和写入，返回 detail、diff、affectedCount、summaryMessage；不创建评分任务。
     * <p>已有同范围有效授权可复用，处理数量不代表实际插入数量。
     */
    @PostMapping("/{projectId}/expert-grants/batch")
    ApiResponse<Map<String, Object>> experts(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grants(projectId, jwt, "PROJECT_MODULE_EXPERT_ASSIGNMENT", request));
    }

    /**
     * {@code POST /api/projects/{projectId}/finance-grants}：为财务人员配置项目经办授权，使其能够办理匹配的财务节点。
     * <p>仅全局管理员可管理财务授权；JSON userId 必须具有 FINANCE_ADMIN 角色，可附 moduleType、roundNo、taskNodeId、reason。
     * <p>返回授权详情并记录变更；办理时财务角色与有效项目授权必须同时满足，单独授予角色不会产生该项目待办。
     * <p>已有财务节点也可通过新增有效授权取得办理资格，不要求重新创建流程节点。
     */
    @PostMapping("/{projectId}/finance-grants")
    ApiResponse<Map<String, Object>> finance(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grant(projectId, jwt, "PROJECT_FINANCE_HANDLER_ASSIGNMENT", request));
    }

    /**
     * {@code POST /api/projects/{projectId}/finance-grants/batch}：在一个事务内批量配置财务经办授权。
     * <p>仅全局管理员操作；JSON userIds 全部须具有 FINANCE_ADMIN，范围字段 moduleType、roundNo、taskNodeId 和 reason 对集合统一生效。
     * <p>返回 detail、diff、affectedCount、summaryMessage；角色及授权范围仍在实际查询和办理时校验，不以推送作为授权凭据。
     */
    @PostMapping("/{projectId}/finance-grants/batch")
    ApiResponse<Map<String, Object>> finances(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grants(projectId, jwt, "PROJECT_FINANCE_HANDLER_ASSIGNMENT", request));
    }

    /**
     * {@code POST /api/projects/{projectId}/proxy-grants}：授权其他用户在指定项目范围代录业务。
     * <p>要求全局管理员或项目负责人；JSON userId 为代录人员，可附 moduleType、roundNo、taskNodeId、reason。
     * <p>返回更新后的授权详情；代录授权遵循运行时节点的 operationMode 与 representedActor 规则，不等于获得原业务角色。
     */
    @PostMapping("/{projectId}/proxy-grants")
    ApiResponse<Map<String, Object>> proxy(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grant(projectId, jwt, "PROJECT_PROXY_RECORDER_ASSIGNMENT", request));
    }

    /**
     * {@code POST /api/projects/{projectId}/proxy-grants/batch}：批量配置统一范围的项目代录授权。
     * <p>要求全局管理员或项目负责人；JSON userIds 指定代录人员，其他范围字段与单条代录接口一致。
     * <p>统一事务写入，返回 detail、diff、affectedCount、summaryMessage；不会改变用户的全局角色。
     */
    @PostMapping("/{projectId}/proxy-grants/batch")
    ApiResponse<Map<String, Object>> proxies(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(grants(projectId, jwt, "PROJECT_PROXY_RECORDER_ASSIGNMENT", request));
    }

    /**
     * {@code POST /api/projects/{projectId}/grants/{grantId}/revoke}：撤销项目中的一条专家、财务或代录授权。
     * <p>grantId 是项目授权记录 ID，JSON reason 为撤销说明；服务确认授权属于 projectId，并按授权类型校验管理能力。
     * <p>返回授权详情，记录审计及权限相关事件；撤销此记录不删除用户，也不撤销其其他成员关系或授权。
     */
    @PostMapping("/{projectId}/grants/{grantId}/revoke")
    ApiResponse<Map<String, Object>> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @PathVariable Long grantId, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.revoke(projectId, actor(jwt), grantId, string(request.get("reason"))));
    }

    /**
     * {@code POST /api/projects/{projectId}/grants/revoke-batch}：原子撤销同一项目内的一组授权记录。
     * <p>JSON grantIds 为授权记录 ID 集合，reason 为统一说明；逐项校验项目归属和对应类型的管理能力。
     * <p>保持单个本地事务，返回 detail、diff、affectedCount、summaryMessage；不将授权记录 ID 当作用户 ID。
     */
    @PostMapping("/{projectId}/grants/revoke-batch")
    ApiResponse<Map<String, Object>> revokeBatch(@AuthenticationPrincipal Jwt jwt, @PathVariable Long projectId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.revokeBatch(projectId, actor(jwt), numbers(request.get("grantIds")),
                string(request.get("reason"))));
    }

    private Map<String, Object> grant(Long projectId, Jwt jwt, String role, Map<String, Object> request) {
        return service.assignGrant(projectId, actor(jwt), role, number(request.get("userId")),
                string(request.get("moduleType")), integer(request.get("roundNo")),
                string(request.get("taskNodeId")), string(request.get("reason")));
    }

    private Map<String, Object> grants(Long projectId, Jwt jwt, String role, Map<String, Object> request) {
        return service.assignGrants(projectId, actor(jwt), role, numbers(request.get("userIds")),
                string(request.get("moduleType")), integer(request.get("roundNo")),
                string(request.get("taskNodeId")), string(request.get("reason")));
    }

    private static ActorContext actor(Jwt jwt) { return ActorContextFactory.from(jwt); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static Long number(Object value) {
        if (value == null) return null; return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value));
    }
    private static Integer integer(Object value) {
        if (value == null) return null; return value instanceof Number n ? n.intValue() : Integer.valueOf(String.valueOf(value));
    }
    private static List<Long> numbers(Object value) {
        if (!(value instanceof List<?> list)) return List.of(); return list.stream().map(ProjectAuthorizationController::number).toList();
    }
}
