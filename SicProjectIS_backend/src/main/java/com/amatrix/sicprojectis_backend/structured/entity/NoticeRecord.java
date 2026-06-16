package com.amatrix.sicprojectis_backend.structured.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NoticeRecord {
    private Long noticeId;
    private Long moduleInstanceId;
    private Long stateRecordId;
    private String moduleType;
    private String noticeType;
    private String noticeTitle;
    private String noticeNo;
    private String publishUnit;
    private Long publishUserId;
    private LocalDateTime publishTime;
    private String noticeScope;
    private String targetDeptScope;
    private String targetUserScope;
    private String projectCategory;
    private Boolean isLimitedProject;
    private Integer limitCount;
    private LocalDateTime startTime;
    private LocalDateTime deadlineTime;
    private String materialRequirementSummary;
    private String contentSummary;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
