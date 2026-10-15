package com.amatrix.sic.workflow.policy;

import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;

import com.amatrix.sic.workflow.dao.WorkflowScopeMapper;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;
import com.amatrix.sic.contracts.ProjectAccessBatchRequest;
import com.amatrix.sic.contracts.ProjectOperationTarget;
import java.util.List;
import java.util.Objects;

/**
 * 以 projectAccessPolicy Bean 名实现项目/模块/节点表单读取；必要时根据当前专家分配同步缺失的 Project 授权。
 * 定义读取与项目运行实例读取使用不同边界，不能把可查看某项目的资格解释为定义管理权限。
 */
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

    /** 项目读取资格只放行该项目实际使用的定义版本，不扩展为任意版本的定义管理权限。 */
    public boolean canReadDefinition(ActorContext actor, Long definitionId) {
        if(actor==null || actor.userId()==null || definitionId==null)return false;
        long after=0;
        while(true) {
            List<Long> projectIds=mapper.definitionProjects(definitionId,after);
            if(projectIds.isEmpty())return false;
            var targets=projectIds.stream().map(id -> new ProjectOperationTarget("definition-"+id,id,
                    "VIEW_WORKFLOW",null,null,null)).toList();
            var response=projects.decideBatch(tokens.authorization(),new ProjectAccessBatchRequest(actor,targets));
            if(!response.success() || response.data()==null)return false;
            if(response.data().stream().anyMatch(decision -> decision.allowed()
                    && projectIds.contains(decision.projectId())
                    && Objects.equals("definition-"+decision.projectId(),decision.decisionId())))return true;
            if(projectIds.size()<100)return false;
            after=projectIds.getLast();
        }
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
