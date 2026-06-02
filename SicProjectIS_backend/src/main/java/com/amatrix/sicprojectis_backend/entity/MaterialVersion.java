package com.amatrix.sicprojectis_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MaterialVersion {
    private Long materialVersionId;
    private Long materialId;
    private Integer versionNo;
    private String fileName;
    private String fileUrl;
    private String fileHash;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private Boolean isCurrent;
}
