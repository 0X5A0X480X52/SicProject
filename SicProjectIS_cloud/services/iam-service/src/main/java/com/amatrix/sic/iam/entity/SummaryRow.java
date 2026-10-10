package com.amatrix.sic.iam.entity;

public record SummaryRow(Long userId,String username,String realName,Long deptId,String deptName,
        boolean enabled,long authVersion) {}
