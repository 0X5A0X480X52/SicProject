package com.amatrix.sicprojectis_backend.structured.dto;

import com.amatrix.sicprojectis_backend.project.entity.ProjectContract;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectContractExt;

public record ContractDraftRequest(ProjectContract contract, ProjectContractExt extension) {
}
