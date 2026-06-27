package com.amatrix.sicprojectis_backend.expertqualification.dto;

public record SubmitExpertQualificationRequest(
        String specialty,
        String professionalTitle,
        String applicationReason
) {
}
