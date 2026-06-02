package com.amatrix.sicprojectis_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Material {
    private Long materialId;
    private Long projectId;
    private Long materialTypeId;
    private Long createdBy;
    private LocalDateTime createdAt;
}
