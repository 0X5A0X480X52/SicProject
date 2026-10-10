package com.amatrix.sic.contracts;

public record ExpertProjectGrantCommand(
        Long projectId,
        Long expertUserId,
        String expertName,
        String moduleType,
        Integer roundNo,
        String taskNodeId,
        Long grantedByUserId,
        String grantedByName) {
}
