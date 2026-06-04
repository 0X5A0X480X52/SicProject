package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record AuditLogQueryResponse(List<AuditLogRecordResponse> logs) {
}
