package com.amatrix.sicprojectis_backend.structured.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sicprojectis_backend.structured.entity.AcceptanceFinancialSettlement;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAcceptanceExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAchievement;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationDetail;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationPublicity;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectContractExt;
import com.amatrix.sicprojectis_backend.structured.entity.SurplusFundsReturnRecord;

@Mapper
public interface ProjectStructuredDataDao {
    ProjectApplicationExt selectApplicationExtByApplicationId(@Param("applicationId") Long applicationId);
    int insertApplicationExt(ProjectApplicationExt record);
    int updateApplicationExt(ProjectApplicationExt record);
    ProjectApplicationDetail selectApplicationDetailByApplicationId(@Param("applicationId") Long applicationId);
    int insertApplicationDetail(ProjectApplicationDetail record);
    int updateApplicationDetail(ProjectApplicationDetail record);
    ProjectContractExt selectContractExtByContractId(@Param("contractId") Long contractId);
    int insertContractExt(ProjectContractExt record);
    int updateContractExt(ProjectContractExt record);
    ProjectAcceptanceExt selectAcceptanceExtByAcceptanceId(@Param("acceptanceId") Long acceptanceId);
    int insertAcceptanceExt(ProjectAcceptanceExt record);
    int updateAcceptanceExt(ProjectAcceptanceExt record);
    int insertPublicity(ProjectApplicationPublicity record);
    int updatePublicity(ProjectApplicationPublicity record);
    int deletePublicity(@Param("publicityId") Long publicityId);
    List<ProjectApplicationPublicity> selectPublicitiesByProjectId(@Param("projectId") Long projectId);
    int insertFinancialSettlement(AcceptanceFinancialSettlement record);
    int updateFinancialSettlement(AcceptanceFinancialSettlement record);
    int deleteFinancialSettlement(@Param("settlementId") Long settlementId);
    List<AcceptanceFinancialSettlement> selectFinancialSettlementsByProjectId(@Param("projectId") Long projectId);
    int insertAchievement(ProjectAchievement record);
    int updateAchievement(ProjectAchievement record);
    int deleteAchievement(@Param("achievementId") Long achievementId);
    List<ProjectAchievement> selectAchievementsByProjectId(@Param("projectId") Long projectId);
    int insertSurplusReturn(SurplusFundsReturnRecord record);
    int updateSurplusReturn(SurplusFundsReturnRecord record);
    int deleteSurplusReturn(@Param("returnId") Long returnId);
    List<SurplusFundsReturnRecord> selectSurplusReturnsByProjectId(@Param("projectId") Long projectId);
}
