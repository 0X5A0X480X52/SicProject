package com.amatrix.sicprojectis_backend.material.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MaterialContextView {
    private Long materialId;
    private Long projectId;
    private Long materialTypeId;
    private String materialTypeCode;
    private String materialTypeName;
    private String moduleType;
    private String allowedFileTypes;
    private Integer maxFileSizeMb;
    private Long materialVersionId;
    private Integer versionNo;
    private String fileName;
    private String fileUrl;
    private String fileHash;
    private Long uploadedBy;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private Boolean isCurrent;
}
