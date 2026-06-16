package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SubmissionRecord {
    private Long submissionId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private String moduleType;
    private String submissionType;
    private String targetActorCode;
    private String targetActorName;
    private String submissionMethod;
    private String submissionNo;
    private String externalSystemNo;
    private String receiptNo;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
