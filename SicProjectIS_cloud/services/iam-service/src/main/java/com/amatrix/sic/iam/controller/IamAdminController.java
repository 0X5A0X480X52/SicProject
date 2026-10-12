package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.service.IamAdminService;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;

/**
 * 管理员用户、角色权限、部门及部门成员 API，解析请求字段并调用 IamAdminService。
 */
@RestController
@RequestMapping("/api/admin")
public class IamAdminController {
    private final IamAdminService service;

    public IamAdminController(IamAdminService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/admin/users/roles}：为用户角色管理页加载完整用户列表及可选角色。
     * <p>要求 PERM_USER_MANAGE；等价于不传筛选条件的 users 查询，返回 roles、users。
     * <p>这是兼容页面的查询入口，不授予角色，也不提供分页。
     */
    @GetMapping("/users/roles")
    ApiResponse<Map<String, Object>> roleManagement() {
        return ApiResponse.ok(service.queryUsers(null, null, null, null, null));
    }

    /**
     * {@code GET /api/admin/users}：按条件检索用户，并提供角色选择列表。
     * <p>要求 PERM_USER_MANAGE；可选 username、realName 为模糊条件，deptId、enabled、roleCode 用于范围筛选。
     * <p>返回 roles、users；列表包含账号、姓名、部门、启用状态和角色，不返回密码。
     */
    @GetMapping("/users")
    ApiResponse<Map<String, Object>> users(@RequestParam(required = false) String username,
            @RequestParam(required = false) String realName, @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Boolean enabled, @RequestParam(required = false) String roleCode) {
        return ApiResponse.ok(service.queryUsers(username, realName, deptId, enabled, roleCode));
    }

    /**
     * {@code GET /api/admin/users/{userId}}：读取指定用户的管理详情。
     * <p>要求 PERM_USER_MANAGE；userId 是 IAM 用户主键，不是项目成员关系 ID。
     * <p>返回用户基本资料、联系方式、部门、状态和角色；用户不存在返回 404。
     */
    @GetMapping("/users/{userId}")
    ApiResponse<Map<String, Object>> user(@PathVariable Long userId) {
        return ApiResponse.ok(service.userDetail(userId));
    }

    /**
     * {@code PUT /api/admin/users/{userId}/roles}：替换指定用户的全局角色集合。
     * <p>要求 PERM_ROLE_MANAGE；JSON roleCodes 表示替换后的完整集合，而非仅新增的角色。
     * <p>校验角色代码，并禁止操作人移除自己的 SYSTEM_ADMIN；变更发布新权限版本及身份事件，旧 token 需要更新。
     * <p>返回 user、diff、query，分别为用户详情、角色增减与刷新后的角色管理数据。
     */
    @PutMapping("/users/{userId}/roles")
    ApiResponse<Map<String, Object>> roles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.updateRoles(actor(jwt), userId, request.get("roleCodes")));
    }

    /**
     * {@code PATCH /api/admin/users/{userId}/status}：启用或停用指定用户账号。
     * <p>要求 PERM_USER_MANAGE；JSON enabled 仅为 true 时表示启用，缺省或非 true 按停用处理。
     * <p>禁止停用操作人自己的账号；同步会话状态并发送身份事件，返回更新后的用户详情。
     */
    @PatchMapping("/users/{userId}/status")
    ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody Map<String, Boolean> request) {
        return ApiResponse.ok(service.updateStatus(actor(jwt), userId, Boolean.TRUE.equals(request.get("enabled"))));
    }

    /**
     * {@code GET /api/admin/roles/permissions}：读取角色与权限的对应关系，供权限配置页展示。
     * <p>要求 PERM_PERMISSION_MANAGE；返回 roles、permissions、matrix，矩阵中的 permissionCodes 已转换为列表。
     * <p>本接口只读取配置，不修改角色或用户的权限版本。
     */
    @GetMapping("/roles/permissions")
    ApiResponse<Map<String, Object>> matrix() {
        return ApiResponse.ok(service.permissionMatrix());
    }

    /**
     * {@code GET /api/admin/permissions}：列出系统已登记的权限代码和说明。
     * <p>要求 PERM_PERMISSION_MANAGE；返回权限定义列表，供配置角色权限时选择。
     * <p>此结果是权限目录，不是当前登录账号已获得的权限列表。
     */
    @GetMapping("/permissions")
    ApiResponse<List<Map<String, Object>>> permissions() {
        return ApiResponse.ok(service.permissions());
    }

    /**
     * {@code PUT /api/admin/roles/{roleCode}/permissions}：替换一个角色的完整权限集合。
     * <p>要求 PERM_PERMISSION_MANAGE；roleCode 为角色代码，JSON permissionCodes 为替换后的全部权限代码。
     * <p>验证角色和权限存在，更新该角色关联用户的权限版本并记录事件。
     * <p>返回 roleCode、diff、matrix；受影响用户需取得新 token 才能使用更新后的权限。
     */
    @PutMapping("/roles/{roleCode}/permissions")
    ApiResponse<Map<String, Object>> permissions(@AuthenticationPrincipal Jwt jwt, @PathVariable String roleCode,
            @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.updateRolePermissions(actor(jwt), roleCode, request.get("permissionCodes")));
    }

    /**
     * {@code GET /api/admin/departments}：查询当前人员可管理的部门目录。
     * <p>要求 PERM_DEPARTMENT_MEMBER_MANAGE；全局管理人员查看全部部门，部门管理人员限制在本人部门。
     * <p>返回部门列表；缺少部门范围的非全局人员会被拒绝，不扩展为全局可见。
     */
    @GetMapping("/departments")
    ApiResponse<List<Map<String, Object>>> departments(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.departments(actor(jwt)));
    }

    /**
     * {@code POST /api/admin/departments}：创建部门，建立部门管理目录项。
     * <p>要求 PERM_DEPARTMENT_MEMBER_MANAGE 且具有全局部门管理身份；JSON 必填 deptName，可提供 deptCode、parentDeptId、enabled。
     * <p>缺省 enabled 为启用，deptCode 按服务规则补全或校验；返回新部门记录并写入部门变更事件。
     */
    @PostMapping("/departments")
    ApiResponse<Map<String, Object>> createDepartment(@AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.createDepartment(actor(jwt), request));
    }

    /**
     * {@code PUT /api/admin/departments/{deptId}}：修改指定部门的名称、代码、上级及启用状态。
     * <p>要求部门管理权限及全局部门管理身份；deptId 指定已有部门，JSON 字段与创建接口一致，deptName 必填。
     * <p>禁止将部门自身作为上级；返回更新记录并发布部门变更事件。
     */
    @PutMapping("/departments/{deptId}")
    ApiResponse<Map<String, Object>> updateDepartment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deptId,
            @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(service.updateDepartment(actor(jwt), deptId, request));
    }

    /**
     * {@code DELETE /api/admin/departments/{deptId}}：删除不再被引用的部门目录项。
     * <p>要求部门管理权限及全局部门管理身份；deptId 为目标部门 ID。
     * <p>存在服务所检查的部门引用时返回 409；成功返回 data=null 并发布删除事件，不自动迁移成员。
     */
    @DeleteMapping("/departments/{deptId}")
    ApiResponse<Void> deleteDepartment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deptId) {
        service.deleteDepartment(actor(jwt), deptId); return ApiResponse.ok(null);
    }

    /**
     * {@code GET /api/admin/departments/members}：加载部门成员管理页的数据及管理能力提示。
     * <p>要求 PERM_DEPARTMENT_MEMBER_MANAGE；可选 deptId，非全局管理人员只能选择本人部门。
     * <p>返回 departments、members、assignableRoleCodes 及管理能力字段；未分配部门的非全局人员收到空列表和禁用原因。
     * <p>前端应读取 memberManagementDisabled，不能将空部门范围解释为可操作全校人员。
     */
    @GetMapping("/departments/members")
    ApiResponse<Map<String, Object>> members(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(service.departmentMembers(actor(jwt), deptId));
    }

    /**
     * {@code GET /api/admin/departments/candidates}：检索可加入当前管理部门的用户候选。
     * <p>要求 PERM_DEPARTMENT_MEMBER_MANAGE；keyword 为可选模糊搜索条件，服务按操作人的部门管理范围筛选。
     * <p>返回 users；候选查询不改变部门或角色，实际加入仍通过 member 接口复核范围。
     */
    @GetMapping("/departments/candidates")
    ApiResponse<Map<String, Object>> candidates(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.candidates(actor(jwt), keyword));
    }

    /**
     * {@code PUT /api/admin/departments/{deptId}/members/{userId}}：将用户加入指定部门，并按需替换部门可分配角色。
     * <p>deptId、userId 分别指定部门和用户；JSON roleCodes 缺省时保留角色，提供时替换可编辑角色子集。
     * <p>要求 PERM_DEPARTMENT_MEMBER_MANAGE 并具有目标部门管理范围；部门管理员不能从其他部门调入成员。
     * <p>返回成员资料，发布身份变更；不允许通过部门管理授予可编辑清单外的全局角色。
     */
    @PutMapping("/departments/{deptId}/members/{userId}")
    ApiResponse<Map<String, Object>> member(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deptId,
            @PathVariable Long userId, @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.upsertMember(actor(jwt), deptId, userId, request.get("roleCodes")));
    }

    /**
     * {@code PATCH /api/admin/departments/members/{userId}/roles}：调整已有部门成员的可编辑角色子集。
     * <p>要求 PERM_DEPARTMENT_MEMBER_MANAGE 及成员所在部门的管理范围；JSON roleCodes 必须提供。
     * <p>仅替换部门允许编辑的角色，其他角色保留；返回成员资料并更新权限版本、发送角色变更事件。
     */
    @PatchMapping("/departments/members/{userId}/roles")
    ApiResponse<Map<String, Object>> memberRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
            @RequestBody Map<String, List<String>> request) {
        return ApiResponse.ok(service.updateMemberRoles(actor(jwt), userId, request.get("roleCodes")));
    }

    /**
     * {@code DELETE /api/admin/departments/members/{userId}}：解除用户与当前部门的归属关系。
     * <p>要求 PERM_DEPARTMENT_MEMBER_MANAGE，服务复核用户当前部门的管理范围。
     * <p>将用户部门置空，返回成员资料并发送身份变更事件；不删除账号，也不在此移除角色或迁移既有项目。
     */
    @DeleteMapping("/departments/members/{userId}")
    ApiResponse<Map<String, Object>> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return ApiResponse.ok(service.removeMember(actor(jwt), userId));
    }

    private static Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
