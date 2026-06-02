package com.amatrix.sicprojectis_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ModuleStateRecord {
    private Long stateRecordId;
    private Long moduleInstanceId;
    private Integer seq;
    private Integer roundNo;
    private String eventType;
    private String fromState;
    private String toState;
    private String fromNodeId;
    private String toNodeId;
    private String result;
    private String summary;
    private String payloadJson;
    private LocalDateTime createdAt;
}
