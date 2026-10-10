package com.amatrix.sic.iam.entity;

public record IdentityRow(Long userId,String username,String passwordHash,String realName,Long deptId,
        String deptName,String phone,String email,boolean enabled,long authVersion) {}
