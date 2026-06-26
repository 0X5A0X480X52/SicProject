package com.amatrix.sicprojectis_backend.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.amatrix.sicprojectis_backend.project.ProjectGrantRoleCodes;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectMemberDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleRuntimeContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("permissionService")
public class PermissionService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";
    private static final String DEPT_ADMIN = "DEPT_ADMIN";
    private static final String PROJECT_LEADER = "PROJECT_LEADER";
    private static final String EXPERT = "EXPERT";
    private static final String FINANCE_ADMIN = "FINANCE_ADMIN";

    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionDao permissionDao;
    private final ProjectDao projectDao;
    private final ProjectMemberDao projectMemberDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final ModuleRuntimeContextViewDao moduleRuntimeContextViewDao;
    private final TaskInstanceDao taskInstanceDao;
    private final ProjectModuleInstanceDao projectModuleInstanceDao;

    public PermissionService(UserRoleDetailViewDao userRoleDetailViewDao, PermissionDao permissionDao,
            ProjectDao projectDao, ProjectMemberDao projectMemberDao, ProjectRoleGrantDao projectRoleGrantDao,
            ModuleRuntimeContextViewDao moduleRuntimeContextViewDao, TaskInstanceDao taskInstanceDao,
            ProjectModuleInstanceDao projectModuleInstanceDao) {
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.permissionDao = permissionDao;
        this.projectDao = projectDao;
        this.projectMemberDao = projectMemberDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.moduleRuntimeContextViewDao = moduleRuntimeContextViewDao;
        this.taskInstanceDao = taskInstanceDao;
        this.projectModuleInstanceDao = projectModuleInstanceDao;
    }

    public boolean hasRole(Long userId, String roleCode) {
        return roles(userId).contains(roleCode);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        if (userId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return permissionDao.countPermissionByUserId(userId, permissionCode) > 0;
    }

    public boolean canAccessProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return false;
        }
        Project project = projectDao.selectById(projectId);
        if (project == null) {
            return false;
        }
        List<String> roles = roles(userId);
        if (roles.contains(SYSTEM_ADMIN) || roles.contains(SCIENCE_ADMIN)) {
            return true;
        }
        if (roles.contains(DEPT_ADMIN)) {
            UserRoleDetailView user = firstRoleDetail(userId);
            if (user != null && Objects.equals(user.getDeptId(), project.getDeptId())) {
                return true;
            }
        }
        if (Objects.equals(project.getLeaderUserId(), userId)
                || projectMemberDao.countByProjectIdAndUserId(projectId, userId) > 0) {
            return true;
        }

        Set<String> grantRoleCodes = projectRoleGrantDao.selectActiveForUserAndProject(userId, projectId).stream()
                .map(ProjectRoleGrant::getGrantRoleCode)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (grantRoleCodes.contains(ProjectGrantRoleCodes.PROJECT_LEADER_BINDING)
                || grantRoleCodes.contains(ProjectGrantRoleCodes.PROJECT_MEMBER_BINDING)
                || grantRoleCodes.contains(ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT)
                || grantRoleCodes.contains(ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT)
                || (roles.contains(EXPERT) && grantRoleCodes.contains(ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT))) {
            return true;
        }
        if (roles.contains(EXPERT) && hasOpenTaskInProject(userId, projectId)) {
            return true;
        }
        // FINANCE_ADMIN can view all projects that have entered the acceptance stage,
        // but can only submit (operate) when there is a matching FINANCE_ADMIN task.
        if (roles.contains(FINANCE_ADMIN) && hasAcceptanceModule(projectId)) {
            return true;
        }
        return false;
    }

    public boolean canOperateModuleNode(Long userId, Long moduleInstanceId) {
        ModuleRuntimeContextView context = moduleRuntimeContextViewDao.selectByModuleInstanceId(moduleInstanceId);
        if (context == null || !canAccessProject(userId, context.getProjectId())) {
            return false;
        }
        List<String> roles = roles(userId);
        for (TaskInstance task : taskInstanceDao.selectOpenByModuleInstanceId(moduleInstanceId)) {
            if (Objects.equals(task.getAssigneeUserId(), userId)
                    || matchesCandidateRole(userId, context, task.getCandidateRoleCode(), roles)) {
                return true;
            }
        }
        return matchesCandidateRole(userId, context, context.getCurrentCandidateRoleCode(), roles);
    }

    private boolean hasOpenTaskInProject(Long userId, Long projectId) {
        return taskInstanceDao.countOpenAssignedByUserAndProject(userId, projectId) > 0;
    }

    private boolean hasAcceptanceModule(Long projectId) {
        return projectModuleInstanceDao.selectByProjectIdAndModuleType(projectId, "ACCEPTANCE") != null;
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
        if (userId == null) {
            return List.of();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && Objects.equals(user.userId(), userId) && user.roleCodes() != null) {
            return user.roleCodes().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
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
