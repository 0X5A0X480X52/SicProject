package com.amatrix.sicprojectis_backend.nodeform.common;

import java.util.List;

import com.amatrix.sicprojectis_backend.expert.dto.ExpertReviewBatchDetailResponse;
import com.amatrix.sicprojectis_backend.material.entity.MaterialContextView;
import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftResponse;
import com.amatrix.sicprojectis_backend.structured.entity.AcceptanceFinancialSettlement;
import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.NoticeRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAchievement;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationPublicity;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;
import com.amatrix.sicprojectis_backend.structured.entity.SurplusFundsReturnRecord;

public record NodeFormDataResponse(
        NodeFormDefinition definition,
        NodeFormContext context,
        ApplicationDraftResponse applicationDraft,
        ContractDraftResponse contractDraft,
        AcceptanceDraftResponse acceptanceDraft,
        List<NoticeRecord> notices,
        List<StateRecordCheckItem> checkItems,
        List<ExternalResultRecord> externalResults,
        List<SealRecord> sealRecords,
        List<SubmissionRecord> submissionRecords,
        List<ArchiveRecord> archiveRecords,
        List<ProjectApplicationPublicity> publicities,
        List<AcceptanceFinancialSettlement> financialSettlements,
        List<ProjectAchievement> achievements,
        List<SurplusFundsReturnRecord> surplusReturns,
        ExpertReviewBatchDetailResponse expertReview,
        List<MaterialContextView> materials) {
}
