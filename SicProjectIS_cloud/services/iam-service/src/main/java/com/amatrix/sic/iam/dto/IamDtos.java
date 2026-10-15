package com.amatrix.sic.iam.dto;

import java.time.Instant;
import java.util.List;

/** IAM 登录、注册请求及用户会话响应；请求中的身份字段不能替代服务端当前账号、部门和权限记录。 */
public final class IamDtos {
    private IamDtos() {
    }

    /**
     * 登录凭据。
     * @param username IAM 登录名，服务会去除首尾空白后查询
     * @param password 原始密码，只用于本次 BCrypt 校验，不得写入日志或持久化明文
     */
    public record LoginRequest(String username, String password) {
    }

    /**
     * 公开注册请求；创建账号后不会自动授予管理员、财务或专家等业务角色。
     * @param username 必填登录名，去除首尾空白后保持唯一
     * @param password 必填原始密码，当前最短长度为 6
     * @param realName 展示姓名；空值或空白时使用 username
     * @param deptId 可选的 IAM 部门 ID；项目申请仍会重新读取并校验当前启用部门
     * @param phone 可选联系电话，空白规范为 null
     * @param email 可选邮箱，空白规范为 null
     */
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
