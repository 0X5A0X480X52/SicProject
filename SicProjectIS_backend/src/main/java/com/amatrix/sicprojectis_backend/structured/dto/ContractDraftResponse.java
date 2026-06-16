package com.amatrix.sicprojectis_backend.structured.dto;

import com.amatrix.sicprojectis_backend.project.entity.ProjectContract;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectContractExt;

public record ContractDraftResponse(ProjectContract contract, ProjectContractExt extension) {
}
