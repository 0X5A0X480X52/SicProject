package com.amatrix.sicprojectis_backend.expertqualification.dto;

public record ReviewExpertQualificationRequest(
        boolean approved,
        String opinion,
        String remark
) {
}

