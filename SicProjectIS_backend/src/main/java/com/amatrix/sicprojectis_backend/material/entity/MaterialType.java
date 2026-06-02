package com.amatrix.sicprojectis_backend.material.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MaterialType {
    private Long materialTypeId;
    private String materialTypeCode;
    private String materialTypeName;
    private String moduleType;
    private String allowedFileTypes;
    private Integer maxFileSizeMb;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
