package com.amatrix.sicprojectis_backend.nodeform.common;

import java.util.List;

import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.NoticeUpsertRequest;

public record NodeFormSaveRequest(
        Long projectId,
        Long moduleInstanceId,
        Long stateRecordId,
        ApplicationDraftRequest applicationDraft,
        ContractDraftRequest contractDraft,
        AcceptanceDraftRequest acceptanceDraft,
        NoticeUpsertRequest notice,
        NodeFormRuntimeRecordRequest runtimeRecord,
        NodeFormProjectRecordRequest projectRecord,
        NodeFormExpertRequest expertReview,
        List<Long> materialVersionIds) {
}
