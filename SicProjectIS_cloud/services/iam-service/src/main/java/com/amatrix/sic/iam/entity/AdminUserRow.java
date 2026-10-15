package com.amatrix.sic.iam.entity;

public record AdminUserRow(Long userId,String username,String realName,Long deptId,String deptName,
        String phone,String email,boolean enabled,String roleCodes) {}
