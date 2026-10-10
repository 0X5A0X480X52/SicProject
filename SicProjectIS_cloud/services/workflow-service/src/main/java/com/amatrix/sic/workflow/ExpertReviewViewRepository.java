package com.amatrix.sic.workflow;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Builds the expert-review response shape consumed by the existing frontend. */
@Repository
public class ExpertReviewViewRepository {
    private final JdbcTemplate jdbc;

    public ExpertReviewViewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> batchDetail(Long batchId) {
        Map<String, Object> batch = jdbc.query("SELECT * FROM expert_review_batch WHERE id=?",
                (rs, row) -> batch(rs), batchId).stream().findFirst().orElse(null);
        if (batch == null) return null;
        return linked("batch", batch, "assignments", assignments(batchId));
    }

    public List<Map<String, Object>> moduleReviews(Long moduleInstanceId) {
        return jdbc.query("""
                SELECT b.*, n.node_id AS configured_node_id
                  FROM expert_review_batch b
                  LEFT JOIN workflow_node_config n ON n.id=b.workflow_node_id
                 WHERE b.module_instance_id=?
                 ORDER BY b.id
                """, (rs, row) -> linked(
                        "batch", batch(rs),
                        "reviewNodeId", toReviewTaskNodeId(rs.getString("configured_node_id")),
                        "assignments", assignments(rs.getLong("id"))), moduleInstanceId);
    }

    private List<Map<String, Object>> assignments(Long batchId) {
        return jdbc.query("SELECT * FROM expert_review_assignment WHERE batch_id=? ORDER BY id", (rs, row) -> {
            long assignmentId = rs.getLong("id");
            Map<String, Object> assignment = linked(
                    "assignmentId", assignmentId, "batchId", batchId,
                    "expertUserId", rs.getLong("expert_user_id"),
                    "expertName", rs.getString("expert_name_snapshot"),
                    "expertOrg", rs.getString("expert_org_snapshot"),
                    "expertTitle", rs.getString("expert_title_snapshot"),
                    "reviewStatus", rs.getString("review_status"),
                    "conflictOfInterest", rs.getBoolean("conflict_of_interest"),
                    "isValid", rs.getBoolean("valid"),
                    "totalScore", rs.getBigDecimal("total_score"),
                    "reviewResult", rs.getString("review_result"),
                    "reviewComment", rs.getString("review_comment"),
                    "assignedAt", rs.getObject("assigned_at", LocalDateTime.class),
                    "submittedAt", rs.getObject("submitted_at", LocalDateTime.class));
            return linked("assignment", assignment, "scores", scores(assignmentId));
        }, batchId);
    }

    private List<Map<String, Object>> scores(Long assignmentId) {
        return jdbc.query("SELECT * FROM expert_review_score WHERE assignment_id=? ORDER BY id", (rs, row) -> linked(
                "scoreId", rs.getLong("id"), "assignmentId", assignmentId,
                "scoreItemCode", rs.getString("item_code"), "scoreItemName", rs.getString("item_name"),
                "weight", rs.getBigDecimal("weight"), "maxScore", rs.getBigDecimal("max_score"),
                "scoreValue", rs.getBigDecimal("score_value"), "comment", rs.getString("comment")), assignmentId);
    }

    private static Map<String, Object> batch(java.sql.ResultSet rs) throws java.sql.SQLException {
        return linked(
                "batchId", rs.getLong("id"), "moduleInstanceId", rs.getLong("module_instance_id"),
                "workflowNodeId", nullableLong(rs, "workflow_node_id"), "roundNo", rs.getInt("round_no"),
                "reviewType", rs.getString("review_type"), "reviewTitle", rs.getString("review_title"),
                "ruleType", rs.getString("rule_type"), "minExpertCount", rs.getInt("min_expert_count"),
                "passScore", rs.getBigDecimal("pass_score"), "recommendScore", rs.getBigDecimal("recommend_score"),
                "removeHighestLowest", rs.getBoolean("remove_highest_lowest"),
                "expectedExpertCount", nullableInteger(rs, "expected_expert_count"),
                "submittedExpertCount", rs.getInt("submitted_expert_count"),
                "validExpertCount", rs.getInt("valid_expert_count"),
                "lowestScore", rs.getBigDecimal("lowest_score"), "highestScore", rs.getBigDecimal("highest_score"),
                "finalScore", rs.getBigDecimal("final_score"), "finalResult", rs.getString("final_result"),
                "status", rs.getString("status"), "createdBy", rs.getLong("created_by"),
                "createdAt", rs.getObject("created_at", LocalDateTime.class),
                "updatedAt", rs.getObject("updated_at", LocalDateTime.class),
                "completedAt", rs.getObject("completed_at", LocalDateTime.class));
    }

    private static String toReviewTaskNodeId(String nodeId) {
        if (nodeId == null) return null;
        return nodeId.replace("ExpertAssignTask", "ExpertReviewTask")
                .replace("ExpertSummaryTask", "ExpertReviewTask");
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
