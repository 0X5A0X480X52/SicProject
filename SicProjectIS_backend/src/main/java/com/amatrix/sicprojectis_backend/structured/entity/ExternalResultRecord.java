package com.amatrix.sicprojectis_backend.structured.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ExternalResultRecord {
    private Long externalResultId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private String moduleType;
    private String resultType;
    private String externalActorCode;
    private String externalActorName;
    private String externalResult;
    private LocalDate externalResultDate;
    private String externalFileNo;
    private String externalSystemNo;
    private BigDecimal approvedAmount;
    private LocalDate effectiveDate;
    private String summary;
    private Long registeredBy;
    private LocalDateTime registeredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
