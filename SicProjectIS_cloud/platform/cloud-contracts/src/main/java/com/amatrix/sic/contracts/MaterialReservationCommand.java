package com.amatrix.sic.contracts;

import java.util.List;

public record MaterialReservationCommand(
        String commandId,
        Long actorUserId,
        Long projectId,
        Long moduleInstanceId,
        Integer expectedSeq,
        List<Long> materialVersionIds,
        List<String> requiredMaterialTypeCodes) {
}
