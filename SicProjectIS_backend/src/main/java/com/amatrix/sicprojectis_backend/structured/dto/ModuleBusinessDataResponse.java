package com.amatrix.sicprojectis_backend.structured.dto;

import java.util.List;
import com.amatrix.sicprojectis_backend.document.entity.ProcessDocument;
import com.amatrix.sicprojectis_backend.material.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.runtime.entity.StateRecordRemark;
import com.amatrix.sicprojectis_backend.structured.entity.AcceptanceFinancialSettlement;
import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAchievement;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationPublicity;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;
import com.amatrix.sicprojectis_backend.structured.entity.SurplusFundsReturnRecord;

public record ModuleBusinessDataResponse(
        ProjectModuleInstance moduleInstance,
        Project project,
        Object businessDraft,
        List<ModuleStateRecord> stateRecords,
        List<StateRecordRemark> remarks,
        List<StateRecordCheckItem> checkItems,
        List<ExternalResultRecord> externalResults,
        List<SealRecord> sealRecords,
        List<SubmissionRecord> submissionRecords,
        List<ArchiveRecord> archiveRecords,
        List<ProjectApplicationPublicity> publicities,
        List<AcceptanceFinancialSettlement> financialSettlements,
        List<ProjectAchievement> achievements,
        List<SurplusFundsReturnRecord> surplusReturns,
        List<ExpertReviewData> expertReviews,
        List<MaterialContextView> materials,
        List<ProcessDocument> documents) {
}
