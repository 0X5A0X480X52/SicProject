package com.amatrix.sicprojectis_backend.structured;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.material.dao.MaterialVersionDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectAcceptanceDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleStateRecordDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordMaterialDao;
import com.amatrix.sicprojectis_backend.runtime.dao.StateRecordRemarkDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.runtime.entity.StateRecordMaterial;
import com.amatrix.sicprojectis_backend.runtime.entity.StateRecordRemark;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.structured.dao.ProjectStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dao.RuntimeStructuredDataDao;
import com.amatrix.sicprojectis_backend.structured.dto.TransitionRequest;
import com.amatrix.sicprojectis_backend.structured.dto.TransitionResponse;
import com.amatrix.sicprojectis_backend.structured.entity.ArchiveRecord;
import com.amatrix.sicprojectis_backend.structured.entity.ExternalResultRecord;
import com.amatrix.sicprojectis_backend.structured.entity.SealRecord;
import com.amatrix.sicprojectis_backend.structured.entity.StateRecordCheckItem;
import com.amatrix.sicprojectis_backend.structured.entity.SubmissionRecord;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;
import com.amatrix.sicprojectis_backend.workflow.FlowableBpmnDefinitionParser;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.TransitionConfig;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowDefinitionDao;

@Service
public class StructuredStateMachineService {
    private final ProjectModuleInstanceDao moduleDao; private final WorkflowDefinitionDao definitionDao;
    private final FlowableBpmnDefinitionParser parser; private final ModuleStateRecordDao stateDao;
    private final StateRecordRemarkDao remarkDao; private final StateRecordMaterialDao materialLinkDao;
    private final MaterialVersionDao materialVersionDao; private final RuntimeStructuredDataDao runtimeDao;
    private final ProjectStructuredDataDao projectDataDao; private final ExpertReviewBatchDao expertBatchDao;
    private final TaskInstanceDao taskDao; private final PermissionService permissionService;
    private final ProjectApplicationDao applicationDao; private final ProjectAcceptanceDao acceptanceDao;

    public StructuredStateMachineService(ProjectModuleInstanceDao moduleDao, WorkflowDefinitionDao definitionDao,
            FlowableBpmnDefinitionParser parser, ModuleStateRecordDao stateDao, StateRecordRemarkDao remarkDao,
            StateRecordMaterialDao materialLinkDao, MaterialVersionDao materialVersionDao,
            RuntimeStructuredDataDao runtimeDao, ProjectStructuredDataDao projectDataDao,
            ExpertReviewBatchDao expertBatchDao, TaskInstanceDao taskDao, PermissionService permissionService,
            ProjectApplicationDao applicationDao, ProjectAcceptanceDao acceptanceDao) {
        this.moduleDao=moduleDao; this.definitionDao=definitionDao; this.parser=parser; this.stateDao=stateDao;
        this.remarkDao=remarkDao; this.materialLinkDao=materialLinkDao; this.materialVersionDao=materialVersionDao;
        this.runtimeDao=runtimeDao; this.projectDataDao=projectDataDao; this.expertBatchDao=expertBatchDao;
        this.taskDao=taskDao; this.permissionService=permissionService;
        this.applicationDao=applicationDao; this.acceptanceDao=acceptanceDao;
    }

    @Transactional
    public TransitionResponse transition(AuthenticatedUser user, Long moduleInstanceId, TransitionRequest request) {
        if (request == null || blank(request.eventType())) throw badRequest("Event type is required");
        ProjectModuleInstance module = requireModule(moduleInstanceId);
        WorkflowBpmnParseResult model = parser.parse(definitionDao.selectById(module.getWorkflowDefinitionId()).getBpmnXml());
        ModuleStateRecord current = latestState(moduleInstanceId);
        requirePermission(user, module, current);
        int currentSeq = current == null ? 0 : current.getSeq();
        if (!Objects.equals(request.expectedSeq(), currentSeq))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module state sequence has changed");
        String sourceNodeId = current == null ? startNode(model).nodeId() : current.getToNodeId();
        TransitionConfig initial = model.transitions().stream()
                .filter(t -> Objects.equals(t.sourceRef(), sourceNodeId) && Objects.equals(t.eventType(), request.eventType()))
                .findFirst().orElseThrow(() -> badRequest("Event is not allowed from current node"));

        LocalDateTime now = LocalDateTime.now();
        ModuleStateRecord record = new ModuleStateRecord();
        record.setModuleInstanceId(moduleInstanceId); record.setSeq(currentSeq + 1);
        record.setRoundNo(current == null ? 1 : current.getRoundNo()); record.setEventType(request.eventType());
        record.setFromState(current == null ? null : current.getToState()); record.setFromNodeId(sourceNodeId);
        record.setResult(request.result()); record.setSummary(request.remark()); record.setCreatedAt(now);
        stateDao.insert(record);
        writeRemark(user, record, request, now);
        writeNodeData(user, module, sourceNodeId, record, request, now);
        linkMaterials(record, request.materialVersionIds(), now);

        NodeConfig target = resolveTarget(model, initial.targetRef(), record, module);
        record.setToNodeId(target.nodeId()); record.setToState(target.stateCode());
        stateDao.updateById(record);
        closeTasks(moduleInstanceId, now);
        boolean finished = "END_EVENT".equals(target.nodeType());
        if (finished) { module.setFinishedAt(now); module.setUpdatedAt(now); moduleDao.updateById(module); }
        else createTask(moduleInstanceId, target, record.getRoundNo(), now);
        return new TransitionResponse(record, target.nodeId(), target.stateCode(), finished);
    }

    private NodeConfig resolveTarget(WorkflowBpmnParseResult model, String nodeId, ModuleStateRecord record, ProjectModuleInstance module) {
        NodeConfig node = node(model, nodeId);
        int hops = 0;
        while ("GATEWAY".equals(node.nodeType())) {
            if (++hops > 20) throw new IllegalStateException("Gateway traversal exceeded limit");
            NodeConfig gateway = node;
            TransitionConfig branch = model.transitions().stream().filter(t -> Objects.equals(t.sourceRef(), gateway.nodeId()))
                    .filter(t -> conditionMatches(t, record, module)).findFirst()
                    .orElseThrow(() -> badRequest("No structured gateway condition matched at " + gateway.nodeId()));
            node = node(model, branch.targetRef());
        }
        if (!"END_EVENT".equals(node.nodeType()) && blank(node.stateCode()))
            throw new IllegalStateException("Final workflow node has no stateCode: " + node.nodeId());
        return node;
    }

    private boolean conditionMatches(TransitionConfig transition, ModuleStateRecord record, ProjectModuleInstance module) {
        if (blank(transition.conditionKey())) return true;
        boolean actual;
        String key = transition.conditionKey();
        if ("isLimitedProject".equals(key)) {
            var application = projectDataDao.selectApplicationExtByApplicationId(requireApplicationId(module.getProjectId()));
            actual = application != null && Boolean.TRUE.equals(application.getIsLimitedProject());
        } else if ("isSchoolLevelAcceptance".equals(key)) {
            var acceptance = projectDataDao.selectAcceptanceExtByAcceptanceId(requireAcceptanceId(module.getProjectId()));
            actual = acceptance != null && Boolean.TRUE.equals(acceptance.getIsSchoolLevelAcceptance());
        } else if (key.toLowerCase().contains("expert")) {
            actual = expertBatchDao.selectByModuleInstanceId(module.getModuleInstanceId()).stream()
                    .max(Comparator.comparing(b -> b.getBatchId())).map(b -> approved(b.getFinalResult())).orElse(false);
        } else if (key.toLowerCase().contains("authority")) {
            var latest = runtimeDao.selectExternalResultsByModuleInstanceId(module.getModuleInstanceId()).stream()
                    .max(Comparator.comparing(ExternalResultRecord::getExternalResultId));
            actual = latest.map(r -> approved(r.getExternalResult())).orElseGet(() -> currentRemarkApproved(record));
        } else {
            actual = currentRemarkApproved(record);
        }
        return actual == Boolean.parseBoolean(transition.conditionValue());
    }

    private void writeRemark(AuthenticatedUser user, ModuleStateRecord state, TransitionRequest request, LocalDateTime now) {
        StateRecordRemark remark = new StateRecordRemark(); remark.setStateRecordId(state.getStateRecordId());
        remark.setParticipantUserId(user.userId()); remark.setParticipantType("USER"); remark.setActionType(request.eventType());
        remark.setResult(request.result()); remark.setIsOperator(true); remark.setRemarkContent(request.remark());
        remark.setIsFinal(true); remark.setSortNo(1); remark.setCreatedAt(now); remarkDao.insert(remark);
    }

    private void writeNodeData(AuthenticatedUser user, ProjectModuleInstance module, String nodeId, ModuleStateRecord state, TransitionRequest request, LocalDateTime now) {
        if (request.checkItems() != null) for (var item : request.checkItems()) {
            StateRecordCheckItem row = new StateRecordCheckItem(); row.setStateRecordId(state.getStateRecordId()); row.setModuleInstanceId(module.getModuleInstanceId());
            row.setNodeId(nodeId); row.setStateCode(state.getFromState()); row.setItemCode(item.itemCode()); row.setItemName(item.itemName());
            row.setItemType(item.itemType()); row.setItemValue(item.itemValue()); row.setItemResult(item.itemResult()); row.setRequired(item.required());
            row.setPassed(item.passed()); row.setRemark(item.remark()); row.setSortNo(item.sortNo()); row.setCreatedAt(now); runtimeDao.insertCheckItem(row);
        }
        if (request.externalResult() != null) {
            var d=request.externalResult(); ExternalResultRecord row=new ExternalResultRecord(); row.setModuleInstanceId(module.getModuleInstanceId()); row.setStateRecordId(state.getStateRecordId()); row.setModuleType(module.getModuleType());
            row.setResultType(d.resultType()); row.setExternalActorCode(d.externalActorCode()); row.setExternalActorName(d.externalActorName()); row.setExternalResult(d.externalResult()); row.setExternalResultDate(d.externalResultDate()); row.setExternalFileNo(d.externalFileNo()); row.setExternalSystemNo(d.externalSystemNo()); row.setApprovedAmount(d.approvedAmount()); row.setEffectiveDate(d.effectiveDate()); row.setSummary(d.summary()); row.setRegisteredBy(user.userId()); row.setRegisteredAt(now); row.setCreatedAt(now); runtimeDao.insertExternalResult(row);
        }
        if (request.seal() != null) {
            var d=request.seal(); SealRecord row=new SealRecord(); row.setModuleInstanceId(module.getModuleInstanceId()); row.setStateRecordId(state.getStateRecordId()); row.setModuleType(module.getModuleType()); row.setSealSubject(d.sealSubject()); row.setSealType(d.sealType()); row.setSealReason(d.sealReason()); row.setCopyCount(d.copyCount()); row.setApplicantUserId(user.userId()); row.setHandledBy(user.userId()); row.setLeaderSigned(d.leaderSigned()); row.setLeaderSignedAt(d.leaderSignedAt()); row.setSchoolSealed(d.schoolSealed()); row.setSchoolSealedAt(d.schoolSealedAt()); row.setExternalSealed(d.externalSealed()); row.setExternalActorName(d.externalActorName()); row.setExternalSealedAt(d.externalSealedAt()); row.setSealStatus(d.sealStatus()); row.setRemark(d.remark()); row.setCreatedAt(now); runtimeDao.insertSealRecord(row);
        }
        if (request.submission() != null) {
            var d=request.submission(); SubmissionRecord row=new SubmissionRecord(); row.setModuleInstanceId(module.getModuleInstanceId()); row.setStateRecordId(state.getStateRecordId()); row.setModuleType(module.getModuleType()); row.setSubmissionType(d.submissionType()); row.setTargetActorCode(d.targetActorCode()); row.setTargetActorName(d.targetActorName()); row.setSubmissionMethod(d.submissionMethod()); row.setSubmissionNo(d.submissionNo()); row.setExternalSystemNo(d.externalSystemNo()); row.setReceiptNo(d.receiptNo()); row.setSubmittedBy(user.userId()); row.setSubmittedAt(d.submittedAt()==null?now:d.submittedAt()); row.setRemark(d.remark()); row.setCreatedAt(now); runtimeDao.insertSubmissionRecord(row);
        }
        if (request.archive() != null) {
            var d=request.archive(); ArchiveRecord row=new ArchiveRecord(); row.setModuleInstanceId(module.getModuleInstanceId()); row.setStateRecordId(state.getStateRecordId()); row.setModuleType(module.getModuleType()); row.setArchiveType(d.archiveType()); row.setArchiveNo(d.archiveNo()); row.setArchiveLocation(d.archiveLocation()); row.setPaperCopyCount(d.paperCopyCount()); row.setElectronicCopyCount(d.electronicCopyCount()); row.setArchivedBy(user.userId()); row.setArchivedAt(d.archivedAt()==null?now:d.archivedAt()); row.setArchiveStatus(d.archiveStatus()); row.setRemark(d.remark()); row.setCreatedAt(now); runtimeDao.insertArchiveRecord(row);
        }
    }

    private void linkMaterials(ModuleStateRecord state, List<Long> ids, LocalDateTime now) {
        if (ids == null) return;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            if (materialVersionDao.selectById(id) == null) throw badRequest("Material version not found: " + id);
            StateRecordMaterial link=new StateRecordMaterial(); link.setStateRecordId(state.getStateRecordId()); link.setMaterialVersionId(id);
            link.setMaterialUsage("TRANSITION_INPUT"); link.setIsRequired(false); link.setLinkedAt(now); materialLinkDao.insert(link);
        }
    }

    private void closeTasks(Long moduleId, LocalDateTime now) { for (TaskInstance task : taskDao.selectOpenByModuleInstanceId(moduleId)) { task.setTaskStatus("COMPLETED"); task.setCompletedAt(now); taskDao.updateById(task); } }
    private void createTask(Long moduleId, NodeConfig node, Integer roundNo, LocalDateTime now) { TaskInstance task=new TaskInstance(); task.setModuleInstanceId(moduleId); task.setNodeId(node.nodeId()); task.setStateCode(node.stateCode()); task.setCandidateRoleCode(node.candidateRoleCode()); task.setTaskStatus("OPEN"); task.setRoundNo(roundNo); task.setCreatedAt(now); taskDao.insert(task); }
    private ModuleStateRecord latestState(Long id) { return stateDao.selectAll().stream().filter(r -> Objects.equals(r.getModuleInstanceId(), id)).max(Comparator.comparing(ModuleStateRecord::getSeq)).orElse(null); }
    private NodeConfig startNode(WorkflowBpmnParseResult model) { return model.nodes().stream().filter(n -> "START_EVENT".equals(n.nodeType())).findFirst().orElseThrow(() -> new IllegalStateException("Workflow has no start node")); }
    private NodeConfig node(WorkflowBpmnParseResult model, String id) { return model.nodes().stream().filter(n -> Objects.equals(n.nodeId(), id)).findFirst().orElseThrow(() -> new IllegalStateException("Workflow node not found: " + id)); }
    private ProjectModuleInstance requireModule(Long id) { ProjectModuleInstance module=moduleDao.selectById(id); if(module==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module instance not found"); return module; }
    private void requirePermission(AuthenticatedUser user, ProjectModuleInstance module, ModuleStateRecord current) { if(user==null || !permissionService.canAccessProject(user.userId(), module.getProjectId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Project access denied"); if(current!=null && !permissionService.canOperateModuleNode(user.userId(), module.getModuleInstanceId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Current workflow task cannot be operated by this user"); }
    private Long requireApplicationId(Long projectId) { var row=applicationDao.selectByProjectId(projectId); if(row==null) throw badRequest("Application data is missing"); return row.getApplicationId(); }
    private Long requireAcceptanceId(Long projectId) { var row=acceptanceDao.selectByProjectId(projectId); if(row==null) throw badRequest("Acceptance data is missing"); return row.getAcceptanceId(); }
    private boolean approved(String result) { return result != null && List.of("APPROVED","PASS","PASSED","RECOMMENDED","ACCEPTED").contains(result.toUpperCase()); }
    private boolean currentRemarkApproved(ModuleStateRecord record) { return remarkDao.selectAll().stream().filter(r -> Objects.equals(r.getStateRecordId(), record.getStateRecordId())).filter(r -> Boolean.TRUE.equals(r.getIsFinal())).map(r -> approved(r.getResult())).findFirst().orElse(false); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
