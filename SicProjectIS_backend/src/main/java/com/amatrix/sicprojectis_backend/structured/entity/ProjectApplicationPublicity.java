package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectApplicationPublicity {
    private Long publicityId;
    private Long applicationId;
    private Long projectId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private String publicityTitle;
    private String publicityScope;
    private LocalDate publicityStartDate;
    private LocalDate publicityEndDate;
    private Integer recommendedRank;
    private String recommendedReason;
    private Boolean hasObjection;
    private String objectionContent;
    private String objectionHandlingResult;
    private String objectionHandlingComment;
    private String publicityResult;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
