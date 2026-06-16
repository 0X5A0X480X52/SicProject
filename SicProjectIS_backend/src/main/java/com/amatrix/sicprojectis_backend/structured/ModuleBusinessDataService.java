package com.amatrix.sicprojectis_backend.structured;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.document.dao.ProcessDocumentDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewAssignmentDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewScoreDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialContextViewDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleStateRecordDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordRemarkDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dao.RuntimeStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dto.ExpertReviewData;
import com.amatrix.sicprojectis_backend.structured.dto.ExpertReviewData.ExpertReviewAssignmentData;
import com.amatrix.sicprojectis_backend.structured.dto.ModuleBusinessDataResponse;

@Service
public class ModuleBusinessDataService {
    private final ProjectModuleInstanceDao moduleDao;
    private final ProjectDao projectDao;
    private final StructuredBusinessService draftService;
    private final ModuleStateRecordDao stateRecordDao;
    private final StateRecordRemarkDao remarkDao;
    private final RuntimeStructuredDataDao runtimeDao;
    private final ProjectStructuredDataDao projectStructuredDao;
    private final ExpertReviewBatchDao batchDao;
    private final ExpertReviewAssignmentDao assignmentDao;
    private final ExpertReviewScoreDao scoreDao;
    private final MaterialContextViewDao materialDao;
    private final ProcessDocumentDao documentDao;
    private final PermissionService permissionService;

    public ModuleBusinessDataService(ProjectModuleInstanceDao moduleDao, ProjectDao projectDao,
            StructuredBusinessService draftService, ModuleStateRecordDao stateRecordDao, StateRecordRemarkDao remarkDao,
            RuntimeStructuredDataDao runtimeDao, ProjectStructuredDataDao projectStructuredDao,
            ExpertReviewBatchDao batchDao, ExpertReviewAssignmentDao assignmentDao, ExpertReviewScoreDao scoreDao,
            MaterialContextViewDao materialDao, ProcessDocumentDao documentDao, PermissionService permissionService) {
        this.moduleDao=moduleDao; this.projectDao=projectDao; this.draftService=draftService; this.stateRecordDao=stateRecordDao;
        this.remarkDao=remarkDao; this.runtimeDao=runtimeDao; this.projectStructuredDao=projectStructuredDao;
        this.batchDao=batchDao; this.assignmentDao=assignmentDao; this.scoreDao=scoreDao; this.materialDao=materialDao;
        this.documentDao=documentDao; this.permissionService=permissionService;
    }

    public ModuleBusinessDataResponse get(AuthenticatedUser user, Long moduleInstanceId) {
        ProjectModuleInstance module = moduleDao.selectById(moduleInstanceId);
        if (module == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module instance not found");
        if (user == null || !permissionService.canAccessProject(user.userId(), module.getProjectId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project access denied");
        var states = stateRecordDao.selectAll().stream().filter(r -> moduleInstanceId.equals(r.getModuleInstanceId())).toList();
        Set<Long> stateIds = states.stream().map(r -> r.getStateRecordId()).collect(Collectors.toSet());
        var remarks = remarkDao.selectAll().stream().filter(r -> stateIds.contains(r.getStateRecordId())).toList();
        Object draft = switch (module.getModuleType()) {
            case "APPLICATION" -> draftService.getApplication(user, module.getProjectId());
            case "CONTRACT" -> draftService.getContract(user, module.getProjectId());
            case "ACCEPTANCE" -> draftService.getAcceptance(user, module.getProjectId());
            default -> null;
        };
        List<ExpertReviewData> expertReviews = batchDao.selectByModuleInstanceId(moduleInstanceId).stream().map(batch ->
                new ExpertReviewData(batch, assignmentDao.selectByBatchId(batch.getBatchId()).stream()
                        .map(a -> new ExpertReviewAssignmentData(a, scoreDao.selectByAssignmentId(a.getAssignmentId()))).toList())).toList();
        return new ModuleBusinessDataResponse(module, projectDao.selectById(module.getProjectId()), draft, states, remarks,
                runtimeDao.selectCheckItemsByModuleInstanceId(moduleInstanceId), runtimeDao.selectExternalResultsByModuleInstanceId(moduleInstanceId),
                runtimeDao.selectSealRecordsByModuleInstanceId(moduleInstanceId), runtimeDao.selectSubmissionRecordsByModuleInstanceId(moduleInstanceId),
                runtimeDao.selectArchiveRecordsByModuleInstanceId(moduleInstanceId),
                projectStructuredDao.selectPublicitiesByProjectId(module.getProjectId()),
                projectStructuredDao.selectFinancialSettlementsByProjectId(module.getProjectId()),
                projectStructuredDao.selectAchievementsByProjectId(module.getProjectId()),
                projectStructuredDao.selectSurplusReturnsByProjectId(module.getProjectId()), expertReviews,
                materialDao.selectAll().stream().filter(m -> module.getProjectId().equals(m.getProjectId())).toList(),
                documentDao.selectAll().stream().filter(d -> moduleInstanceId.equals(d.getModuleInstanceId())).toList());
    }
}
