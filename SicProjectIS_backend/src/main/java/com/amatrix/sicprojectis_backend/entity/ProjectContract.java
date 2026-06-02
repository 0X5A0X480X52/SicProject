package com.amatrix.sicprojectis_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectContract {
    private Long contractId;
    private Long projectId;
    private String contractCode;
    private String contractName;
    private BigDecimal contractAmount;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private String sealStatus;
    private LocalDateTime submittedAt;
    private LocalDateTime signedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
