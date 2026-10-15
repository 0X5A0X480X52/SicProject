package com.amatrix.sic.contracts;

import java.util.List;

/**
 * 不可变操作人上下文：用户、部门、角色、权限、Token ID、授权版本；hasRole/hasPermission 判断能力。
 * @param deptId 身份中的部门，可为空；新申请仍需读取 IAM 当前启用部门
 * @param tokenId JWT 的 jti，用于单 token 注销，不等同于权限版本
 * @param authVersion 签发身份携带的权限版本，Gateway 与 Redis 当前值及 pending 屏障比较
 */
public record ActorContext(
        Long userId,
        String username,
        Long deptId,
        String deptName,
        List<String> roleCodes,
        List<String> permissionCodes,
        String tokenId,
        long authVersion) {

    public boolean hasRole(String roleCode) {
        return roleCodes != null && roleCodes.contains(roleCode);
    }

    public boolean hasPermission(String permissionCode) {
        return permissionCodes != null && permissionCodes.contains(permissionCode);
    }
}
