package com.amatrix.sicprojectis_backend.task.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TaskInstance {
    private Long taskInstanceId;
    private Long moduleInstanceId;
    private String nodeId;
    private String stateCode;
    private Long assigneeUserId;
    private String candidateRoleCode;
    private String taskStatus;
    private Integer roundNo;
    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deadlineAt;
}
