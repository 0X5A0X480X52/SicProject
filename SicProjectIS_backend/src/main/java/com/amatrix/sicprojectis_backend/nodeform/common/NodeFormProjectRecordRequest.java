package com.amatrix.sicprojectis_backend.nodeform.common;

import com.amatrix.sicprojectis_backend.structured.dto.AchievementRequest;
import com.amatrix.sicprojectis_backend.structured.dto.FinancialSettlementRequest;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationPublicity;
import com.amatrix.sicprojectis_backend.structured.entity.SurplusFundsReturnRecord;

public record NodeFormProjectRecordRequest(
        ProjectApplicationPublicity publicity,
        FinancialSettlementRequest financialSettlement,
        AchievementRequest achievement,
        SurplusFundsReturnRecord surplusReturn) {
}
