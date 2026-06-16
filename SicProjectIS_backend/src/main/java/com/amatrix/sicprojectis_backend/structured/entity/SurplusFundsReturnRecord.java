package com.amatrix.sicprojectis_backend.structured.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SurplusFundsReturnRecord {
    private Long returnId;
    private Long acceptanceId;
    private Long projectId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private BigDecimal surplusAmount;
    private Boolean returnRequired;
    private String returnAccountName;
    private String returnAccountNo;
    private String returnBankName;
    private String returnStatus;
    private BigDecimal returnedAmount;
    private LocalDateTime returnedAt;
    private Long financeOperatorId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
