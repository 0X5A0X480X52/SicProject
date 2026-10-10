package com.amatrix.sicprojectis_backend.security;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.amatrix.sicprojectis_backend.project.ProjectGrantRoleCodes;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleRuntimeContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;

/** Workflow-task authorization owned by the workflow runtime domain. */
@Service("workflowOperationPolicy")
public class WorkflowOperationPolicy {
    private static final String PROJECT_LEADER = "PROJECT_LEADER";
    private static final String EXPERT = "EXPERT";

    private final GlobalPermissionPolicy globalPermissionPolicy;
    private final ProjectAccessPolicy projectAccessPolicy;
    private final ProjectDao projectDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final ModuleRuntimeContextViewDao moduleRuntimeContextViewDao;
    private final TaskInstanceDao taskInstanceDao;

    public WorkflowOperationPolicy(
            GlobalPermissionPolicy globalPermissionPolicy,
            ProjectAccessPolicy projectAccessPolicy,
            ProjectDao projectDao,
            ProjectRoleGrantDao projectRoleGrantDao,
            ModuleRuntimeContextViewDao moduleRuntimeContextViewDao,
            TaskInstanceDao taskInstanceDao) {
        this.globalPermissionPolicy = globalPermissionPolicy;
        this.projectAccessPolicy = projectAccessPolicy;
        this.projectDao = projectDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.moduleRuntimeContextViewDao = moduleRuntimeContextViewDao;
        this.taskInstanceDao = taskInstanceDao;
    }

    public boolean canOperate(Long userId, Long moduleInstanceId) {
        ModuleRuntimeContextView context = moduleRuntimeContextViewDao.selectByModuleInstanceId(moduleInstanceId);
        if (context == null || !projectAccessPolicy.canAccess(userId, context.getProjectId())) {
            return false;
        }
        List<String> roles = globalPermissionPolicy.roles(userId);
        for (TaskInstance task : taskInstanceDao.selectOpenByModuleInstanceId(moduleInstanceId)) {
            if (Objects.equals(task.getAssigneeUserId(), userId)
                    || matchesCandidateRole(userId, context, task.getCandidateRoleCode(), roles)) {
                return true;
            }
        }
        return matchesCandidateRole(userId, context, context.getCurrentCandidateRoleCode(), roles);
    }

    private boolean matchesCandidateRole(
            Long userId,
            ModuleRuntimeContextView context,
            String candidateRoleCode,
            List<String> roles) {
        if (candidateRoleCode == null) {
            return false;
        }
        if (PROJECT_LEADER.equals(candidateRoleCode)) {
            Project project = context.getProjectId() == null ? null : projectDao.selectById(context.getProjectId());
            return project != null
                    && (Objects.equals(project.getLeaderUserId(), userId)
                            || projectAccessPolicy.hasGrant(userId, context.getProjectId(),
                                    ProjectGrantRoleCodes.PROJECT_LEADER_BINDING));
        }
        if (EXPERT.equals(candidateRoleCode)) {
            return roles.contains(EXPERT)
                    && !projectRoleGrantDao.selectMatchingActiveGrant(
                            context.getProjectId(),
                            context.getModuleType(),
                            ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT,
                            userId,
                            context.getCurrentRoundNo(),
                            context.getCurrentNodeId()).isEmpty();
        }
        return roles.contains(candidateRoleCode);
    }
}
