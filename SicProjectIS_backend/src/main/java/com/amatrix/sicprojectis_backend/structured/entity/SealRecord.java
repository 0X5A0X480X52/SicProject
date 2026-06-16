package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SealRecord {
    private Long sealRecordId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private String moduleType;
    private String sealSubject;
    private String sealType;
    private String sealReason;
    private Integer copyCount;
    private Long applicantUserId;
    private Long handledBy;
    private Boolean leaderSigned;
    private LocalDateTime leaderSignedAt;
    private Boolean schoolSealed;
    private LocalDateTime schoolSealedAt;
    private Boolean externalSealed;
    private String externalActorName;
    private LocalDateTime externalSealedAt;
    private String sealStatus;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
