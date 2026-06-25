package com.amatrix.sicprojectis_backend.structured;

import java.time.LocalDateTime;
import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.project.dao.ProjectAcceptanceDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectContractDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.entity.ProjectAcceptance;
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.project.entity.ProjectContract;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.FinancialSettlementRequest;
import com.amatrix.sicprojectis_backend.structured.dto.AchievementRequest;
import com.amatrix.sicprojectis_backend.structured.entity.AcceptanceFinancialSettlement;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAchievement;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAcceptanceExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationDetail;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectApplicationExt;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectContractExt;

@Service
public class StructuredBusinessService {
    private final ProjectDao projectDao;
    private final ProjectApplicationDao applicationDao;
    private final ProjectContractDao contractDao;
    private final ProjectAcceptanceDao acceptanceDao;
    private final ProjectStructuredDataDao structuredDao;
    private final PermissionService permissionService;

    public StructuredBusinessService(ProjectDao projectDao, ProjectApplicationDao applicationDao,
            ProjectContractDao contractDao, ProjectAcceptanceDao acceptanceDao,
            ProjectStructuredDataDao structuredDao, PermissionService permissionService) {
        this.projectDao = projectDao;
        this.applicationDao = applicationDao;
        this.contractDao = contractDao;
        this.acceptanceDao = acceptanceDao;
        this.structuredDao = structuredDao;
        this.permissionService = permissionService;
    }

    public ApplicationDraftResponse getApplication(AuthenticatedUser user, Long projectId) {
        requireAccess(user, projectId);
        ProjectApplication application = applicationDao.selectByProjectId(projectId);
        if (application == null) return new ApplicationDraftResponse(null, null, null);
        return new ApplicationDraftResponse(application,
                structuredDao.selectApplicationExtByApplicationId(application.getApplicationId()),
                structuredDao.selectApplicationDetailByApplicationId(application.getApplicationId()));
    }

    @Transactional
    public ApplicationDraftResponse saveApplication(AuthenticatedUser user, Long projectId, ApplicationDraftRequest request) {
        requireAccess(user, projectId);
        if (request == null || request.application() == null) throw badRequest("Application data is required");
        LocalDateTime now = LocalDateTime.now();
        ProjectApplication application = request.application();
        ProjectApplication existing = applicationDao.selectByProjectId(projectId);
        application.setProjectId(projectId);
        if (existing == null) {
            application.setApplicationId(null);
            application.setIsLimitedProject(Boolean.TRUE.equals(application.getIsLimitedProject()));
            application.setCreatedAt(now);
            application.setUpdatedAt(now);
            applicationDao.insert(application);
        } else {
            copyNonNullProperties(application, existing, "applicationId", "projectId", "createdAt", "updatedAt");
            existing.setProjectId(projectId);
            existing.setUpdatedAt(now);
            applicationDao.updateById(existing);
            application = existing;
        }
        upsertApplicationExt(projectId, application.getApplicationId(), request.extension(), now);
        upsertApplicationDetail(projectId, application.getApplicationId(), request.detail(), now);
        return getApplication(user, projectId);
    }

    public ContractDraftResponse getContract(AuthenticatedUser user, Long projectId) {
        requireAccess(user, projectId);
        ProjectContract contract = contractDao.selectByProjectId(projectId);
        return contract == null ? new ContractDraftResponse(null, null)
                : new ContractDraftResponse(contract, structuredDao.selectContractExtByContractId(contract.getContractId()));
    }

    @Transactional
    public ContractDraftResponse saveContract(AuthenticatedUser user, Long projectId, ContractDraftRequest request) {
        requireAccess(user, projectId);
        if (request == null || request.contract() == null) throw badRequest("Contract data is required");
        LocalDateTime now = LocalDateTime.now();
        ProjectContract contract = request.contract();
        ProjectContract existing = contractDao.selectByProjectId(projectId);
        contract.setProjectId(projectId);
        if (existing == null) {
            contract.setContractId(null); contract.setCreatedAt(now); contract.setUpdatedAt(now); contractDao.insert(contract);
        } else {
            copyNonNullProperties(contract, existing, "contractId", "projectId", "createdAt", "updatedAt");
            existing.setProjectId(projectId); existing.setUpdatedAt(now); contractDao.updateById(existing); contract = existing;
        }
        ProjectContractExt ext = request.extension();
        if (ext != null) {
            ProjectContractExt existingExt = structuredDao.selectContractExtByContractId(contract.getContractId());
            if (existingExt == null) {
                ext.setContractId(contract.getContractId()); ext.setProjectId(projectId); ext.setContractExtId(null); ext.setCreatedAt(now); ext.setUpdatedAt(now); structuredDao.insertContractExt(ext);
            } else {
                copyNonNullProperties(ext, existingExt, "contractExtId", "contractId", "projectId", "createdAt", "updatedAt");
                existingExt.setContractId(contract.getContractId()); existingExt.setProjectId(projectId); existingExt.setUpdatedAt(now); structuredDao.updateContractExt(existingExt);
            }
        }
        return getContract(user, projectId);
    }

    public AcceptanceDraftResponse getAcceptance(AuthenticatedUser user, Long projectId) {
        requireAccess(user, projectId);
        ProjectAcceptance acceptance = acceptanceDao.selectByProjectId(projectId);
        return acceptance == null ? new AcceptanceDraftResponse(null, null)
                : new AcceptanceDraftResponse(acceptance, structuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId()));
    }

    @Transactional
    public AcceptanceDraftResponse saveAcceptance(AuthenticatedUser user, Long projectId, AcceptanceDraftRequest request) {
        requireAccess(user, projectId);
        if (request == null || request.acceptance() == null) throw badRequest("Acceptance data is required");
        LocalDateTime now = LocalDateTime.now();
        ProjectAcceptance acceptance = request.acceptance();
        ProjectAcceptance existing = acceptanceDao.selectByProjectId(projectId);
        acceptance.setProjectId(projectId);
        if (existing == null) {
            acceptance.setAcceptanceId(null); acceptance.setCreatedAt(now); acceptance.setUpdatedAt(now); acceptanceDao.insert(acceptance);
        } else {
            copyNonNullProperties(acceptance, existing, "acceptanceId", "projectId", "createdAt", "updatedAt");
            existing.setProjectId(projectId); existing.setUpdatedAt(now); acceptanceDao.updateById(existing); acceptance = existing;
        }
        ProjectAcceptanceExt ext = request.extension();
        if (ext != null) {
            ProjectAcceptanceExt existingExt = structuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId());
            if (existingExt == null) {
                ext.setAcceptanceId(acceptance.getAcceptanceId()); ext.setProjectId(projectId); ext.setAcceptanceExtId(null); ext.setCreatedAt(now); ext.setUpdatedAt(now); structuredDao.insertAcceptanceExt(ext);
            } else {
                copyNonNullProperties(ext, existingExt, "acceptanceExtId", "acceptanceId", "projectId", "createdAt", "updatedAt");
                existingExt.setAcceptanceId(acceptance.getAcceptanceId()); existingExt.setProjectId(projectId); existingExt.setUpdatedAt(now); structuredDao.updateAcceptanceExt(existingExt);
            }
        }
        return getAcceptance(user, projectId);
    }

    public List<ProjectAchievement> getAchievements(AuthenticatedUser user, Long projectId) {
        requireAccess(user, projectId); return structuredDao.selectAchievementsByProjectId(projectId);
    }

    @Transactional
    public ProjectAchievement addAchievement(AuthenticatedUser user, Long projectId, AchievementRequest request) {
        requireAccess(user, projectId);
        if (request == null || request.achievementType() == null || request.achievementTitle() == null) throw badRequest("Achievement type and title are required");
        LocalDateTime now=LocalDateTime.now(); ProjectAchievement row=new ProjectAchievement(); row.setProjectId(projectId); row.setModuleInstanceId(request.moduleInstanceId()); row.setAcceptanceId(request.acceptanceId()); row.setAchievementType(request.achievementType()); row.setAchievementTitle(request.achievementTitle()); row.setAuthorList(request.authorList()); row.setAchievementLevel(request.achievementLevel()); row.setPublishOrGrantDate(request.publishOrGrantDate()); row.setProofMaterialVersionId(request.proofMaterialVersionId()); row.setRemark(request.remark()); row.setCreatedAt(now); row.setUpdatedAt(now); structuredDao.insertAchievement(row); updateAchievementProjection(projectId, now); return row;
    }

    public List<AcceptanceFinancialSettlement> getSettlements(AuthenticatedUser user, Long projectId) {
        requireAccess(user, projectId); return structuredDao.selectFinancialSettlementsByProjectId(projectId);
    }

    @Transactional
    public AcceptanceFinancialSettlement addSettlement(AuthenticatedUser user, Long projectId, FinancialSettlementRequest request) {
        requireAccess(user, projectId);
        if (request == null || request.moduleInstanceId() == null || request.receivedAmount() == null || request.spentAmount() == null) throw badRequest("Module, received amount and spent amount are required");
        if (request.receivedAmount().signum()<0 || request.spentAmount().signum()<0) throw badRequest("Financial amounts cannot be negative");
        BigDecimal surplus=request.receivedAmount().subtract(request.spentAmount());
        BigDecimal rate=request.receivedAmount().signum()==0?BigDecimal.ZERO:request.spentAmount().multiply(new BigDecimal("100")).divide(request.receivedAmount(),2,RoundingMode.HALF_UP);
        LocalDateTime now=LocalDateTime.now(); AcceptanceFinancialSettlement row=new AcceptanceFinancialSettlement(); row.setAcceptanceId(request.acceptanceId()); row.setProjectId(projectId); row.setModuleInstanceId(request.moduleInstanceId()); row.setStateRecordId(request.stateRecordId()); row.setApprovedAmount(request.approvedAmount()); row.setReceivedAmount(request.receivedAmount()); row.setSpentAmount(request.spentAmount()); row.setSurplusAmount(surplus); row.setExecutionRate(rate); row.setSettlementResult(request.settlementResult()); row.setFinanceOperatorId(user.userId()); row.setFinanceReviewComment(request.financeReviewComment()); row.setSettledAt(request.settledAt()==null?now:request.settledAt()); row.setCreatedAt(now); row.setUpdatedAt(now); structuredDao.insertFinancialSettlement(row);
        ProjectAcceptance acceptance=acceptanceDao.selectByProjectId(projectId); if(acceptance!=null){ProjectAcceptanceExt ext=structuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId()); if(ext!=null){ext.setSurplusAmount(surplus); ext.setSurplusReturnRequired(surplus.signum()>0); ext.setSurplusReturnStatus(surplus.signum()>0?"PENDING":"NOT_REQUIRED"); ext.setUpdatedAt(now); structuredDao.updateAcceptanceExt(ext);}}
        return row;
    }

    private void updateAchievementProjection(Long projectId, LocalDateTime now) {
        ProjectAcceptance acceptance=acceptanceDao.selectByProjectId(projectId); if(acceptance==null)return; ProjectAcceptanceExt ext=structuredDao.selectAcceptanceExtByAcceptanceId(acceptance.getAcceptanceId()); if(ext==null)return;
        List<ProjectAchievement> rows=structuredDao.selectAchievementsByProjectId(projectId); ext.setPaperCount(countType(rows,"PAPER")); ext.setPatentCount(countType(rows,"PATENT")); ext.setSoftwareCopyrightCount(countType(rows,"SOFTWARE_COPYRIGHT")); ext.setOtherAchievementCount(rows.size()-ext.getPaperCount()-ext.getPatentCount()-ext.getSoftwareCopyrightCount()); ext.setUpdatedAt(now); structuredDao.updateAcceptanceExt(ext);
    }
    private int countType(List<ProjectAchievement> rows,String type){return (int)rows.stream().filter(r->type.equalsIgnoreCase(r.getAchievementType())).count();}

    private void upsertApplicationExt(Long projectId, Long applicationId, ProjectApplicationExt ext, LocalDateTime now) {
        if (ext == null) return;
        ProjectApplicationExt existing = structuredDao.selectApplicationExtByApplicationId(applicationId);
        if (existing == null) {
            ext.setApplicationId(applicationId); ext.setProjectId(projectId); ext.setApplicationExtId(null); ext.setCreatedAt(now); ext.setUpdatedAt(now); structuredDao.insertApplicationExt(ext);
        } else {
            copyNonNullProperties(ext, existing, "applicationExtId", "applicationId", "projectId", "createdAt", "updatedAt");
            existing.setApplicationId(applicationId); existing.setProjectId(projectId); existing.setUpdatedAt(now); structuredDao.updateApplicationExt(existing);
        }
    }

    private void upsertApplicationDetail(Long projectId, Long applicationId, ProjectApplicationDetail detail, LocalDateTime now) {
        if (detail == null) return;
        ProjectApplicationDetail existing = structuredDao.selectApplicationDetailByApplicationId(applicationId);
        if (existing == null) {
            detail.setApplicationId(applicationId); detail.setProjectId(projectId); detail.setApplicationDetailId(null); detail.setCreatedAt(now); detail.setUpdatedAt(now); structuredDao.insertApplicationDetail(detail);
        } else {
            copyNonNullProperties(detail, existing, "applicationDetailId", "applicationId", "projectId", "createdAt", "updatedAt");
            existing.setApplicationId(applicationId); existing.setProjectId(projectId); existing.setUpdatedAt(now); structuredDao.updateApplicationDetail(existing);
        }
    }


    private void copyNonNullProperties(Object source, Object target, String... excludedProperties) {
        Set<String> excluded = Set.of(excludedProperties);
        for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(source.getClass())) {
            if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null || excluded.contains(descriptor.getName())) {
                continue;
            }
            try {
                Object value = descriptor.getReadMethod().invoke(source);
                if (value != null) {
                    descriptor.getWriteMethod().invoke(target, value);
                }
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed to merge structured business field: " + descriptor.getName(), ex);
            }
        }
    }
    private void requireAccess(AuthenticatedUser user, Long projectId) {
        if (projectDao.selectById(projectId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        if (user == null || !permissionService.canAccessProject(user.userId(), projectId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
