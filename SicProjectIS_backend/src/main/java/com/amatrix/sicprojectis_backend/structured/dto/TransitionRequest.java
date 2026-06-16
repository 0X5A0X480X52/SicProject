package com.amatrix.sicprojectis_backend.structured.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransitionRequest(
        String eventType,
        Integer expectedSeq,
        String result,
        String remark,
        List<Long> materialVersionIds,
        List<CheckItemData> checkItems,
        ExternalResultData externalResult,
        SealData seal,
        SubmissionData submission,
        ArchiveData archive) {
    public record CheckItemData(String itemCode, String itemName, String itemType, String itemValue,
            String itemResult, Boolean required, Boolean passed, String remark, Integer sortNo) { }
    public record ExternalResultData(String resultType, String externalActorCode, String externalActorName,
            String externalResult, LocalDate externalResultDate, String externalFileNo, String externalSystemNo,
            BigDecimal approvedAmount, LocalDate effectiveDate, String summary) { }
    public record SealData(String sealSubject, String sealType, String sealReason, Integer copyCount,
            Boolean leaderSigned, LocalDateTime leaderSignedAt, Boolean schoolSealed, LocalDateTime schoolSealedAt,
            Boolean externalSealed, String externalActorName, LocalDateTime externalSealedAt, String sealStatus, String remark) { }
    public record SubmissionData(String submissionType, String targetActorCode, String targetActorName,
            String submissionMethod, String submissionNo, String externalSystemNo, String receiptNo,
            LocalDateTime submittedAt, String remark) { }
    public record ArchiveData(String archiveType, String archiveNo, String archiveLocation, Integer paperCopyCount,
            Integer electronicCopyCount, LocalDateTime archivedAt, String archiveStatus, String remark) { }
}
