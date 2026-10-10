package com.amatrix.sic.project.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.amatrix.sic.project.dao.ProjectScopeMapper;
import com.amatrix.sic.project.entity.ProjectScopeRow;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.AccessDecision;
import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ProjectAccessResult;
import com.amatrix.sic.contracts.ProjectOperationTarget;

@Component("projectAccessPolicy")
public class ProjectAccessPolicy {
    private static final String EXPERT_GRANT = "PROJECT_MODULE_EXPERT_ASSIGNMENT";
    private static final String FINANCE_GRANT = "PROJECT_FINANCE_HANDLER_ASSIGNMENT";
    private static final String PROXY_GRANT = "PROJECT_PROXY_RECORDER_ASSIGNMENT";
    private final ProjectScopeMapper mapper;

    public ProjectAccessPolicy(ProjectScopeMapper mapper) { this.mapper = mapper; }

    public boolean canAccess(ActorContext actor, Long projectId) { return canAccess(actor, projectId, "READ"); }

    public boolean canAccess(ActorContext actor, Long projectId, String operation) {
        if (actor == null || projectId == null) return false;
        return decide(actor.userId(), actor.deptId(), actor.roleCodes(), projectId, operation).allowed();
    }

    public AccessDecision decide(Long userId, Long deptId, List<String> roles, Long projectId, String operation) {
        ActorContext actor = new ActorContext(userId, null, deptId, null, roles, List.of(), null, 0);
        ProjectOperationTarget target = new ProjectOperationTarget("single", projectId, operation, null, null, null);
        ProjectAccessResult result = decideBatch(actor, List.of(target)).getFirst();
        return new AccessDecision(result.allowed(), result.projectVersion(), result.reasonCode());
    }

    public List<ProjectAccessResult> decideBatch(ActorContext actor, List<ProjectOperationTarget> requested) {
        List<ProjectOperationTarget> targets = requested == null ? List.of()
                : requested.stream().filter(Objects::nonNull).toList();
        if (targets.isEmpty()) return List.of();
        Set<Long> ids = new LinkedHashSet<>();
        targets.stream().map(ProjectOperationTarget::projectId).filter(Objects::nonNull).forEach(ids::add);
        Map<Long, ProjectScopeRow> projects = loadProjects(ids);
        Map<Long, Boolean> memberships = loadMemberships(actor == null ? null : actor.userId(), ids);
        Map<Long, List<Grant>> grants = loadGrants(actor == null ? null : actor.userId(), ids);
        List<ProjectAccessResult> results = new ArrayList<>(targets.size());
        for (ProjectOperationTarget target : targets) {
            ProjectScopeRow project = projects.get(target.projectId());
            Decision decision = evaluate(actor, project, target, memberships.getOrDefault(target.projectId(), false),
                    grants.getOrDefault(target.projectId(), List.of()));
            results.add(new ProjectAccessResult(target.decisionId(), target.projectId(), decision.allowed(),
                    project == null ? 0 : project.version(), decision.reason(),
                    project == null ? null : project.ownerUserId(), project == null ? null : project.ownerName(),
                    project == null ? null : project.deptId()));
        }
        return results;
    }

    private Decision evaluate(ActorContext actor, ProjectScopeRow project, ProjectOperationTarget target,
            boolean member, List<Grant> grants) {
        if (actor == null || actor.userId() == null || target.projectId() == null)
            return Decision.deny("INVALID_ACTOR_OR_PROJECT");
        if (project == null) return Decision.deny("PROJECT_NOT_ACTIVE");
        Set<String> roles = new HashSet<>(actor.roleCodes() == null ? List.of() : actor.roleCodes());
        String operation = normalized(target.operation(), "READ");
        if (roles.contains("SYSTEM_ADMIN")) return Decision.allow();
        if (operation.startsWith("OPERATE_")) {
            return switch (operation) {
                case "OPERATE_PROJECT_LEADER_TASK" -> Objects.equals(project.ownerUserId(), actor.userId())
                        ? Decision.allow() : Decision.deny("PROJECT_LEADER_MISMATCH");
                case "OPERATE_DEPARTMENT_TASK" -> roles.contains("DEPT_ADMIN") && actor.deptId() != null
                        && Objects.equals(project.deptId(), actor.deptId())
                                ? Decision.allow() : Decision.deny("PROJECT_DEPARTMENT_MISMATCH");
                case "OPERATE_FINANCE_TASK" -> roles.contains("FINANCE_ADMIN") && matches(grants, FINANCE_GRANT, target)
                        ? Decision.allow() : Decision.deny("PROJECT_FINANCE_GRANT_REQUIRED");
                case "OPERATE_EXPERT_TASK" -> roles.contains("EXPERT") && matches(grants, EXPERT_GRANT, target)
                        ? Decision.allow() : Decision.deny("PROJECT_EXPERT_GRANT_REQUIRED");
                case "OPERATE_SCIENCE_TASK" -> roles.contains("SCIENCE_ADMIN")
                        ? Decision.allow() : Decision.deny("SCIENCE_ROLE_REQUIRED");
                default -> Decision.deny("UNKNOWN_WORKFLOW_OPERATION");
            };
        }
        if (roles.contains("SCIENCE_ADMIN")) return Decision.allow();
        if (operation.startsWith("START_")) return Decision.deny("WORKFLOW_START_REQUIRES_SCIENCE_ADMIN");
        boolean owner = Objects.equals(project.ownerUserId(), actor.userId());
        boolean department = roles.contains("DEPT_ADMIN") && actor.deptId() != null
                && Objects.equals(project.deptId(), actor.deptId());
        boolean proxy = matches(grants, PROXY_GRANT, target);
        boolean finance = roles.contains("FINANCE_ADMIN") && matches(grants, FINANCE_GRANT, target);
        boolean expert = roles.contains("EXPERT") && matches(grants, EXPERT_GRANT, target);
        boolean allowed = Set.of("MATERIAL_WRITE", "MATERIAL_DELETE", "WRITE_WORKFLOW_DATA").contains(operation)
                ? owner || member || department || proxy || finance
                : owner || member || department || proxy || finance || expert;
        return allowed ? Decision.allow() : Decision.deny("PROJECT_OPERATION_DENIED");
    }

    private static boolean matches(List<Grant> grants, String role, ProjectOperationTarget target) {
        return grants.stream().anyMatch(grant -> role.equals(grant.role())
                && optionalMatches(grant.moduleType(), target.moduleType())
                && optionalMatches(grant.roundNo(), target.roundNo())
                && optionalMatches(grant.taskNodeId(), target.taskNodeId()));
    }

    private static boolean optionalMatches(Object scoped, Object requested) {
        return requested == null || scoped == null || Objects.equals(scoped, requested);
    }

    private Map<Long, ProjectScopeRow> loadProjects(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long,ProjectScopeRow> result=new HashMap<>();
        mapper.projects(ids).forEach(row -> result.put(row.id(),row));
        return result;
    }

    private Map<Long,Boolean> loadMemberships(Long userId,Set<Long> ids) {
        if(userId==null || ids.isEmpty())return Map.of();
        Map<Long,Boolean> result=new HashMap<>();
        mapper.memberships(userId,ids).forEach(id -> result.put(id,true));
        return result;
    }

    private Map<Long,List<Grant>> loadGrants(Long userId,Set<Long> ids) {
        if(userId==null || ids.isEmpty())return Map.of();
        Map<Long,List<Grant>> result=new LinkedHashMap<>();
        mapper.grants(userId,ids).forEach(row -> result.computeIfAbsent(row.projectId(),ignored -> new ArrayList<>())
                .add(new Grant(row.role(),row.moduleType(),row.roundNo(),row.taskNodeId())));
        return result;
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
    private record Grant(String role, String moduleType, Integer roundNo, String taskNodeId) {}
    private record Decision(boolean allowed, String reason) {
        static Decision allow() { return new Decision(true, "ALLOWED"); }
        static Decision deny(String reason) { return new Decision(false, reason); }
    }
}
