package com.amatrix.sic.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationSagaCommand(
        UUID sagaId,
        Long projectId,
        Long actorUserId,
        String projectCode,
        String projectName,
        String projectType,
        String projectLevel,
        BigDecimal approvedAmount,
        LocalDate startDate,
        LocalDate endDate,
        String applicationTitle,
        Boolean limitedProject,
        String applicationSummary) {
}
