package com.amatrix.sic.project.service;

import com.amatrix.sic.project.service.directory.ProjectUserDirectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import com.amatrix.sic.project.dao.ProjectAuthorizationMapper;
import com.amatrix.sic.project.dao.ProjectAuditMapper;
import com.amatrix.sic.project.entity.ProjectRecord;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.EventEnvelope;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.contracts.UserSummary;
import com.amatrix.sic.messaging.OutboxService;
import com.amatrix.sic.security.CanAccessProject;

import tools.jackson.databind.ObjectMapper;

/**
 * 实现授权管理与角色/管理能力校验；维护项目版本、成员/授权关系、审计和 project.authorization；提供内部专家 grant 幂等同步/撤销。
 * 批量命令在同一事务内修改，统一返回变更后的详情；审计、项目版本和授权事件随本地事务提交。
 */
@Service
public class ProjectAuthorizationService {
    private static final String EXPERT_GRANT = "PROJECT_MODULE_EXPERT_ASSIGNMENT";
    private static final String FINANCE_GRANT = "PROJECT_FINANCE_HANDLER_ASSIGNMENT";
    private static final String PROXY_GRANT = "PROJECT_PROXY_RECORDER_ASSIGNMENT";
    private final ProjectAuthorizationMapper mapper;
    private final ProjectAuditMapper auditMapper;
    private final ProjectUserDirectory users;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public ProjectAuthorizationService(ProjectAuthorizationMapper mapper,ProjectAuditMapper auditMapper, ProjectUserDirectory users, OutboxService outbox,
            ObjectMapper objectMapper) {
        this.mapper = mapper;this.auditMapper = auditMapper; this.users = users; this.outbox = outbox; this.objectMapper = objectMapper;
    }

    /**
     * 先读取项目、成员和各类授权，再装配用户信息与操作能力；不在结果映射期间继续取得数据库连接。
     */
    @CanAccessProject
    public Map<String, Object> detail(Long projectId, ActorContext actor) {
        ProjectRecord project = project(projectId);
        List<Map<String, Object>> members = members(projectId);
        List<Map<String, Object>> experts = grants(projectId, EXPERT_GRANT);
        List<Map<String, Object>> finance = grants(projectId, FINANCE_GRANT);
        List<Map<String, Object>> proxies = grants(projectId, PROXY_GRANT);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.add(project.ownerUserId());
        members.forEach(row -> ids.add((Long) row.get("userId")));
        for (var rows : List.of(experts, finance, proxies)) {
            rows.forEach(row -> { ids.add((Long) row.get("granteeId")); ids.add((Long) row.get("grantedById")); });
        }
        // 所有结果集已结束映射，再查询用户或 IAM，避免映射过程中嵌套获取连接。
        Map<Long, UserSummary> summaries = users.requireAll(ids);
        members.forEach(row -> row.put("user", userMap(summaries.get(row.remove("userId")))));
        for (var rows : List.of(experts, finance, proxies)) {
            rows.forEach(row -> {
                row.put("grantee", userMap(summaries.get(row.remove("granteeId"))));
                row.put("grantedBy", userMap(summaries.get(row.remove("grantedById"))));
            });
        }
        return linked("project", projectSummary(project), "leader", userMap(summaries.get(project.ownerUserId())),
                "members", members, "expertGrants", experts,
                "financeGrants", finance, "proxyGrants", proxies,
                "users", users.allEnabled().stream().map(this::userMap).toList(),
                "capabilities", capabilities(project, actor));
    }

    // 更新项目负责人，事务内维护审计、项目版本和事件，最后只装配一次响应详情。
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> changeLeader(Long projectId, ActorContext actor, Long userId, String reason) {
        ProjectRecord project = project(projectId);
        requireCapability(project, actor, "LEADER");
        UserSummary selected = users.require(userId);
        if (!selected.enabled()) throw bad("Selected user is disabled");
        UserSummary previous = users.require(project.ownerUserId());
        mapper.changeLeader(projectId,userId,selected.realName());
        mapper.demoteMember(projectId,project.ownerUserId());
        upsertMemberRow(projectId, selected, "LEADER", "Project owner");
        audit(project, actor, userId, "CHANGE_LEADER", "PROJECT_LEADER_BINDING", reason,
                write(userMap(previous)), write(userMap(selected)));
        Long projectVersion = mapper.version(projectId);
        emit(projectId, actor.userId(), "PROJECT_LEADER_CHANGED", linked("projectId", projectId,
                "previousUserId", project.ownerUserId(), "userId", userId, "ownerUserId", userId,
                "ownerName", selected.realName(), "deptId", project.deptId(), "projectVersion", projectVersion));
        return detail(projectId, actor);
    }

    // 更新项目成员，支持新增或修改责任分工；事务内维护审计、项目版本和事件，最后只装配一次响应详情。
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> upsertMember(Long projectId, ActorContext actor, Long userId, String responsibility) {
        ProjectRecord project = project(projectId); requireCapability(project, actor, "MEMBER");
        UserSummary selected = users.require(userId);
        writeMember(project, actor, selected, responsibility);
        return detail(projectId, actor);
    }

    // 在同一事务中写入成员信息，维护审计、项目版本和事件，最后只装配一次响应详情；支持新增或修改责任分工。
    private void writeMember(ProjectRecord project, ActorContext actor, UserSummary selected, String responsibility) {
        Long projectId = project.id();
        Long userId = selected.userId();
        upsertMemberRow(projectId, selected, Objects.equals(userId, project.ownerUserId()) ? "LEADER" : "MEMBER",
                normalize(responsibility));
        touch(projectId); audit(project, actor, userId, "UPSERT_MEMBER", "PROJECT_MEMBER_BINDING",
                responsibility, null, write(userMap(selected)));
        emit(projectId, actor.userId(), "PROJECT_MEMBERS_CHANGED", linked("projectId", projectId, "userId", userId));
    }

    /**
     * 将多个成员变更作为一个本地事务处理，命令完成后统一装配详情和变更摘要，保持同步批量操作的整体提交语义。
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> upsertMembers(Long projectId, ActorContext actor, List<Long> userIds,
            String responsibility) {
        List<Long> ids = distinctIds(userIds);
        ProjectRecord project = project(projectId); requireCapability(project, actor, "MEMBER");
        Map<Long, UserSummary> selected = users.requireAll(ids);
        for (Long id : ids) writeMember(project, actor, selected.get(id), responsibility);
        return mutation(detail(projectId, actor), ids.stream().map(String::valueOf).toList(), List.of(),
                ids.size(), "Project members updated");
    }

    // 删除项目成员，事务内维护审计、项目版本和事件，最后只装配一次响应详情；不允许删除项目负责人。
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> removeMember(Long projectId, ActorContext actor, Long userId) {
        ProjectRecord project = project(projectId); requireCapability(project, actor, "MEMBER");
        if (Objects.equals(project.ownerUserId(), userId)) throw bad("Project leader cannot be removed");
        mapper.deleteMember(projectId,userId);
        touch(projectId); audit(project, actor, userId, "REMOVE_MEMBER", "PROJECT_MEMBER_BINDING", null, null, null);
        emit(projectId, actor.userId(), "PROJECT_MEMBERS_CHANGED", linked("projectId", projectId, "userId", userId));
        return detail(projectId, actor);
    }

    // 分配项目授权，事务内维护审计、项目版本和事件，最后只装配一次响应详情。
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> assignGrant(Long projectId, ActorContext actor, String grantRoleCode,
            Long userId, String moduleType, Integer roundNo, String taskNodeId, String reason) {
        ProjectRecord project = project(projectId); requireCapability(project, actor, capabilityFor(grantRoleCode));
        UserSummary selected = users.require(userId);
        validateSelectedRole(selected, grantRoleCode);
        writeGrant(project, actor, grantRoleCode, selected, moduleType, roundNo, taskNodeId, reason);
        return detail(projectId, actor);
    }

    // 在同一事务中写入项目授权，维护审计、项目版本和事件，最后只装配一次响应详情；支持新增或修改授权。
    private void writeGrant(ProjectRecord project, ActorContext actor, String grantRoleCode,
            UserSummary selected, String moduleType, Integer roundNo, String taskNodeId, String reason) {
        Long projectId = project.id();
        Long userId = selected.userId();
        var command=grantValues(projectId,actor,grantRoleCode,selected,"PROJECT",moduleType,roundNo,taskNodeId,reason);
        if(mapper.duplicateGrant(command)==0) {
            mapper.insertGrant(command);
            touch(projectId); audit(project, actor, userId, "ASSIGN_GRANT", grantRoleCode, reason, null,
                    String.valueOf(command.get("id")));
            emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED", linked("projectId", projectId,
                    "grantRoleCode", grantRoleCode, "userId", userId));
        }
    }

    /**
     * 批量校验并写入同类项目授权，事务内维护审计、项目版本与事件，最后只装配一次响应详情。
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> assignGrants(Long projectId, ActorContext actor, String grantRoleCode,
            List<Long> userIds, String moduleType, Integer roundNo, String taskNodeId, String reason) {
        List<Long> ids = distinctIds(userIds);
        ProjectRecord project = project(projectId); requireCapability(project, actor, capabilityFor(grantRoleCode));
        Map<Long, UserSummary> selected = users.requireAll(ids);
        ids.forEach(id -> validateSelectedRole(selected.get(id), grantRoleCode));
        for (Long id : ids) writeGrant(project, actor, grantRoleCode, selected.get(id), moduleType, roundNo, taskNodeId, reason);
        return mutation(detail(projectId, actor), ids.stream().map(String::valueOf).toList(), List.of(),
                ids.size(), "Project grants assigned");
    }

    /**
     * 根据 Workflow 指派范围同步专家临时项目授权；对应专家、模块、轮次和节点的已有授权可幂等复用。
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> syncExpertGrant(Long projectId, ExpertProjectGrantCommand command) {
        if (command == null || !Objects.equals(projectId, command.projectId()) || command.expertUserId() == null) {
            throw bad("Invalid expert project grant command");
        }
        ProjectRecord project = project(projectId);
        UserSummary expert = users.require(command.expertUserId());
        validateSelectedRole(expert, EXPERT_GRANT);
        String moduleType = normalize(command.moduleType());
        String taskNodeId = normalize(command.taskNodeId());
        ActorContext actor=internalActor(command);
        var grant=grantValues(projectId,actor,EXPERT_GRANT,expert,"MODULE",moduleType,command.roundNo(),taskNodeId,"Workflow expert assignment");
        if(mapper.duplicateGrant(grant)>0)return linked("changed",false,"status","ALREADY_ACTIVE");
        mapper.insertGrant(grant);
        touch(projectId);
        audit(project, actor, command.expertUserId(), "ASSIGN_GRANT", EXPERT_GRANT,
                "Workflow expert assignment", null, taskNodeId);
        emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED",
                linked("projectId", projectId, "grantRoleCode", EXPERT_GRANT, "userId", command.expertUserId()));
        return linked("changed", true, "status", "ACTIVE");
    }

    // 撤销专家临时项目授权，事务内维护审计、项目版本和事件，最后只装配一次响应详情；对应专家、模块、轮次和节点的授权可幂等撤销。
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> revokeExpertGrant(Long projectId, ExpertProjectGrantCommand command) {
        if (command == null || !Objects.equals(projectId, command.projectId()) || command.expertUserId() == null) {
            throw bad("Invalid expert project grant command");
        }
        ProjectRecord project = project(projectId);
        int changed=mapper.revokeExpert(linked("projectId",projectId,"userId",command.expertUserId(),"role",EXPERT_GRANT,
                "moduleType",normalize(command.moduleType()),"roundNo",command.roundNo(),"node",normalize(command.taskNodeId())));
        if (changed > 0) {
            ActorContext actor = internalActor(command);
            touch(projectId);
            audit(project, actor, command.expertUserId(), "REVOKE_GRANT", EXPERT_GRANT,
                    "Workflow expert assignment removed", command.taskNodeId(), null);
            emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED",
                    linked("projectId", projectId, "grantRoleCode", EXPERT_GRANT,
                            "userId", command.expertUserId()));
        }
        return linked("changed", changed > 0, "status", changed > 0 ? "REVOKED" : "NOT_ACTIVE");
    }

    // 撤销项目授权，事务内维护审计、项目版本和事件，最后只装配一次响应详情；不是逐条独立提交的后台任务。
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> revoke(Long projectId, ActorContext actor, Long grantId, String reason) {
        ProjectRecord project = project(projectId);
        revokeCommand(project, actor, grantId, reason);
        return detail(projectId, actor);
    }

    // 撤销项目授权的内部命令，用于在事务中处理具体的撤销逻辑。
    private void revokeCommand(ProjectRecord project, ActorContext actor, Long grantId, String reason) {
        Long projectId = project.id();
        String role=mapper.grantRole(grantId,projectId);
        if(role==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Project grant not found");
        requireCapability(project, actor, capabilityFor(role));
        mapper.revokeGrant(grantId);
        touch(projectId); audit(project, actor, null, "REVOKE_GRANT", role, reason, String.valueOf(grantId), null);
        emit(projectId, actor.userId(), "PROJECT_GRANT_CHANGED", linked("projectId", projectId, "grantId", grantId));
    }

    /**
     * 在同一事务中撤销选中的授权并返回统一变更结果；不是逐条独立提交的后台任务。
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CanAccessProject
    public Map<String, Object> revokeBatch(Long projectId, ActorContext actor, List<Long> grantIds, String reason) {
        List<Long> ids = distinctIds(grantIds);
        ProjectRecord project = project(projectId);
        for (Long id : ids) revokeCommand(project, actor, id, reason);
        return mutation(detail(projectId, actor), List.of(), ids.stream().map(String::valueOf).toList(),
                ids.size(), "Project grants revoked");
    }

    // 查询当前用户可管理的项目列表，返回项目概要和各类授权数量；需要 PERM_PROJECT_GRANT_VIEW 权限。
    @PreAuthorize("hasAuthority('PERM_PROJECT_GRANT_VIEW')")
    public Map<String, Object> manageableProjects(ActorContext actor, Long deptId) {
        List<Map<String,Object>> projects=mapper.manageable(actor.hasRole("SYSTEM_ADMIN") || actor.hasRole("SCIENCE_ADMIN"),
                actor.hasRole("DEPT_ADMIN"),deptId).stream().map(this::projectSummary).toList();
        List<Map<String,Object>> counts=mapper.grantCounts();
        return linked("projects", projects, "grantTypeCounts", counts);
    }

    // 其他辅助方法，用于装配响应、计算差异、查询成员和授权、写入成员行、生成授权值、计算能力、校验权限、获取项目记录和用户信息。
    private Map<String, Object> mutation(Map<String, Object> detail, List<String> added, List<String> removed,
            int affected, String message) {
        return linked("detail", detail, "diff", linked("added", added, "removed", removed),
                "affectedCount", affected, "summaryMessage", message);
    }

    private List<Map<String,Object>> members(Long projectId) { return mapper.members(projectId); }
    private List<Map<String,Object>> grants(Long projectId,String role) { return mapper.grants(projectId,role); }
    private void upsertMemberRow(Long projectId,UserSummary user,String memberRole,String responsibility) {
        mapper.upsertMember(linked("projectId",projectId,"userId",user.userId(),"role",memberRole,
                "responsibility",responsibility,"name",user.realName()));
    }
    private Map<String,Object> grantValues(Long projectId,ActorContext actor,String role,UserSummary user,
            String scope,String moduleType,Integer round,String node,String reason) {
        return linked("projectId",projectId,"userId",user.userId(),"name",user.realName(),"role",role,"grantScope",scope,
                "moduleType",normalize(moduleType),"roundNo",round,"node",normalize(node),"reason",normalize(reason),
                "operatorId",actor.userId(),"operatorName",actor.username());
    }

    private Map<String, Object> capabilities(ProjectRecord project, ActorContext actor) {
        boolean global = actor.hasRole("SYSTEM_ADMIN") || actor.hasRole("SCIENCE_ADMIN");
        boolean owner = Objects.equals(project.ownerUserId(), actor.userId());
        boolean department = actor.hasRole("DEPT_ADMIN") && Objects.equals(project.deptId(), actorDept(actor));
        return linked("canManageLeader", global, "canManageMembers", global || owner || department,
                "canManageExperts", global || owner || department, "canManageFinance", global,
                "canManageProxy", global || owner);
    }

    private void requireCapability(ProjectRecord project, ActorContext actor, String operation) {
        Map<String, Object> c = capabilities(project, actor);
        String key = switch (operation) {
            case "LEADER" -> "canManageLeader"; case "MEMBER" -> "canManageMembers";
            case "EXPERT" -> "canManageExperts"; case "FINANCE" -> "canManageFinance";
            default -> "canManageProxy";
        };
        if (!Boolean.TRUE.equals(c.get(key))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not have permission to manage this project authorization");
    }

    private static String capabilityFor(String grantRoleCode) {
        if (EXPERT_GRANT.equals(grantRoleCode)) return "EXPERT";
        if (FINANCE_GRANT.equals(grantRoleCode)) return "FINANCE";
        return "PROXY";
    }

    private static void validateSelectedRole(UserSummary user, String grantRoleCode) {
        if (EXPERT_GRANT.equals(grantRoleCode) && !user.roleCodes().contains("EXPERT")) throw bad("Selected user does not have global EXPERT role");
        if (FINANCE_GRANT.equals(grantRoleCode) && !user.roleCodes().contains("FINANCE_ADMIN")) throw bad("Selected user does not have global FINANCE_ADMIN role");
    }

    private ProjectRecord project(Long id) {
        boolean writeTransaction=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                &&!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var row=writeTransaction?mapper.lockProject(id):mapper.project(id);
        if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Project not found");
        return row;
    }

    private Map<String, Object> projectSummary(ProjectRecord p) {
        return linked("projectId", p.id(), "projectCode", p.code(), "projectName", p.name(), "deptId", p.deptId(),
                "deptName", p.deptName(), "leaderUserId", p.ownerUserId(), "leaderRealName", p.ownerName(),
                "projectType", p.type(), "projectLevel", p.level(), "lifecycleStage", p.stage());
    }

    private Map<String, Object> userMap(UserSummary user) {
        return linked("userId", user.userId(), "username", user.username(), "realName", user.realName(),
                "deptId", user.deptId(), "deptName", user.deptName(), "roleCodes", user.roleCodes());
    }

    private void touch(Long projectId) { mapper.touch(projectId); }

    private void audit(ProjectRecord project, ActorContext actor, Long targetId, String action, String roleCode,
            String remark, String before, String after) {
        UserSummary target = targetId == null ? null : users.require(targetId);
        auditMapper.insert(linked("id",UUID.randomUUID().toString(),"action",action,"projectId",project.id(),"projectName",project.name(),
                "operatorId",actor.userId(),"operatorName",actor.username(),"targetId",targetId,"targetName",target==null?null:target.realName(),
                "role",roleCode,"before",before,"after",after,"remark",normalize(remark)));
    }

    private void emit(Long projectId, Long actorId, String type, Map<String, Object> payload) {
        outbox.enqueue("project.authorization", EventEnvelope.create(type, "PROJECT", projectId.toString(),
                null, null, actorId, payload));
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static Long actorDept(ActorContext actor) { return actor.deptId(); }

    private static ActorContext internalActor(ExpertProjectGrantCommand command) {
        Long id = command.grantedByUserId() == null ? 0L : command.grantedByUserId();
        String name = normalize(command.grantedByName());
        return new ActorContext(id, name == null ? "workflow-service" : name, null, null,
                List.of(), List.of(), "internal-workflow-grant", 0);
    }

    private static List<Long> distinctIds(List<Long> values) {
        if (values == null || values.isEmpty()) throw bad("At least one user or grant is required");
        return new ArrayList<>(new LinkedHashSet<>(values.stream().filter(Objects::nonNull).toList()));
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put(String.valueOf(pairs[i]), pairs[i+1]); return result;
    }

}
