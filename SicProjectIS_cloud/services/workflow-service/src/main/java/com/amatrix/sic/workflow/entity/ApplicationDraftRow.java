package com.amatrix.sic.workflow.entity;
public record ApplicationDraftRow(Long id,Long moduleId,Long projectId,String title,boolean limited,String summary,String json,java.time.LocalDateTime createdAt) {}
