package com.amatrix.sicprojectis_backend.system;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantLogDao;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrantLog;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.dao.AdminOperationLogDao;
import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.RoleDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.dto.AdminCountItemResponse;
import com.amatrix.sicprojectis_backend.system.dto.AdminOverviewResponse;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOverviewService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";

    private final AppUserDao appUserDao;
    private final RoleDao roleDao;
    private final PermissionDao permissionDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final ProjectRoleGrantLogDao projectRoleGrantLogDao;
    private final AdminOperationLogDao adminOperationLogDao;
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionService permissionService;

    public AdminOverviewService(
            AppUserDao appUserDao,
            RoleDao roleDao,
            PermissionDao permissionDao,
            ProjectRoleGrantDao projectRoleGrantDao,
            ProjectRoleGrantLogDao projectRoleGrantLogDao,
            AdminOperationLogDao adminOperationLogDao,
            UserRoleDetailViewDao userRoleDetailViewDao,
            PermissionService permissionService) {
        this.appUserDao = appUserDao;
        this.roleDao = roleDao;
        this.permissionDao = permissionDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.projectRoleGrantLogDao = projectRoleGrantLogDao;
        this.adminOperationLogDao = adminOperationLogDao;
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.permissionService = permissionService;
    }

    public AdminOverviewResponse getOverview(AuthenticatedUser currentUser) {
        if (!permissionService.hasRole(currentUser.userId(), SYSTEM_ADMIN)
                && !permissionService.hasRole(currentUser.userId(), SCIENCE_ADMIN)
                && !permissionService.hasRole(currentUser.userId(), "DEPT_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view admin overview");
        }

        List<ProjectRoleGrant> visibleGrants = projectRoleGrantDao.selectAll().stream()
                .filter(grant -> permissionService.canAccessProject(currentUser.userId(), grant.getProjectId())
                        || permissionService.hasRole(currentUser.userId(), SYSTEM_ADMIN)
                        || permissionService.hasRole(currentUser.userId(), SCIENCE_ADMIN))
                .toList();
        List<AdminCountItemResponse> grantTypeCounts = visibleGrants.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProjectRoleGrant::getGrantRoleCode,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new AdminCountItemResponse(entry.getKey(), entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AdminCountItemResponse::key))
                .toList();

        List<AdminCountItemResponse> roleCounts = userRoleDetailViewDao.selectAll().stream()
                .filter(detail -> detail.getRoleCode() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        UserRoleDetailView::getRoleCode,
                        java.util.stream.Collectors.mapping(UserRoleDetailView::getUserId, java.util.stream.Collectors.toSet())))
                .entrySet().stream()
                .map(entry -> new AdminCountItemResponse(entry.getKey(), entry.getKey(), entry.getValue().size()))
                .sorted(Comparator.comparing(AdminCountItemResponse::key))
                .toList();

        long enabledUserCount = appUserDao.selectAll().stream().filter(user -> Boolean.TRUE.equals(user.getEnabled())).count();
        long auditLogCount = countVisibleAuditLogs(currentUser);

        return new AdminOverviewResponse(
                appUserDao.selectAll().size(),
                enabledUserCount,
                roleDao.selectAll().stream().filter(role -> Boolean.TRUE.equals(role.getEnabled())).count(),
                permissionDao.selectAll().size(),
                visibleGrants.stream().filter(grant -> Objects.equals("ACTIVE", grant.getStatus())).count(),
                auditLogCount,
                grantTypeCounts,
                roleCounts);
    }

    private long countVisibleAuditLogs(AuthenticatedUser currentUser) {
        boolean systemAdmin = permissionService.hasRole(currentUser.userId(), SYSTEM_ADMIN);
        boolean scienceAdmin = permissionService.hasRole(currentUser.userId(), SCIENCE_ADMIN);
        if (systemAdmin) {
            return adminOperationLogDao.selectAll().size() + projectRoleGrantLogDao.selectAll().size();
        }
        if (scienceAdmin) {
            return adminOperationLogDao.selectAll().stream()
                    .filter(log -> log.getProjectId() == null || permissionService.canAccessProject(currentUser.userId(), log.getProjectId()))
                    .count()
                    + projectRoleGrantLogDao.selectAll().stream()
                            .filter(log -> canSeeProjectGrantLog(currentUser.userId(), log))
                            .count();
        }
        return projectRoleGrantLogDao.selectAll().stream()
                .filter(log -> canSeeProjectGrantLog(currentUser.userId(), log))
                .count();
    }

    private boolean canSeeProjectGrantLog(Long userId, ProjectRoleGrantLog log) {
        ProjectRoleGrant grant = projectRoleGrantDao.selectById(log.getProjectRoleGrantId());
        return grant != null && permissionService.canAccessProject(userId, grant.getProjectId());
    }
}
