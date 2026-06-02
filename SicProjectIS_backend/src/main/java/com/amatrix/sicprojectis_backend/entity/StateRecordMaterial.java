package com.amatrix.sicprojectis_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class StateRecordMaterial {
    private Long recordMaterialId;
    private Long stateRecordId;
    private Long remarkId;
    private Long materialVersionId;
    private String materialUsage;
    private Boolean isRequired;
    private LocalDateTime linkedAt;
}
