package com.amatrix.sicprojectis_backend.expertqualification.dto;

import java.util.List;

public record MyExpertQualificationResponse(
        boolean expert,
        boolean hasActiveApplication,
        List<ExpertQualificationApplicationResponse> applications
) {
}
