package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectContractExt {
    private Long contractExtId;
    private Long contractId;
    private Long projectId;
    private Long moduleInstanceId;
    private String contractSource;
    private String partyAName;
    private String partyAContact;
    private String partyAPhone;
    private String partyBName;
    private String partyBContact;
    private String partyBPhone;
    private String authorityReviewResult;
    private LocalDate authorityReviewDate;
    private String authorityReviewOpinion;
    private LocalDateTime leaderSignedAt;
    private LocalDateTime schoolSealedAt;
    private LocalDateTime authoritySealedAt;
    private LocalDate effectiveDate;
    private String archiveNo;
    private String archiveLocation;
    private Integer archiveCopies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
