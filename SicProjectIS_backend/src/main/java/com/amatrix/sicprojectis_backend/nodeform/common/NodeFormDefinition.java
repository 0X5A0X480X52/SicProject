package com.amatrix.sicprojectis_backend.nodeform.common;

import java.util.List;

public record NodeFormDefinition(
        String formCode,
        NodeFormModuleType moduleType,
        String nodeId,
        String stateCode,
        String title,
        NodeFormDataKind dataKind,
        NodeFormWriteMode writeMode,
        boolean supportsFiles,
        List<String> materialTypeCodes) {
}
