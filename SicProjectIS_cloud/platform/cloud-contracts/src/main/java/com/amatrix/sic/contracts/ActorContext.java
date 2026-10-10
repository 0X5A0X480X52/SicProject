package com.amatrix.sic.contracts;

import java.util.List;

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
