package com.amatrix.sic.project.entity;

public record UserSnapshotRow(Long userId,String username,String realName,Long deptId,String deptName,
        String rolesJson,boolean enabled,long version) {}
