package com.amatrix.sic.contracts;

import java.util.Map;

/**
 * Idempotent command emitted by workflow-service after a state transition and
 * consumed by material-service to render and persist a generated document.
 */
public record DocumentGenerationCommand(
        String commandId,
        Long projectId,
        Long moduleInstanceId,
        Long stateRecordId,
        Integer stateSeq,
        String moduleType,
        String nodeId,
        String stateCode,
        String documentTypeCode,
        String documentTypeName,
        String templateCode,
        String snapshotSchemaJson,
        String snapshotViewName,
        String outputMaterialTypeCode,
        String outputMaterialTypeName,
        Boolean required,
        Long actorUserId,
        Map<String, Object> snapshot) {
}
