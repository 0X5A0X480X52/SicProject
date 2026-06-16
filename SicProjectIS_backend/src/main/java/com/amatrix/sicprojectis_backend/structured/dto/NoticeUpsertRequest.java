package com.amatrix.sicprojectis_backend.structured.dto;

import java.time.LocalDateTime;

public record NoticeUpsertRequest(
        String moduleType, String noticeType, String noticeTitle, String noticeNo, String publishUnit,
        LocalDateTime publishTime, String noticeScope, String targetDeptScope, String targetUserScope,
        String projectCategory, Boolean isLimitedProject, Integer limitCount, LocalDateTime startTime,
        LocalDateTime deadlineTime, String materialRequirementSummary, String contentSummary, String remark) {
}
