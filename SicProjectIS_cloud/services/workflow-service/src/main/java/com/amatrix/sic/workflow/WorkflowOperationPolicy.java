package com.amatrix.sic.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ProjectAccessBatchRequest;
import com.amatrix.sic.contracts.ProjectAccessResult;
import com.amatrix.sic.contracts.ProjectOperationTarget;

@Component("workflowOperationPolicy")
public class WorkflowOperationPolicy {
    private final JdbcTemplate jdbc;
    private final ProjectAccessClient projects;
    private final WorkflowInternalTokenProvider tokens;

    public WorkflowOperationPolicy(JdbcTemplate jdbc, ProjectAccessClient projects,
            WorkflowInternalTokenProvider tokens) {
        this.jdbc = jdbc;
        this.projects = projects;
        this.tokens = tokens;
    }

    public boolean canOperate(ActorContext actor, Long moduleInstanceId) {
        return exactCanOperate(actor, moduleInstanceId);
    }

    public boolean exactCanOperate(ActorContext actor, Long moduleInstanceId) {
        return actor != null && operableModuleIds(actor, List.of(moduleInstanceId)).contains(moduleInstanceId);
    }

    public Set<Long> operableModuleIds(ActorContext actor, List<Long> moduleIds) {
        if (actor == null || actor.userId() == null || moduleIds == null || moduleIds.isEmpty()) return Set.of();
        List<Long> ids = moduleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Task> tasks = jdbc.query("""
                SELECT t.id,t.module_instance_id,t.node_id,t.assignee_user_id,t.candidate_role,t.round_no,
                       m.project_id,m.module_code
                  FROM workflow_task t JOIN module_instance m ON m.id=t.module_instance_id
                 WHERE m.finished=FALSE AND t.status='OPEN' AND t.node_id=m.current_node_id
                   AND t.module_instance_id IN (%s)
                """.formatted(placeholders), (rs, row) -> new Task(rs.getLong("id"),
                        rs.getLong("module_instance_id"), rs.getLong("project_id"), rs.getString("module_code"),
                        rs.getString("node_id"), nullableLong(rs, "assignee_user_id"),
                        rs.getString("candidate_role"), rs.getInt("round_no")), ids.toArray());
        Set<Long> expertModules = expertModules(actor, ids);
        List<Task> locallyEligible = tasks.stream().filter(task -> locallyEligible(actor, task, expertModules)).toList();
        if (locallyEligible.isEmpty()) return Set.of();
        if (actor.hasRole("SYSTEM_ADMIN"))
            return locallyEligible.stream().map(Task::moduleId).collect(java.util.stream.Collectors.toSet());
        List<ProjectOperationTarget> targets = locallyEligible.stream()
                .map(task -> new ProjectOperationTarget(String.valueOf(task.taskId()), task.projectId(),
                        operation(task.candidateRole()), task.moduleType(), task.round(), task.nodeId()))
                .toList();
        var response = projects.decideBatch(tokens.authorization(), new ProjectAccessBatchRequest(actor, targets));
        if (!response.success() || response.data() == null) return Set.of();
        Map<String, Task> byDecision = new HashMap<>();
        locallyEligible.forEach(task -> byDecision.put(String.valueOf(task.taskId()), task));
        Set<Long> result = new LinkedHashSet<>();
        for (ProjectAccessResult decision : response.data()) {
            Task task = byDecision.get(decision.decisionId());
            if (task != null && decision.allowed()) result.add(task.moduleId());
        }
        return result;
    }

    private boolean locallyEligible(ActorContext actor, Task task, Set<Long> expertModules) {
        if (actor.hasRole("SYSTEM_ADMIN")) return true;
        if (task.assigneeUserId() != null) return Objects.equals(task.assigneeUserId(), actor.userId());
        String role = task.candidateRole();
        if (role == null || !actor.hasRole(role)) return false;
        return !"EXPERT".equals(role) || expertModules.contains(task.moduleId());
    }

    private Set<Long> expertModules(ActorContext actor, List<Long> moduleIds) {
        if (!actor.hasRole("EXPERT")) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(moduleIds.size(), "?"));
        List<Object> args = new ArrayList<>(); args.add(actor.userId()); args.addAll(moduleIds);
        return new HashSet<>(jdbc.query("""
                SELECT DISTINCT b.module_instance_id FROM expert_review_batch b
                  JOIN expert_review_assignment a ON a.batch_id=b.id
                 WHERE a.expert_user_id=? AND a.valid=TRUE AND a.review_status='ASSIGNED'
                   AND b.module_instance_id IN (%s)
                """.formatted(placeholders), (rs, row) -> rs.getLong(1), args.toArray()));
    }

    private static String operation(String candidateRole) {
        return switch (candidateRole == null ? "" : candidateRole) {
            case "PROJECT_LEADER" -> "OPERATE_PROJECT_LEADER_TASK";
            case "DEPT_ADMIN" -> "OPERATE_DEPARTMENT_TASK";
            case "FINANCE_ADMIN" -> "OPERATE_FINANCE_TASK";
            case "EXPERT" -> "OPERATE_EXPERT_TASK";
            case "SCIENCE_ADMIN" -> "OPERATE_SCIENCE_TASK";
            default -> "OPERATE_UNKNOWN_TASK";
        };
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }

    private record Task(Long taskId, Long moduleId, Long projectId, String moduleType, String nodeId,
            Long assigneeUserId, String candidateRole, int round) {}
}
