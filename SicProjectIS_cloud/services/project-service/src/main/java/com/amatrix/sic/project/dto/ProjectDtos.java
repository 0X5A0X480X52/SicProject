package com.amatrix.sic.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.amatrix.sic.contracts.StateTransitionContract;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record StartProjectApplicationRequest(
            String projectCode, String projectName, String projectType, String projectLevel,
            BigDecimal approvedAmount, LocalDate startDate, LocalDate endDate, String applicationTitle,
            Boolean isLimitedProject, String applicationSummary) {
    }

    public record StartProjectApplicationResponse(
            Long projectId, Long moduleInstanceId, StateTransitionContract transition) {
    }
}
