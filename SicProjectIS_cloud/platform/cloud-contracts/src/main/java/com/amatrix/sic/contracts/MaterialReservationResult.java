package com.amatrix.sic.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MaterialReservationResult(
        String reservationId,
        String status,
        Instant expiresAt,
        List<Long> materialVersionIds,
        Map<Long, String> materialTypeCodes) {
}
