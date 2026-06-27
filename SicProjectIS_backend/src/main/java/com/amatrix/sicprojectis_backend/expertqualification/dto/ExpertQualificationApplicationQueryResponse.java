package com.amatrix.sicprojectis_backend.expertqualification.dto;

import java.util.List;

public record ExpertQualificationApplicationQueryResponse(
        List<ExpertQualificationApplicationResponse> applications
) {
}
