package com.amatrix.sicprojectis_backend.security;

import java.util.List;
import java.util.Objects;

import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectMemberDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.ProjectGrantRoleCodes;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleRuntimeContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;
import org.springframework.stereotype.Service;

@Service("permissionService")
public class PermissionService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";
    private static final String DEPT_ADMIN = "DEPT_ADMIN";
    private static final String PROJECT_LEADER = "PROJECT_LEADER";
    private static final String EXPERT = "EXPERT";

    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionDao permissionDao;
    private final ProjectDao projectDao;
    private final ProjectMemberDao projectMemberDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final ModuleRuntimeContextViewDao moduleRuntimeContextViewDao;
    private final TaskInstanceDao taskInstanceDao;

    public PermissionService(UserRoleDetailViewDao userRoleDetailViewDao, PermissionDao permissionDao,
            ProjectDao projectDao, ProjectMemberDao projectMemberDao, ProjectRoleGrantDao projectRoleGrantDao,
            ModuleRuntimeContextViewDao moduleRuntimeContextViewDao, TaskInstanceDao taskInstanceDao) {
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.permissionDao = permissionDao;
        this.projectDao = projectDao;
        this.projectMemberDao = projectMemberDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.moduleRuntimeContextViewDao = moduleRuntimeContextViewDao;
        this.taskInstanceDao = taskInstanceDao;
    }

    public boolean hasRole(Long userId, String roleCode) {
        return roles(userId).contains(roleCode);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        return permissionDao.selectPermissionCodesByUserId(userId).contains(permissionCode);
    }

    public boolean canAccessProject(Long userId, Long projectId) {
        Project project = projectDao.selectById(projectId);
        if (project == null) {
            return false;
        }
        List<String> roles = roles(userId);
        if (roles.contains(SYSTEM_ADMIN) || roles.contains(SCIENCE_ADMIN)) {
            return true;
        }
        UserRoleDetailView user = firstRoleDetail(userId);
        if (roles.contains(DEPT_ADMIN) && user != null && Objects.equals(user.getDeptId(), project.getDeptId())) {
            return true;
        }
        if (Objects.equals(project.getLeaderUserId(), userId) || projectMemberDao.countByProjectIdAndUserId(projectId, userId) > 0) {
            return true;
        }
        if (hasProjectGrant(userId, projectId, ProjectGrantRoleCodes.PROJECT_LEADER_BINDING)
                || hasProjectGrant(userId, projectId, ProjectGrantRoleCodes.PROJECT_MEMBER_BINDING)
                || hasProjectGrant(userId, projectId, ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT)
                || hasProjectGrant(userId, projectId, ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT)) {
            return true;
        }
        if (roles.contains(EXPERT) && hasProjectGrant(userId, projectId, ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT)) {
            return true;
        }
        return roles.contains(EXPERT) && hasOpenTaskInProject(userId, projectId);
    }

    public boolean canOperateModuleNode(Long userId, Long moduleInstanceId) {
        ModuleRuntimeContextView context = moduleRuntimeContextViewDao.selectByModuleInstanceId(moduleInstanceId);
        if (context == null || !canAccessProject(userId, context.getProjectId())) {
            return false;
        }
        List<String> roles = roles(userId);
        for (TaskInstance task : taskInstanceDao.selectOpenByModuleInstanceId(moduleInstanceId)) {
            if (Objects.equals(task.getAssigneeUserId(), userId) || matchesCandidateRole(userId, context, task.getCandidateRoleCode(), roles)) {
                return true;
            }
        }
        return matchesCandidateRole(userId, context, context.getCurrentCandidateRoleCode(), roles);
    }

    private boolean hasOpenTaskInProject(Long userId, Long projectId) {
        return moduleRuntimeContextViewDao.selectAll().stream()
                .filter(context -> Objects.equals(context.getProjectId(), projectId))
                .flatMap(context -> taskInstanceDao.selectOpenByModuleInstanceId(context.getModuleInstanceId()).stream())
                .anyMatch(task -> Objects.equals(task.getAssigneeUserId(), userId));
    }

    private boolean hasProjectGrant(Long userId, Long projectId, String grantRoleCode) {
        return projectRoleGrantDao.selectActiveForUserAndProject(userId, projectId).stream()
                .map(ProjectRoleGrant::getGrantRoleCode)
                .anyMatch(grantRoleCode::equals);
    }

    private boolean matchesCandidateRole(Long userId, ModuleRuntimeContextView context, String candidateRoleCode, List<String> roles) {
        if (candidateRoleCode == null) {
            return false;
        }
        if (PROJECT_LEADER.equals(candidateRoleCode)) {
            if (context.getProjectId() == null) {
                return false;
            }
            Project project = projectDao.selectById(context.getProjectId());
            return project != null
                    && (Objects.equals(project.getLeaderUserId(), userId)
                            || hasProjectGrant(userId, context.getProjectId(), ProjectGrantRoleCodes.PROJECT_LEADER_BINDING));
        }
        if (EXPERT.equals(candidateRoleCode)) {
            return roles.contains(EXPERT)
                    && hasMatchingExpertGrant(userId, context.getProjectId(), context.getModuleType(),
                            context.getCurrentRoundNo(), context.getCurrentNodeId());
        }
        return roles.contains(candidateRoleCode);
    }

    private boolean hasMatchingExpertGrant(
            Long userId,
            Long projectId,
            String moduleType,
            Integer roundNo,
            String taskNodeId) {
        return !projectRoleGrantDao.selectMatchingActiveGrant(
                projectId,
                moduleType,
                ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT,
                userId,
                roundNo,
                taskNodeId).isEmpty();
    }

    private List<String> roles(Long userId) {
        return userRoleDetailViewDao.selectByUserId(userId).stream()
                .map(UserRoleDetailView::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private UserRoleDetailView firstRoleDetail(Long userId) {
        List<UserRoleDetailView> details = userRoleDetailViewDao.selectByUserId(userId);
        return details.isEmpty() ? null : details.getFirst();
    }
}
