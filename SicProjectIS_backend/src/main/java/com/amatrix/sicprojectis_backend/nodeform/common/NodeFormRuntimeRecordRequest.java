package com.amatrix.sicprojectis_backend.nodeform.common;

import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;

public record NodeFormRuntimeRecordRequest(
        StateRecordCheckItem checkItem,
        ExternalResultRecord externalResult,
        SealRecord sealRecord,
        SubmissionRecord submissionRecord,
        ArchiveRecord archiveRecord) {
}
