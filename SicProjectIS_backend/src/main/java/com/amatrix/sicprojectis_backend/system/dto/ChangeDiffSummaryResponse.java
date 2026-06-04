package com.amatrix.sicprojectis_backend.system.dto;

import java.util.List;

public record ChangeDiffSummaryResponse(
        List<String> added,
        List<String> removed) {
}
