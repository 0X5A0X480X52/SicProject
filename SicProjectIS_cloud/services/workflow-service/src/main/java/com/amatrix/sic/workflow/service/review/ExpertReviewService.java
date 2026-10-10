package com.amatrix.sic.workflow.service.review;

import com.amatrix.sic.workflow.dao.review.ExpertReviewViewRepository;
import com.amatrix.sic.workflow.dto.ExpertReviewDtos;
import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;
import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.WorkflowServiceTokenClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;

import static com.amatrix.sic.workflow.dto.ExpertReviewDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import com.amatrix.sic.workflow.dao.ExpertReviewMapper;
import com.amatrix.sic.workflow.entity.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.messaging.OutboxService;

@Service
public class ExpertReviewService {
    private final ExpertReviewMapper mapper;
    private final WorkflowOperationPolicy operationPolicy;
    private final OutboxService outbox;
    private final WorkflowServiceTokenClient identities;
    private final WorkflowInternalTokenProvider tokens;
    private final ExpertReviewViewRepository views;
    private final ProjectAccessClient projects;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public ExpertReviewService(ExpertReviewMapper mapper, WorkflowOperationPolicy operationPolicy, OutboxService outbox,
            WorkflowServiceTokenClient identities, WorkflowInternalTokenProvider tokens,
            ExpertReviewViewRepository views, ProjectAccessClient projects,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.operationPolicy = operationPolicy;
        this.outbox = outbox;
        this.identities = identities;
        this.tokens = tokens;
        this.views = views;
        this.projects = projects;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
        this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> create(ActorContext actor, CreateBatchRequest request) {
        if (request == null || request.moduleInstanceId() == null || blank(request.reviewType()) || blank(request.reviewTitle())) {
            throw bad("ExpertModule, review type and title are required");
        }
        ExpertModule module = lockModule(request.moduleInstanceId());
        requireOperator(actor, module.id());
        requireAssignmentNode(module, request.workflowNodeId());
        Map<String,Object> insert=linked("module",module.id(),"node",request.workflowNodeId(),"round",module.round(),"type",request.reviewType(),
                "title",request.reviewTitle(),"rule",blank(request.ruleType())?"AVERAGE":request.ruleType(),"min",request.minExpertCount()==null?3:request.minExpertCount(),
                "pass",request.passScore(),"recommend",request.recommendScore(),"remove",Boolean.TRUE.equals(request.removeHighestLowest()),"expected",request.expectedExpertCount(),"actor",actor.userId());
        mapper.insertBatch(insert);
        return views.batchDetail(((Number)Objects.requireNonNull(insert.get("id"))).longValue());
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> assign(ActorContext actor, Long batchId, AssignExpertRequest request) {
        if (request == null || request.expertUserId() == null) throw bad("Expert user is required");
        if (Objects.equals(request.expertUserId(), actor.userId())) throw bad("The assigner cannot assign themselves as expert");
        var identity = identities.summary(tokens.authorization(), request.expertUserId());
        if (!identity.success() || identity.data() == null || !identity.data().enabled()) {
            throw bad("Selected expert does not exist or is disabled");
        }
        if (!identity.data().roleCodes().contains("EXPERT")) {
            throw bad("Selected user does not have an approved expert qualification");
        }
        String expertName = blank(request.expertName()) ? identity.data().realName() : request.expertName();
        return transactions.execute(status -> assignLocked(actor,batchId,request,expertName));
    }

    private Map<String,Object> assignLocked(ActorContext actor, Long batchId, AssignExpertRequest request, String expertName) {
        ExpertBatch observed = requireBatch(batchId);
        ExpertModule module = lockModule(observed.moduleId());
        ExpertBatch batch = lockBatch(batchId);
        requireSameModule(batch, module);
        requireActiveAssignmentBatch(batch, module);
        requireOperator(actor, batch.moduleId());
        try {
            mapper.insertAssignment(batchId,request.expertUserId(),expertName,request.expertOrg(),request.expertTitle());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expert is already assigned to this review batch");
        }
        refreshAggregate(batchId);
        ExpertProjectGrantCommand grant = grant(batch, request.expertUserId(), expertName,
                actor.userId(), actor.username());
        projects.syncExpertGrant(tokens.authorization(), grant.projectId(), grant);
        return views.batchDetail(batchId);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> submit(ActorContext actor, Long assignmentId, SubmitScoreRequest request) {
        Long batchId = assignmentBatchId(assignmentId);
        ExpertBatch observed = requireBatch(batchId);
        ExpertModule module = lockModule(observed.moduleId());
        ExpertBatch batch = lockBatch(batchId);
        requireSameModule(batch, module);
        ExpertAssignment assignment = lockAssignment(assignmentId);
        requireSameBatch(assignment, batch);
        if (!Objects.equals(actor.userId(), assignment.expertUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned expert can submit this score");
        }
        requireActiveReviewBatch(batch, module);
        requireOperator(actor, module.id());
        if ("SUBMITTED".equals(assignment.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Score was already submitted");
        if (request == null || request.scores() == null || request.scores().isEmpty()) throw bad("At least one score item is required");
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreItem item : request.scores()) {
            if (blank(item.itemCode()) || blank(item.itemName()) || item.scoreValue() == null) throw bad("Score item code, name and value are required");
            BigDecimal max = item.maxScore() == null ? new BigDecimal("100") : item.maxScore();
            if (item.scoreValue().signum() < 0 || item.scoreValue().compareTo(max) > 0) throw bad("Score must be between zero and maxScore");
            BigDecimal weight = item.weight() == null ? BigDecimal.ONE : item.weight();
            total = total.add(item.scoreValue().multiply(weight));
            mapper.insertScore(linked("assignment",assignmentId,"code",item.itemCode(),"name",item.itemName(),"weight",weight,"max",max,"score",item.scoreValue(),"comment",item.comment()));
        }
        boolean conflict = Boolean.TRUE.equals(request.conflictOfInterest());
        boolean valid = !conflict && !Boolean.FALSE.equals(request.valid());
        mapper.submit(linked("id",assignmentId,"conflict",conflict,"valid",valid,"total",total.setScale(2,RoundingMode.HALF_UP),
                "result",request.reviewResult(),"comment",request.reviewComment()));
        boolean completed = refreshAggregate(assignment.batchId());
        if (completed) {
            outbox.enqueue("workflow.expert-review-completed", EventEnvelope.create("EXPERT_REVIEW_COMPLETED",
                    "EXPERT_REVIEW_BATCH", assignment.batchId().toString(), null, null, actor.userId(),
                    Map.of("batchId", assignment.batchId(), "moduleInstanceId", moduleId(assignment.batchId()))));
        }
        return views.batchDetail(assignment.batchId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> remove(ActorContext actor, Long assignmentId) {
        Long batchId = assignmentBatchId(assignmentId);
        ExpertBatch observed = requireBatch(batchId);
        ExpertModule module = lockModule(observed.moduleId());
        ExpertBatch batch = lockBatch(batchId);
        requireSameModule(batch, module);
        ExpertAssignment assignment = lockAssignment(assignmentId);
        requireSameBatch(assignment, batch);
        requireActiveAssignmentBatch(batch, module);
        requireOperator(actor, batch.moduleId());
        if ("SUBMITTED".equals(assignment.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Submitted expert assignment cannot be removed");
        mapper.deleteScores(assignmentId);
        mapper.deleteAssignment(assignmentId);
        refreshAggregate(batch.id());
        if (!hasMatchingAssignment(batch, assignment.expertUserId())) {
            ExpertProjectGrantCommand grant = grant(batch, assignment.expertUserId(), null,
                    actor.userId(), actor.username());
            projects.revokeExpertGrant(tokens.authorization(), grant.projectId(), grant);
        }
        return views.batchDetail(batch.id());
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> detail(ActorContext actor, Long batchId) {
        ExpertBatch batch = requireBatch(batchId);
        boolean assigned = mapper.assigned(batchId,actor.userId()) > 0;
        if (!assigned) requireOperator(actor, batch.moduleId());
        return views.batchDetail(batchId);
    }

    private boolean refreshAggregate(Long batchId) {
        ExpertBatch before = lockBatch(batchId);
        List<BigDecimal> values = mapper.scores(batchId);
        int assigned = mapper.assignedCount(batchId);
        int submitted = mapper.submittedCount(batchId);
        int expected = assigned;
        List<BigDecimal> effective = new ArrayList<>(values);
        if (before.removeHighestLowest() && effective.size() >= 5) effective = effective.subList(1, effective.size() - 1);
        BigDecimal finalScore = effective.isEmpty() ? null : effective.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(effective.size()), 2, RoundingMode.HALF_UP);
        String finalResult = finalScore == null ? null : result(before, finalScore);
        boolean completed = expected > 0 && submitted >= expected;
        mapper.aggregate(linked("id",batchId,"assigned",assigned,"submitted",submitted,"valid",values.size(),
                "lowest",values.isEmpty()?null:values.getFirst(),"highest",values.isEmpty()?null:values.getLast(),"score",finalScore,"result",finalResult,
                "status",completed?"COMPLETED":"IN_PROGRESS","completed",completed?LocalDateTime.now():null));
        return completed && !"COMPLETED".equals(before.status());
    }

    private String result(ExpertBatch batch, BigDecimal score) {
        if (batch.recommendScore() != null && score.compareTo(batch.recommendScore()) >= 0) return "RECOMMENDED";
        if (batch.passScore() != null && score.compareTo(batch.passScore()) >= 0) return "PASSED";
        return "REJECTED";
    }

    private void requireOperator(ActorContext actor, Long moduleId) {
        if (!operationPolicy.exactCanOperate(actor, moduleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current task cannot be operated by this user");
        }
    }

    private ExpertBatch requireBatch(Long id) {
        ExpertBatch batch=mapper.batch(id,false);
        if(batch==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expert review batch not found");
        return batch;
    }

    private ExpertBatch lockBatch(Long id) {
        ExpertBatch batch=mapper.batch(id,true);
        if(batch==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expert review batch not found");
        return batch;
    }

    private ExpertAssignment lockAssignment(Long id) {
        ExpertAssignment assignment=mapper.lockAssignment(id);
        if(assignment==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expert assignment not found");
        return assignment;
    }

    private Long assignmentBatchId(Long id) {
        Long batchId=mapper.assignmentBatch(id);
        if(batchId==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expert assignment not found");
        return batchId;
    }

    private static void requireSameModule(ExpertBatch batch, ExpertModule module) {
        if (!Objects.equals(batch.moduleId(), module.id()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Expert batch module changed");
    }

    private static void requireSameBatch(ExpertAssignment assignment, ExpertBatch batch) {
        if (!Objects.equals(assignment.batchId(), batch.id()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Expert assignment batch changed");
    }

    private ExpertModule lockModule(Long id) {
        ExpertModule module=mapper.lockModule(id);
        if(module==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active module instance not found");
        return module;
    }

    private void requireAssignmentNode(ExpertModule module, Long workflowNodeId) {
        if (workflowNodeId == null || !module.nodeId().endsWith("ExpertAssignTask")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expert review batch can only be created at the current expert assignment node");
        }
        Integer count = mapper.nodeMatches(workflowNodeId,module.definitionId(),module.nodeId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "workflowNodeId does not belong to the current workflow node");
        }
    }

    private void requireActiveAssignmentBatch(ExpertBatch batch, ExpertModule module) {
        if (batch.roundNo() != module.round() || !module.nodeId().endsWith("ExpertAssignTask")
                || !matchesConfiguredNode(batch, module, false) || "COMPLETED".equals(batch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expert assignment does not belong to the current assignment node and round");
        }
    }

    private void requireActiveReviewBatch(ExpertBatch batch, ExpertModule module) {
        if (batch.roundNo() != module.round() || !module.nodeId().endsWith("ExpertReviewTask")
                || !matchesConfiguredNode(batch, module, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expert score does not belong to the current review node and round");
        }
    }

    private boolean matchesConfiguredNode(ExpertBatch batch, ExpertModule module, boolean reviewNode) {
        if (batch.workflowNodeId() == null) return false;
        String configured = mapper.nodeCode(batch.workflowNodeId(),module.definitionId());
        return configured != null && Objects.equals(reviewNode ? toReviewTaskNode(configured) : configured,
                module.nodeId());
    }

    private Long moduleId(Long batchId) { return requireBatch(batchId).moduleId(); }
    private ExpertProjectGrantCommand grant(ExpertBatch batch, Long expertUserId, String expertName,
            Long grantedBy, String grantedByName) {
        var scope=mapper.grant(batch.workflowNodeId(),batch.moduleId());
        if(scope==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module instance not found");
        return new ExpertProjectGrantCommand(scope.projectId(),expertUserId,expertName,scope.moduleType(),batch.roundNo(),
                toReviewTaskNode(scope.nodeId()),grantedBy,grantedByName);
    }
    private boolean hasMatchingAssignment(ExpertBatch batch, Long expertUserId) {
        Integer count = mapper.matchingAssignments(batch.moduleId(),batch.roundNo(),batch.workflowNodeId(),expertUserId);
        return count != null && count > 0;
    }
    private static String toReviewTaskNode(String nodeId) {
        if (nodeId == null) return null;
        return nodeId.replace("ExpertAssignTask", "ExpertReviewTask")
                .replace("ExpertSummaryTask", "ExpertReviewTask");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static Map<String,Object> linked(Object... pairs) {
        Map<String,Object> result=new java.util.LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);
        return result;
    }
}
