package com.amatrix.sic.workflow.policy;

import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;

import com.amatrix.sic.workflow.dao.WorkflowScopeMapper;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;

@Component("projectAccessPolicy")
public class WorkflowProjectAccessPolicy {
    private final ProjectAccessClient projects;
    private final WorkflowInternalTokenProvider tokens;
    private final WorkflowScopeMapper mapper;

    public WorkflowProjectAccessPolicy(ProjectAccessClient projects, WorkflowInternalTokenProvider tokens,
            WorkflowScopeMapper mapper) {
        this.projects = projects;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    public boolean canAccess(ActorContext actor, Long projectId) {
        return canAccess(actor, projectId, "ACCESS_WORKFLOW_DATA");
    }

    public boolean canAccess(ActorContext actor, Long projectId, String operation) {
        if (actor == null || projectId == null) return false;
        var response = projects.decide(tokens.authorization(), projectId, actor.userId(), actor.deptId(), operation,
                actor.roleCodes());
        return response.success() && response.data() != null && response.data().allowed();
    }

    public boolean canAccessModule(ActorContext actor, Long moduleInstanceId) {
        if (actor == null || moduleInstanceId == null) return false;
        Long projectId = mapper.projectId(moduleInstanceId);
        if (projectId == null) return false;
        if (canAccess(actor, projectId, "VIEW_WORKFLOW")) return true;
        ExpertProjectGrantCommand grant = assignedExpertGrant(actor, moduleInstanceId);
        if (grant == null) return false;
        var response = projects.syncExpertGrant(tokens.authorization(), projectId, grant);
        return response.success();
    }

    public boolean canAccessForm(ActorContext actor, Long projectId, Long moduleInstanceId) {
        if (moduleInstanceId != null) return canAccessModule(actor, moduleInstanceId);
        return projectId != null && canAccess(actor, projectId, "VIEW_WORKFLOW");
    }

    private ExpertProjectGrantCommand assignedExpertGrant(ActorContext actor, Long moduleInstanceId) {
        var grant = mapper.expertGrant(actor.userId(), moduleInstanceId);
        if (grant == null) return null;
        return new ExpertProjectGrantCommand(grant.projectId(), actor.userId(), grant.expertName(),
                grant.moduleType(), grant.round(), grant.nodeId() == null ? null
                        : grant.nodeId().replace("ExpertAssignTask", "ExpertReviewTask")
                                .replace("ExpertSummaryTask", "ExpertReviewTask"),
                grant.createdBy(), "workflow-service");
    }
}
