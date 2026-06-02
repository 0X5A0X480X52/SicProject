package com.amatrix.sicprojectis_backend.entity;

import lombok.Data;

@Data
public class ProcessDocumentFile {
    private Long documentFileId;
    private Long documentId;
    private Long materialVersionId;
    private String filePurpose;
    private Boolean isMainFile;
}
