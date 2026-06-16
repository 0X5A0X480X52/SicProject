package com.amatrix.sicprojectis_backend.structured.dto;

import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationDetail;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;

public record ApplicationDraftRequest(ProjectApplication application, ProjectApplicationExt extension, ProjectApplicationDetail detail) {
}
