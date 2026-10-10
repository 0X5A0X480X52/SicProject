package com.amatrix.sic.workflow;

import static com.amatrix.sic.workflow.ExpertReviewDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
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
    private final JdbcTemplate jdbc;
    private final WorkflowOperationPolicy operationPolicy;
    private final OutboxService outbox;
    private final WorkflowServiceTokenClient identities;
    private final WorkflowInternalTokenProvider tokens;
    private final ExpertReviewViewRepository views;
    private final ProjectAccessClient projects;

    public ExpertReviewService(JdbcTemplate jdbc, WorkflowOperationPolicy operationPolicy, OutboxService outbox,
            WorkflowServiceTokenClient identities, WorkflowInternalTokenProvider tokens,
            ExpertReviewViewRepository views, ProjectAccessClient projects) {
        this.jdbc = jdbc;
        this.operationPolicy = operationPolicy;
        this.outbox = outbox;
        this.identities = identities;
        this.tokens = tokens;
        this.views = views;
        this.projects = projects;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> create(ActorContext actor, CreateBatchRequest request) {
        if (request == null || request.moduleInstanceId() == null || blank(request.reviewType()) || blank(request.reviewTitle())) {
            throw bad("Module, review type and title are required");
        }
        Module module = lockModule(request.moduleInstanceId());
        requireOperator(actor, module.id());
        requireAssignmentNode(module, request.workflowNodeId());
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO expert_review_batch(module_instance_id,workflow_node_id,round_no,review_type,
                      review_title,rule_type,min_expert_count,pass_score,recommend_score,remove_highest_lowest,
                      expected_expert_count,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, module.id());
            if (request.workflowNodeId() == null) statement.setNull(2, java.sql.Types.BIGINT);
            else statement.setLong(2, request.workflowNodeId());
            statement.setInt(3, module.round());
            statement.setString(4, request.reviewType());
            statement.setString(5, request.reviewTitle());
            statement.setString(6, blank(request.ruleType()) ? "AVERAGE" : request.ruleType());
            statement.setInt(7, request.minExpertCount() == null ? 3 : request.minExpertCount());
            statement.setBigDecimal(8, request.passScore());
            statement.setBigDecimal(9, request.recommendScore());
            statement.setBoolean(10, Boolean.TRUE.equals(request.removeHighestLowest()));
            if (request.expectedExpertCount() == null) statement.setNull(11, java.sql.Types.INTEGER);
            else statement.setInt(11, request.expectedExpertCount());
            statement.setLong(12, actor.userId());
            return statement;
        }, holder);
        return detail(actor, Objects.requireNonNull(holder.getKey()).longValue());
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> assign(ActorContext actor, Long batchId, AssignExpertRequest request) {
        Batch batch = lockBatch(batchId);
        Module module = lockModule(batch.moduleId());
        requireActiveAssignmentBatch(batch, module);
        requireOperator(actor, batch.moduleId());
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
        try {
            jdbc.update("""
                    INSERT INTO expert_review_assignment(batch_id,expert_user_id,expert_name_snapshot,
                      expert_org_snapshot,expert_title_snapshot) VALUES (?,?,?,?,?)
                    """, batchId, request.expertUserId(), expertName, request.expertOrg(), request.expertTitle());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expert is already assigned to this review batch");
        }
        refreshAggregate(batchId);
        ExpertProjectGrantCommand grant = grant(batch, request.expertUserId(), expertName,
                actor.userId(), actor.username());
        projects.syncExpertGrant(tokens.authorization(), grant.projectId(), grant);
        return detail(actor, batchId);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> submit(ActorContext actor, Long assignmentId, SubmitScoreRequest request) {
        Assignment assignment = lockAssignment(assignmentId);
        if (!Objects.equals(actor.userId(), assignment.expertUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned expert can submit this score");
        }
        Batch batch = lockBatch(assignment.batchId());
        Module module = lockModule(batch.moduleId());
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
            jdbc.update("""
                    INSERT INTO expert_review_score(assignment_id,item_code,item_name,weight,max_score,score_value,comment)
                    VALUES (?,?,?,?,?,?,?)
                    """, assignmentId, item.itemCode(), item.itemName(), weight, max, item.scoreValue(), item.comment());
        }
        boolean conflict = Boolean.TRUE.equals(request.conflictOfInterest());
        boolean valid = !conflict && !Boolean.FALSE.equals(request.valid());
        jdbc.update("""
                UPDATE expert_review_assignment SET conflict_of_interest=?,valid=?,total_score=?,review_result=?,
                  review_comment=?,review_status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP(6) WHERE id=?
                """, conflict, valid, total.setScale(2, RoundingMode.HALF_UP), request.reviewResult(),
                request.reviewComment(), assignmentId);
        boolean completed = refreshAggregate(assignment.batchId());
        if (completed) {
            outbox.enqueue("workflow.expert-review-completed", EventEnvelope.create("EXPERT_REVIEW_COMPLETED",
                    "EXPERT_REVIEW_BATCH", assignment.batchId().toString(), null, null, actor.userId(),
                    Map.of("batchId", assignment.batchId(), "moduleInstanceId", moduleId(assignment.batchId()))));
        }
        return detail(actor, assignment.batchId());
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> remove(ActorContext actor, Long assignmentId) {
        Assignment assignment = lockAssignment(assignmentId);
        Batch batch = lockBatch(assignment.batchId());
        Module module = lockModule(batch.moduleId());
        requireActiveAssignmentBatch(batch, module);
        requireOperator(actor, batch.moduleId());
        if ("SUBMITTED".equals(assignment.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Submitted expert assignment cannot be removed");
        jdbc.update("DELETE FROM expert_review_score WHERE assignment_id=?", assignmentId);
        jdbc.update("DELETE FROM expert_review_assignment WHERE id=?", assignmentId);
        refreshAggregate(batch.id());
        if (!hasMatchingAssignment(batch, assignment.expertUserId())) {
            ExpertProjectGrantCommand grant = grant(batch, assignment.expertUserId(), null,
                    actor.userId(), actor.username());
            projects.revokeExpertGrant(tokens.authorization(), grant.projectId(), grant);
        }
        return detail(actor, batch.id());
    }

    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> detail(ActorContext actor, Long batchId) {
        Batch batch = requireBatch(batchId);
        boolean assigned = jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_assignment WHERE batch_id=? AND expert_user_id=?",
                Integer.class, batchId, actor.userId()) > 0;
        if (!assigned) requireOperator(actor, batch.moduleId());
        return views.batchDetail(batchId);
    }

    private boolean refreshAggregate(Long batchId) {
        Batch before = lockBatch(batchId);
        List<BigDecimal> values = jdbc.query("""
                SELECT total_score FROM expert_review_assignment
                 WHERE batch_id=? AND review_status='SUBMITTED' AND valid=TRUE AND conflict_of_interest=FALSE
                   AND total_score IS NOT NULL ORDER BY total_score
                """, (rs, row) -> rs.getBigDecimal(1), batchId);
        int assigned = jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_assignment WHERE batch_id=?", Integer.class, batchId);
        int submitted = jdbc.queryForObject("SELECT COUNT(*) FROM expert_review_assignment WHERE batch_id=? AND review_status='SUBMITTED'", Integer.class, batchId);
        int expected = assigned;
        List<BigDecimal> effective = new ArrayList<>(values);
        if (before.removeHighestLowest() && effective.size() >= 5) effective = effective.subList(1, effective.size() - 1);
        BigDecimal finalScore = effective.isEmpty() ? null : effective.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(effective.size()), 2, RoundingMode.HALF_UP);
        String finalResult = finalScore == null ? null : result(before, finalScore);
        boolean completed = expected > 0 && submitted >= expected;
        jdbc.update("""
                UPDATE expert_review_batch SET expected_expert_count=?,min_expert_count=?,submitted_expert_count=?,
                  valid_expert_count=?,lowest_score=?,highest_score=?,final_score=?,final_result=?,status=?,completed_at=? WHERE id=?
                """, assigned, assigned, submitted, values.size(), values.isEmpty() ? null : values.getFirst(),
                values.isEmpty() ? null : values.getLast(), finalScore, finalResult, completed ? "COMPLETED" : "IN_PROGRESS",
                completed ? LocalDateTime.now() : null, batchId);
        return completed && !"COMPLETED".equals(before.status());
    }

    private String result(Batch batch, BigDecimal score) {
        if (batch.recommendScore() != null && score.compareTo(batch.recommendScore()) >= 0) return "RECOMMENDED";
        if (batch.passScore() != null && score.compareTo(batch.passScore()) >= 0) return "PASSED";
        return "REJECTED";
    }

    private void requireOperator(ActorContext actor, Long moduleId) {
        if (!operationPolicy.exactCanOperate(actor, moduleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current task cannot be operated by this user");
        }
    }

    private Batch requireBatch(Long id) {
        List<Batch> rows = jdbc.query("SELECT * FROM expert_review_batch WHERE id=?", (rs, row) -> batch(rs), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert review batch not found");
        return rows.getFirst();
    }

    private Batch lockBatch(Long id) {
        List<Batch> rows = jdbc.query("SELECT * FROM expert_review_batch WHERE id=? FOR UPDATE", (rs, row) -> batch(rs), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert review batch not found");
        return rows.getFirst();
    }

    private Batch batch(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Batch(rs.getLong("id"), rs.getLong("module_instance_id"), nullableLong(rs, "workflow_node_id"),
                rs.getInt("round_no"), rs.getBoolean("remove_highest_lowest"),
                rs.getBigDecimal("pass_score"), rs.getBigDecimal("recommend_score"), rs.getString("status"));
    }

    private Assignment lockAssignment(Long id) {
        List<Assignment> rows = jdbc.query("SELECT id,batch_id,expert_user_id,review_status FROM expert_review_assignment WHERE id=? FOR UPDATE",
                (rs, row) -> new Assignment(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4)), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expert assignment not found");
        return rows.getFirst();
    }

    private Module lockModule(Long id) {
        List<Module> rows = jdbc.query("""
                SELECT id,workflow_definition_id,current_node_id,current_state,current_round
                  FROM module_instance WHERE id=? AND finished=FALSE FOR UPDATE
                """, (rs, row) -> new Module(rs.getLong("id"), rs.getLong("workflow_definition_id"),
                        rs.getString("current_node_id"), rs.getString("current_state"),
                        rs.getInt("current_round")), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active module instance not found");
        return rows.getFirst();
    }

    private void requireAssignmentNode(Module module, Long workflowNodeId) {
        if (workflowNodeId == null || !module.nodeId().endsWith("ExpertAssignTask")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expert review batch can only be created at the current expert assignment node");
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_node_config
                 WHERE id=? AND workflow_definition_id=? AND node_id=?
                """, Integer.class, workflowNodeId, module.definitionId(), module.nodeId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "workflowNodeId does not belong to the current workflow node");
        }
    }

    private void requireActiveAssignmentBatch(Batch batch, Module module) {
        if (batch.roundNo() != module.round() || !module.nodeId().endsWith("ExpertAssignTask")
                || !matchesConfiguredNode(batch, module, false) || "COMPLETED".equals(batch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expert assignment does not belong to the current assignment node and round");
        }
    }

    private void requireActiveReviewBatch(Batch batch, Module module) {
        if (batch.roundNo() != module.round() || !module.nodeId().endsWith("ExpertReviewTask")
                || !matchesConfiguredNode(batch, module, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Expert score does not belong to the current review node and round");
        }
    }

    private boolean matchesConfiguredNode(Batch batch, Module module, boolean reviewNode) {
        if (batch.workflowNodeId() == null) return false;
        String configured = jdbc.query("""
                SELECT node_id FROM workflow_node_config WHERE id=? AND workflow_definition_id=?
                """, (rs, row) -> rs.getString(1), batch.workflowNodeId(), module.definitionId())
                .stream().findFirst().orElse(null);
        return configured != null && Objects.equals(reviewNode ? toReviewTaskNode(configured) : configured,
                module.nodeId());
    }

    private Long moduleId(Long batchId) { return requireBatch(batchId).moduleId(); }
    private ExpertProjectGrantCommand grant(Batch batch, Long expertUserId, String expertName,
            Long grantedBy, String grantedByName) {
        return jdbc.query("""
                SELECT m.project_id,m.module_code,n.node_id
                  FROM module_instance m LEFT JOIN workflow_node_config n ON n.id=? WHERE m.id=?
                """, (rs, row) -> new ExpertProjectGrantCommand(rs.getLong("project_id"), expertUserId, expertName,
                        rs.getString("module_code"), batch.roundNo(), toReviewTaskNode(rs.getString("node_id")),
                        grantedBy, grantedByName), batch.workflowNodeId(), batch.moduleId()).getFirst();
    }
    private boolean hasMatchingAssignment(Batch batch, Long expertUserId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM expert_review_assignment a JOIN expert_review_batch b ON b.id=a.batch_id
                 WHERE b.module_instance_id=? AND b.round_no=? AND (b.workflow_node_id<=>?) AND a.expert_user_id=?
                """, Integer.class, batch.moduleId(), batch.roundNo(), batch.workflowNodeId(), expertUserId);
        return count != null && count > 0;
    }
    private static String toReviewTaskNode(String nodeId) {
        if (nodeId == null) return null;
        return nodeId.replace("ExpertAssignTask", "ExpertReviewTask")
                .replace("ExpertSummaryTask", "ExpertReviewTask");
    }
    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException { long value=rs.getLong(column);return rs.wasNull()?null:value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record Module(Long id, Long definitionId, String nodeId, String state, int round) {}
    private record Batch(Long id, Long moduleId, Long workflowNodeId, int roundNo, boolean removeHighestLowest, BigDecimal passScore,
            BigDecimal recommendScore, String status) {}
    private record Assignment(Long id, Long batchId, Long expertUserId, String status) {}
}
