package com.amatrix.sic.workflow.policy;

import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.amatrix.sic.workflow.dao.WorkflowScopeMapper;
import com.amatrix.sic.workflow.entity.WorkflowTaskScope;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ProjectAccessBatchRequest;
import com.amatrix.sic.contracts.ProjectAccessResult;
import com.amatrix.sic.contracts.ProjectOperationTarget;

@Component("workflowOperationPolicy")
public class WorkflowOperationPolicy {
    private final WorkflowScopeMapper mapper;
    private final ProjectAccessClient projects;
    private final WorkflowInternalTokenProvider tokens;

    public WorkflowOperationPolicy(WorkflowScopeMapper mapper, ProjectAccessClient projects,
            WorkflowInternalTokenProvider tokens) {
        this.mapper = mapper;
        this.projects = projects;
        this.tokens = tokens;
    }

    public boolean canOperate(ActorContext actor, Long moduleInstanceId) {
        return exactCanOperate(actor, moduleInstanceId);
    }

    public boolean exactCanOperate(ActorContext actor, Long moduleInstanceId) {
        return actor != null && operableModuleIds(actor, List.of(moduleInstanceId)).contains(moduleInstanceId);
    }

    public record RuntimeAccess(boolean canRead, boolean canOperate) {}

    /** Shares a single Project lookup within a view request; writes still recheck under their lock. */
    public RuntimeAccess runtimeAccess(ActorContext actor, Long moduleId, Long projectId) {
        if (actor == null || actor.userId() == null || moduleId == null || projectId == null)
            return new RuntimeAccess(false, false);
        List<Long> ids = List.of(moduleId);
        Set<Long> expertModules = expertModules(actor, ids);
        List<WorkflowTaskScope> eligible = mapper.openTasks(ids).stream()
                .filter(task -> Objects.equals(projectId, task.projectId()))
                .filter(task -> locallyEligible(actor, task, expertModules)).toList();
        List<ProjectOperationTarget> targets = new ArrayList<>();
        targets.add(new ProjectOperationTarget("runtime-read", projectId, "VIEW_WORKFLOW", null, null, null));
        if (!actor.hasRole("SYSTEM_ADMIN")) {
            eligible.forEach(task -> targets.add(new ProjectOperationTarget(String.valueOf(task.taskId()), projectId,
                    operation(task.candidateRole()), task.moduleType(), task.round(), task.nodeId())));
        }
        var response = projects.decideBatch(tokens.authorization(), new ProjectAccessBatchRequest(actor, targets));
        if (!response.success() || response.data() == null) return new RuntimeAccess(false, false);
        Set<String> taskIds = eligible.stream().map(task -> String.valueOf(task.taskId()))
                .collect(java.util.stream.Collectors.toSet());
        boolean read = false;
        boolean operate = false;
        for (ProjectAccessResult decision : response.data()) {
            if (!Objects.equals(projectId, decision.projectId()) || !decision.allowed()) continue;
            if ("runtime-read".equals(decision.decisionId())) read = true;
            else if (taskIds.contains(decision.decisionId())) operate = true;
        }
        if (actor.hasRole("SYSTEM_ADMIN")) operate = read && !eligible.isEmpty();
        return new RuntimeAccess(read, operate);
    }

    public Set<Long> operableModuleIds(ActorContext actor, List<Long> moduleIds) {
        if (actor == null || actor.userId() == null || moduleIds == null || moduleIds.isEmpty()) return Set.of();
        List<Long> ids = moduleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Set.of();
        List<WorkflowTaskScope> tasks = mapper.openTasks(ids);
        Set<Long> expertModules = expertModules(actor, ids);
        List<WorkflowTaskScope> locallyEligible = tasks.stream().filter(task -> locallyEligible(actor, task, expertModules)).toList();
        if (locallyEligible.isEmpty()) return Set.of();
        if (actor.hasRole("SYSTEM_ADMIN"))
            return locallyEligible.stream().map(WorkflowTaskScope::moduleId).collect(java.util.stream.Collectors.toSet());
        List<ProjectOperationTarget> targets = locallyEligible.stream()
                .map(task -> new ProjectOperationTarget(String.valueOf(task.taskId()), task.projectId(),
                        operation(task.candidateRole()), task.moduleType(), task.round(), task.nodeId()))
                .toList();
        var response = projects.decideBatch(tokens.authorization(), new ProjectAccessBatchRequest(actor, targets));
        if (!response.success() || response.data() == null) return Set.of();
        Map<String, WorkflowTaskScope> byDecision = new HashMap<>();
        locallyEligible.forEach(task -> byDecision.put(String.valueOf(task.taskId()), task));
        Set<Long> result = new LinkedHashSet<>();
        for (ProjectAccessResult decision : response.data()) {
            WorkflowTaskScope task = byDecision.get(decision.decisionId());
            if (task != null && decision.allowed()) result.add(task.moduleId());
        }
        return result;
    }

    private boolean locallyEligible(ActorContext actor, WorkflowTaskScope task, Set<Long> expertModules) {
        if (actor.hasRole("SYSTEM_ADMIN")) return true;
        if (task.assigneeUserId() != null) return Objects.equals(task.assigneeUserId(), actor.userId());
        String role = task.candidateRole();
        if (role == null || !actor.hasRole(role)) return false;
        return !"EXPERT".equals(role) || expertModules.contains(task.moduleId());
    }

    private Set<Long> expertModules(ActorContext actor, List<Long> moduleIds) {
        if (!actor.hasRole("EXPERT")) return Set.of();
        return new HashSet<>(mapper.expertModules(actor.userId(), moduleIds));
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

}
