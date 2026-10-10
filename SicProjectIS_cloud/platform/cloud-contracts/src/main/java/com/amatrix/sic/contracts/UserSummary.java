package com.amatrix.sic.contracts;

import java.util.List;

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
