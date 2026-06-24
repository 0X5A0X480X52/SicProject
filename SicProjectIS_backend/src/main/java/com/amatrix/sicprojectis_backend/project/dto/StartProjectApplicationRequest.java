package com.amatrix.sicprojectis_backend.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StartProjectApplicationRequest(
        String projectCode,
        String projectName,
        String projectType,
        String projectLevel,
        BigDecimal approvedAmount,
        LocalDate startDate,
        LocalDate endDate,
        String applicationTitle,
        Boolean isLimitedProject,
        String applicationSummary) {
}
