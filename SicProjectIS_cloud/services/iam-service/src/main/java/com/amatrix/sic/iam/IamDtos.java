package com.amatrix.sic.iam;

import java.time.Instant;
import java.util.List;

public final class IamDtos {
    private IamDtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterRequest(
            String username, String password, String realName, Long deptId, String phone, String email) {
    }

    public record CurrentUser(
            Long userId, String username, String realName, Long deptId, String deptName,
            List<String> roleCodes, List<String> permissionCodes) {
    }

    public record LoginResponse(String token, Instant expiresAt, CurrentUser user) {
    }

    public record UserAccount(
            Long userId, String username, String passwordHash, String realName, Long deptId,
            String deptName, String phone, String email, boolean enabled, long authVersion,
            List<String> roleCodes, List<String> permissionCodes) {
        public CurrentUser currentUser() {
            return new CurrentUser(userId, username, realName, deptId, deptName, roleCodes, permissionCodes);
        }
    }
}
