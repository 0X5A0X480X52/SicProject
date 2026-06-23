package com.amatrix.sicprojectis_backend.material.dto;

import com.amatrix.sicprojectis_backend.material.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.material.entity.MaterialVersion;

public record MaterialUploadResponse(MaterialVersion version, MaterialContextView context) {
}
