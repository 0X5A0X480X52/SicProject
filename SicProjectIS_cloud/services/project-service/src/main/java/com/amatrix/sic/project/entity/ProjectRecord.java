package com.amatrix.sic.project.entity;

public record ProjectRecord(Long id,String code,String name,Long deptId,String deptName,
        Long ownerUserId,String ownerName,String type,String level,String stage) {}
