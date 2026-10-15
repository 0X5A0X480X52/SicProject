package com.amatrix.sic.contracts;

import java.util.List;

/**
 * 跨服务用户摘要：姓名、部门、角色、启用状态及 authVersion。
 * 不包含密码或密码哈希；落到各服务的快照可能晚于 IAM 当前数据。
 * @param authVersion 身份授权版本，消费端用它防止旧事件覆盖较新快照
 */
public record UserSummary(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        List<String> roleCodes,
        boolean enabled,
        long authVersion) {
}
