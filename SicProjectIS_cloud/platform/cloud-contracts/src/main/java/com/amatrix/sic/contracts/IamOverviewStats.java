package com.amatrix.sic.contracts;

import java.util.List;

/**
 * IAM 提供给 Project 管理概览的汇总结果；不含用户明细。
 * @param totalUsers 当前账号总数
 * @param enabledUsers 当前启用账号数
 * @param totalRoles 已登记角色数
 * @param totalPermissions 已登记权限数
 * @param roleCounts 按角色代码汇总的用户数量
 */
public record IamOverviewStats(
        long totalUsers,
        long enabledUsers,
        long totalRoles,
        long totalPermissions,
        List<CountItem> roleCounts) {
    /** 单项统计；key 是稳定代码，label 是展示名，count 是数量。 */
    public record CountItem(String key, String label, long count) {
    }
}
