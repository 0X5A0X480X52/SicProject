package com.amatrix.sicprojectis_backend.expert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewAssignmentDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewBatchDao;
import com.amatrix.sicprojectis_backend.expert.dao.ExpertReviewScoreDao;
import com.amatrix.sicprojectis_backend.expert.dto.AssignExpertRequest;
import com.amatrix.sicprojectis_backend.expert.dto.CreateExpertReviewBatchRequest;
import com.amatrix.sicprojectis_backend.expert.dto.ExpertReviewBatchDetailResponse;
import com.amatrix.sicprojectis_backend.expert.dto.SubmitExpertScoreRequest;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewAssignment;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewBatch;
import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewScore;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleRuntimeContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dto.ExpertReviewData.ExpertReviewAssignmentData;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowNodeDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;

@Service
public class ExpertReviewService {
    private final ExpertReviewBatchDao batchDao;
    private final ExpertReviewAssignmentDao assignmentDao;
    private final ExpertReviewScoreDao scoreDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final ProjectModuleInstanceDao moduleDao;
    private final WorkflowNodeDao workflowNodeDao;
    private final ModuleRuntimeContextViewDao runtimeContextViewDao;
    private final ApplicationEventPublisher eventPublisher;

    public ExpertReviewService(ExpertReviewBatchDao batchDao, ExpertReviewAssignmentDao assignmentDao,
            ExpertReviewScoreDao scoreDao, ProjectRoleGrantDao projectRoleGrantDao, ProjectModuleInstanceDao moduleDao,
            WorkflowNodeDao workflowNodeDao, ModuleRuntimeContextViewDao runtimeContextViewDao,
            ApplicationEventPublisher eventPublisher) {
        this.batchDao = batchDao;
        this.assignmentDao = assignmentDao;
        this.scoreDao = scoreDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.moduleDao = moduleDao;
        this.workflowNodeDao = workflowNodeDao;
        this.runtimeContextViewDao = runtimeContextViewDao;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ExpertReviewBatchDetailResponse create(AuthenticatedUser user, CreateExpertReviewBatchRequest request) {
        if (request == null || request.moduleInstanceId() == null || blank(request.reviewType()) || blank(request.reviewTitle())) {
            throw badRequest("Module, review type and title are required");
        }
        ProjectModuleInstance module = moduleDao.selectById(request.moduleInstanceId());
        if (module == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module instance not found");
        }
        ModuleRuntimeContextView ctx = runtimeContextViewDao.selectByModuleInstanceId(request.moduleInstanceId());
        Integer roundNo = ctx == null || ctx.getCurrentRoundNo() == null ? 1 : ctx.getCurrentRoundNo();
        Long reviewWorkflowNodeId = resolveReviewWorkflowNodeId(module, request.workflowNodeId(), ctx);

        LocalDateTime now = LocalDateTime.now();
        ExpertReviewBatch batch = new ExpertReviewBatch();
        batch.setModuleInstanceId(request.moduleInstanceId());
        batch.setWorkflowNodeId(reviewWorkflowNodeId);
        batch.setRoundNo(roundNo);
        batch.setReviewType(request.reviewType());
        batch.setReviewTitle(request.reviewTitle());
        batch.setRuleType(request.ruleType() == null ? "AVERAGE" : request.ruleType());
        batch.setMinExpertCount(request.minExpertCount() == null ? 3 : request.minExpertCount());
        batch.setPassScore(request.passScore());
        batch.setRecommendScore(request.recommendScore());
        batch.setRemoveHighestLowest(Boolean.TRUE.equals(request.removeHighestLowest()));
        batch.setExpectedExpertCount(request.expectedExpertCount());
        batch.setSubmittedExpertCount(0);
        batch.setValidExpertCount(0);
        batch.setStatus("IN_PROGRESS");
        batch.setCreatedBy(user.userId());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batchDao.insert(batch);
        return detail(batch.getBatchId());
    }

    @Transactional
    public ExpertReviewBatchDetailResponse assign(AuthenticatedUser user, Long batchId, AssignExpertRequest request) {
        ExpertReviewBatch batch = requireBatch(batchId);
        if (request == null || request.expertUserId() == null) {
            throw badRequest("Expert user is required");
        }
        boolean alreadyAssigned = assignmentDao.selectByBatchId(batchId).stream()
                .anyMatch(a -> Objects.equals(a.getExpertUserId(), request.expertUserId()));
        if (alreadyAssigned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "????????????");
        }

        LocalDateTime now = LocalDateTime.now();
        ExpertReviewAssignment row = new ExpertReviewAssignment();
        row.setBatchId(batchId);
        row.setExpertUserId(request.expertUserId());
        row.setExpertName(request.expertName());
        row.setExpertOrg(request.expertOrg());
        row.setExpertTitle(request.expertTitle());
        row.setAssignedAt(now);
        row.setReviewStatus("ASSIGNED");
        row.setConflictOfInterest(false);
        row.setIsValid(true);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        assignmentDao.insert(row);

        ProjectModuleInstance module = moduleDao.selectById(batch.getModuleInstanceId());
        if (module != null) {
            Integer roundNo = batch.getRoundNo();
            String reviewTaskNodeId = reviewTaskNodeId(batch.getWorkflowNodeId());
            List<ProjectRoleGrant> existingGrants = projectRoleGrantDao.selectMatchingActiveGrant(
                    module.getProjectId(), module.getModuleType(), "PROJECT_MODULE_EXPERT_ASSIGNMENT",
                    request.expertUserId(), roundNo, reviewTaskNodeId);
            if (existingGrants.isEmpty()) {
                ProjectRoleGrant grant = new ProjectRoleGrant();
                grant.setProjectId(module.getProjectId());
                grant.setModuleType(module.getModuleType());
                grant.setGrantRoleCode("PROJECT_MODULE_EXPERT_ASSIGNMENT");
                grant.setGranteeUserId(request.expertUserId());
                grant.setGrantedByUserId(user.userId());
                grant.setGrantScope("MODULE");
                grant.setRoundNo(roundNo);
                grant.setTaskNodeId(reviewTaskNodeId);
                grant.setStatus("ACTIVE");
                grant.setEffectiveFrom(now);
                grant.setGrantReason("????-??????");
                grant.setCreatedAt(now);
                grant.setUpdatedAt(now);
                projectRoleGrantDao.insert(grant);
            }
        }
        int assignedCount = assignmentDao.selectByBatchId(batchId).size();
        batch.setExpectedExpertCount(assignedCount);
        batch.setMinExpertCount(assignedCount);
        batch.setStatus("IN_PROGRESS");
        batch.setCompletedAt(null);
        batch.setUpdatedAt(now);
        batchDao.updateById(batch);
        return detail(batchId);
    }

    @Transactional
    public ExpertReviewBatchDetailResponse submit(AuthenticatedUser user, Long assignmentId, SubmitExpertScoreRequest request) {
        ExpertReviewAssignment assignment = assignmentDao.selectById(assignmentId);
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert assignment not found");
        }
        if (!Objects.equals(assignment.getExpertUserId(), user.userId()) && !isSystemAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "??????????????????????");
        }
        if ("SUBMITTED".equals(assignment.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "??????????");
        }
        if (request == null || request.scores() == null || request.scores().isEmpty()) {
            throw badRequest("At least one score item is required");
        }

        ExpertReviewBatch before = requireBatch(assignment.getBatchId());
        boolean wasCompleted = "COMPLETED".equals(before.getStatus());
        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        for (var item : request.scores()) {
            if (item.scoreValue() == null) {
                throw badRequest("Score value is required");
            }
            BigDecimal weight = item.weight() == null ? BigDecimal.ONE : item.weight();
            total = total.add(item.scoreValue().multiply(weight));
            ExpertReviewScore score = new ExpertReviewScore();
            score.setAssignmentId(assignmentId);
            score.setScoreItemCode(item.itemCode());
            score.setScoreItemName(item.itemName());
            score.setWeight(weight);
            score.setMaxScore(item.maxScore() == null ? new BigDecimal("100") : item.maxScore());
            score.setScoreValue(item.scoreValue());
            score.setComment(item.comment());
            score.setCreatedAt(now);
            scoreDao.insert(score);
        }
        assignment.setConflictOfInterest(Boolean.TRUE.equals(request.conflictOfInterest()));
        assignment.setIsValid(!assignment.getConflictOfInterest() && !Boolean.FALSE.equals(request.valid()));
        assignment.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
        assignment.setReviewResult(request.reviewResult());
        assignment.setReviewComment(request.reviewComment());
        assignment.setReviewStatus("SUBMITTED");
        assignment.setSubmittedAt(now);
        assignment.setUpdatedAt(now);
        assignmentDao.updateById(assignment);

        ExpertReviewBatch after = aggregate(assignment.getBatchId(), now);
        if (!wasCompleted && "COMPLETED".equals(after.getStatus())) {
            eventPublisher.publishEvent(new ExpertReviewBatchCompletedEvent(after.getBatchId()));
        }
        return detail(assignment.getBatchId());
    }

    @Transactional
    public ExpertReviewBatchDetailResponse removeAssignment(AuthenticatedUser user, Long assignmentId) {
        ExpertReviewAssignment assignment = assignmentDao.selectById(assignmentId);
        if (assignment == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert assignment not found");
        if ("SUBMITTED".equals(assignment.getReviewStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "????????????");
        if (!scoreDao.selectByAssignmentId(assignmentId).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "?????????????");
        ExpertReviewBatch batch = requireBatch(assignment.getBatchId());
        ProjectModuleInstance module = moduleDao.selectById(batch.getModuleInstanceId());
        if (module != null) {
            Integer roundNo = batch.getRoundNo();
            String reviewTaskNodeId = reviewTaskNodeId(batch.getWorkflowNodeId());
            projectRoleGrantDao.selectMatchingActiveGrant(module.getProjectId(), module.getModuleType(), "PROJECT_MODULE_EXPERT_ASSIGNMENT", assignment.getExpertUserId(), roundNo, reviewTaskNodeId)
                    .forEach(grant -> projectRoleGrantDao.deleteById(grant.getProjectRoleGrantId()));
        }
        assignmentDao.deleteById(assignmentId);
        var remainingAssignments = assignmentDao.selectByBatchId(batch.getBatchId());
        int remaining = remainingAssignments.size();
        int submitted = (int) remainingAssignments.stream().filter(a -> "SUBMITTED".equals(a.getReviewStatus())).count();
        batch.setExpectedExpertCount(remaining);
        batch.setMinExpertCount(remaining);
        batch.setSubmittedExpertCount(submitted);
        if (remaining > 0 && submitted >= remaining) {
            batch.setStatus("COMPLETED");
            batch.setCompletedAt(LocalDateTime.now());
        } else {
            batch.setStatus("IN_PROGRESS");
            batch.setCompletedAt(null);
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchDao.updateById(batch);
        return detail(batch.getBatchId());
    }

    public ExpertReviewBatchDetailResponse detail(Long batchId) {
        ExpertReviewBatch batch = requireBatch(batchId);
        return new ExpertReviewBatchDetailResponse(batch, assignmentDao.selectByBatchId(batchId).stream()
                .map(a -> new ExpertReviewAssignmentData(a, scoreDao.selectByAssignmentId(a.getAssignmentId())))
                .toList());
    }

    private ExpertReviewBatch aggregate(Long batchId, LocalDateTime now) {
        ExpertReviewBatch batch = requireBatch(batchId);
        var allAssignments = assignmentDao.selectByBatchId(batchId);
        List<BigDecimal> scores = allAssignments.stream()
                .filter(a -> "SUBMITTED".equals(a.getReviewStatus()))
                .filter(a -> Boolean.TRUE.equals(a.getIsValid()) && !Boolean.TRUE.equals(a.getConflictOfInterest()))
                .map(ExpertReviewAssignment::getTotalScore)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        int submitted = (int) allAssignments.stream().filter(a -> "SUBMITTED".equals(a.getReviewStatus())).count();
        int expected = batch.getExpectedExpertCount() == null ? allAssignments.size() : batch.getExpectedExpertCount();
        batch.setSubmittedExpertCount(submitted);
        batch.setValidExpertCount(scores.size());
        if (!scores.isEmpty()) {
            batch.setLowestScore(scores.getFirst());
            batch.setHighestScore(scores.getLast());
            List<BigDecimal> effective = scores;
            if (Boolean.TRUE.equals(batch.getRemoveHighestLowest()) && scores.size() >= 5) {
                effective = scores.subList(1, scores.size() - 1);
            }
            BigDecimal finalScore = effective.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(effective.size()), 2, RoundingMode.HALF_UP);
            batch.setFinalScore(finalScore);
            batch.setFinalResult(result(batch, finalScore));
        }
        if (expected > 0 && submitted >= expected) {
            batch.setStatus("COMPLETED");
            if (batch.getCompletedAt() == null) {
                batch.setCompletedAt(now);
            }
        } else {
            batch.setStatus("IN_PROGRESS");
            batch.setCompletedAt(null);
        }
        batch.setUpdatedAt(now);
        batchDao.updateById(batch);
        return batch;
    }

    private Long resolveReviewWorkflowNodeId(ProjectModuleInstance module, Long requestedWorkflowNodeId, ModuleRuntimeContextView ctx) {
        WorkflowNode requestedNode = requestedWorkflowNodeId == null ? null : workflowNodeDao.selectById(requestedWorkflowNodeId);
        String sourceNodeId = requestedNode == null ? null : requestedNode.getNodeId();
        if (sourceNodeId == null && ctx != null) {
            sourceNodeId = ctx.getCurrentNodeId();
        }
        String reviewTaskNodeId = toReviewTaskNodeId(sourceNodeId);
        if (reviewTaskNodeId != null) {
            WorkflowNode reviewNode = workflowNodeDao.selectByWorkflowDefinitionIdAndNodeId(module.getWorkflowDefinitionId(), reviewTaskNodeId);
            if (reviewNode != null) {
                return reviewNode.getWorkflowNodeId();
            }
        }
        return requestedWorkflowNodeId;
    }

    private String reviewTaskNodeId(Long workflowNodeId) {
        if (workflowNodeId == null) return null;
        WorkflowNode wnode = workflowNodeDao.selectById(workflowNodeId);
        return toReviewTaskNodeId(wnode == null ? null : wnode.getNodeId());
    }

    private String result(ExpertReviewBatch batch, BigDecimal score) {
        if (batch.getRecommendScore() != null && score.compareTo(batch.getRecommendScore()) >= 0) return "RECOMMENDED";
        if (batch.getPassScore() != null && score.compareTo(batch.getPassScore()) >= 0) return "PASSED";
        return "REJECTED";
    }

    private String toReviewTaskNodeId(String nodeId) {
        if (nodeId == null) return null;
        return nodeId.replace("ExpertAssignTask", "ExpertReviewTask").replace("ExpertSummaryTask", "ExpertReviewTask");
    }

    private boolean isSystemAdmin(AuthenticatedUser user) {
        return user != null && user.roleCodes() != null && user.roleCodes().contains("SYSTEM_ADMIN");
    }

    private ExpertReviewBatch requireBatch(Long id) {
        ExpertReviewBatch batch = batchDao.selectById(id);
        if (batch == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert review batch not found");
        return batch;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
