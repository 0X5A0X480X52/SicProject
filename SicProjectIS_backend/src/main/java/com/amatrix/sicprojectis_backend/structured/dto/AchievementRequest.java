package com.amatrix.sicprojectis_backend.structured.dto;

import java.time.LocalDate;

public record AchievementRequest(Long moduleInstanceId, Long acceptanceId, String achievementType,
        String achievementTitle, String authorList, String achievementLevel, LocalDate publishOrGrantDate,
        Long proofMaterialVersionId, String remark) {
}
