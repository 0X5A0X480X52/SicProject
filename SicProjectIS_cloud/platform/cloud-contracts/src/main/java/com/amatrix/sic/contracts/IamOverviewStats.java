package com.amatrix.sic.contracts;

import java.util.List;

public record IamOverviewStats(
        long totalUsers,
        long enabledUsers,
        long totalRoles,
        long totalPermissions,
        List<CountItem> roleCounts) {
    public record CountItem(String key, String label, long count) {
    }
}
