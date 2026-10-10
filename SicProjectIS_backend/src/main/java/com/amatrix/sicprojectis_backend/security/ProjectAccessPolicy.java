package com.amatrix.sicprojectis_backend.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.amatrix.sicprojectis_backend.project.ProjectGrantRoleCodes;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectMemberDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;

/** Project-scoped authorization owned by the project domain. */
@Service("projectAccessPolicy")
public class ProjectAccessPolicy {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";
    private static final String DEPT_ADMIN = "DEPT_ADMIN";
    private static final String EXPERT = "EXPERT";
    private static final String FINANCE_ADMIN = "FINANCE_ADMIN";

    private final GlobalPermissionPolicy globalPermissionPolicy;
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final ProjectDao projectDao;
    private final ProjectMemberDao projectMemberDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final TaskInstanceDao taskInstanceDao;
    private final ProjectModuleInstanceDao projectModuleInstanceDao;

    public ProjectAccessPolicy(
            GlobalPermissionPolicy globalPermissionPolicy,
            UserRoleDetailViewDao userRoleDetailViewDao,
            ProjectDao projectDao,
            ProjectMemberDao projectMemberDao,
            ProjectRoleGrantDao projectRoleGrantDao,
            TaskInstanceDao taskInstanceDao,
            ProjectModuleInstanceDao projectModuleInstanceDao) {
        this.globalPermissionPolicy = globalPermissionPolicy;
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.projectDao = projectDao;
        this.projectMemberDao = projectMemberDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.taskInstanceDao = taskInstanceDao;
        this.projectModuleInstanceDao = projectModuleInstanceDao;
    }

    public boolean canAccess(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return false;
        }
        Project project = projectDao.selectById(projectId);
        if (project == null) {
            return false;
        }
        List<String> roles = globalPermissionPolicy.roles(userId);
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

        Set<String> grants = projectRoleGrantDao.selectActiveForUserAndProject(userId, projectId).stream()
                .map(ProjectRoleGrant::getGrantRoleCode)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (grants.contains(ProjectGrantRoleCodes.PROJECT_LEADER_BINDING)
                || grants.contains(ProjectGrantRoleCodes.PROJECT_MEMBER_BINDING)
                || grants.contains(ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT)
                || grants.contains(ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT)
                || (roles.contains(EXPERT)
                        && grants.contains(ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT))) {
            return true;
        }
        if (roles.contains(EXPERT) && taskInstanceDao.countOpenAssignedByUserAndProject(userId, projectId) > 0) {
            return true;
        }
        return roles.contains(FINANCE_ADMIN)
                && projectModuleInstanceDao.selectByProjectIdAndModuleType(projectId, "ACCEPTANCE") != null;
    }

    public boolean hasGrant(Long userId, Long projectId, String grantRoleCode) {
        if (userId == null || projectId == null || grantRoleCode == null) {
            return false;
        }
        return projectRoleGrantDao.selectActiveForUserAndProject(userId, projectId).stream()
                .map(ProjectRoleGrant::getGrantRoleCode)
                .anyMatch(grantRoleCode::equals);
    }

    private UserRoleDetailView firstRoleDetail(Long userId) {
        List<UserRoleDetailView> details = userRoleDetailViewDao.selectByUserId(userId);
        return details.isEmpty() ? null : details.getFirst();
    }
}
