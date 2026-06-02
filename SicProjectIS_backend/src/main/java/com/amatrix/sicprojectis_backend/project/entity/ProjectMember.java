package com.amatrix.sicprojectis_backend.project.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectMember {
    private Long projectMemberId;
    private Long projectId;
    private Long userId;
    private String memberRole;
    private String responsibility;
    private LocalDateTime joinedAt;
}
