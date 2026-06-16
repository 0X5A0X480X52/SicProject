package com.amatrix.sicprojectis_backend.structured.dto;

import com.amatrix.sicprojectis_backend.project.entity.ProjectAcceptance;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAcceptanceExt;

public record AcceptanceDraftRequest(ProjectAcceptance acceptance, ProjectAcceptanceExt extension) {
}
