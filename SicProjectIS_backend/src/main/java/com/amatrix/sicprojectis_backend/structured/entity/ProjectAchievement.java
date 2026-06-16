package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectAchievement {
    private Long achievementId;
    private Long projectId;
    private Long moduleInstanceId;
    private Long acceptanceId;
    private String achievementType;
    private String achievementTitle;
    private String authorList;
    private String achievementLevel;
    private LocalDate publishOrGrantDate;
    private Long proofMaterialVersionId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
