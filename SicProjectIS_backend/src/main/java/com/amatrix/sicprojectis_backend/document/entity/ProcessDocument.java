package com.amatrix.sicprojectis_backend.document.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProcessDocument {
    private Long documentId;
    private Long moduleInstanceId;
    private Long generatedStateRecordId;
    private String documentTypeCode;
    private String documentNo;
    private String documentTitle;
    private String documentStatus;
    private String snapshotJson;
    private LocalDateTime generatedAt;
}
