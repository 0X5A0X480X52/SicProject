package com.amatrix.sic.contracts;

public record ProjectAccessResult(
        String decisionId,
        Long projectId,
        boolean allowed,
        long projectVersion,
        String reasonCode,
        Long ownerUserId,
        String ownerName,
        Long deptId) {
}
