package com.amatrix.sic.workflow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.contracts.ExpertProjectGrantCommand;

@Component("projectAccessPolicy")
public class WorkflowProjectAccessPolicy {
    private final ProjectAccessClient projects;
    private final WorkflowInternalTokenProvider tokens;
    private final JdbcTemplate jdbc;

    public WorkflowProjectAccessPolicy(ProjectAccessClient projects, WorkflowInternalTokenProvider tokens,
            JdbcTemplate jdbc) {
        this.projects = projects;
        this.tokens = tokens;
        this.jdbc = jdbc;
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
        Long projectId = jdbc.query("SELECT project_id FROM module_instance WHERE id=?",
                (rs, row) -> rs.getLong(1), moduleInstanceId).stream().findFirst().orElse(null);
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
        return jdbc.query("""
                SELECT m.project_id,m.module_code,m.current_round,b.created_by,n.node_id,a.expert_name_snapshot
                  FROM module_instance m
                  JOIN workflow_task t ON t.module_instance_id=m.id AND t.status='OPEN'
                    AND t.node_id=m.current_node_id AND t.candidate_role='EXPERT'
                  JOIN expert_review_batch b ON b.module_instance_id=m.id AND b.round_no=m.current_round
                  JOIN expert_review_assignment a ON a.batch_id=b.id AND a.expert_user_id=?
                  LEFT JOIN workflow_node_config n ON n.id=b.workflow_node_id
                 WHERE m.id=? AND m.finished=FALSE
                   AND a.valid=TRUE
                   AND (b.workflow_node_id IS NULL OR n.id IS NULL OR
                     REPLACE(REPLACE(n.node_id,'ExpertAssignTask','ExpertReviewTask'),
                       'ExpertSummaryTask','ExpertReviewTask')=t.node_id)
                 ORDER BY b.id DESC LIMIT 1
                """, (rs, row) -> new ExpertProjectGrantCommand(rs.getLong("project_id"), actor.userId(),
                        rs.getString("expert_name_snapshot"), rs.getString("module_code"),
                        rs.getInt("current_round"), rs.getString("node_id") == null ? null
                                : rs.getString("node_id").replace("ExpertAssignTask", "ExpertReviewTask")
                                        .replace("ExpertSummaryTask", "ExpertReviewTask"),
                        rs.getLong("created_by"), "workflow-service"), actor.userId(), moduleInstanceId)
                .stream().findFirst().orElse(null);
    }
}
