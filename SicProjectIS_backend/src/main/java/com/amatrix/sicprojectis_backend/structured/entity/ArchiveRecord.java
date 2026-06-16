package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ArchiveRecord {
    private Long archiveId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private String moduleType;
    private String archiveType;
    private String archiveNo;
    private String archiveLocation;
    private Integer paperCopyCount;
    private Integer electronicCopyCount;
    private Long archivedBy;
    private LocalDateTime archivedAt;
    private String archiveStatus;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
