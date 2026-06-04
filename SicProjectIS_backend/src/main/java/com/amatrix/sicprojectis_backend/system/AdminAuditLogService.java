package com.amatrix.sicprojectis_backend.system;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantLogDao;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrantLog;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.system.dao.AdminOperationLogDao;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.dto.AdminUserSummaryResponse;
import com.amatrix.sicprojectis_backend.system.dto.AuditLogQueryResponse;
import com.amatrix.sicprojectis_backend.system.dto.AuditLogRecordResponse;
import com.amatrix.sicprojectis_backend.system.entity.AdminOperationLog;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuditLogService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";

    private final AdminOperationLogDao adminOperationLogDao;
    private final ProjectRoleGrantLogDao projectRoleGrantLogDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final ProjectDao projectDao;
    private final AppUserDao appUserDao;
    private final PermissionService permissionService;

    public AdminAuditLogService(
            AdminOperationLogDao adminOperationLogDao,
            ProjectRoleGrantLogDao projectRoleGrantLogDao,
            ProjectRoleGrantDao projectRoleGrantDao,
            ProjectDao projectDao,
            AppUserDao appUserDao,
            PermissionService permissionService) {
        this.adminOperationLogDao = adminOperationLogDao;
        this.projectRoleGrantLogDao = projectRoleGrantLogDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.projectDao = projectDao;
        this.appUserDao = appUserDao;
        this.permissionService = permissionService;
    }

    public AuditLogQueryResponse queryLogs(
            AuthenticatedUser currentUser,
            String keyword,
            String actionType,
            String scopeType,
            Long projectId,
            Long operatorUserId,
            LocalDate dateFrom,
            LocalDate dateTo) {
        boolean systemAdmin = permissionService.hasRole(currentUser.userId(), SYSTEM_ADMIN);
        boolean scienceAdmin = permissionService.hasRole(currentUser.userId(), SCIENCE_ADMIN);
        if (!systemAdmin && !scienceAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view audit logs");
        }

        Lookup lookup = buildLookup();
        List<AuditLogRecordResponse> logs = new ArrayList<>();
        for (AdminOperationLog log : adminOperationLogDao.selectAll()) {
            if (!systemAdmin && !Objects.equals(log.getProjectId(), null)) {
                if (!permissionService.canAccessProject(currentUser.userId(), log.getProjectId())) {
                    continue;
                }
            }
            logs.add(toAdminLogRecord(log, lookup));
        }
        Map<Long, ProjectRoleGrant> grantById = projectRoleGrantDao.selectAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProjectRoleGrant::getProjectRoleGrantId,
                        grant -> grant,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (ProjectRoleGrantLog log : projectRoleGrantLogDao.selectAll()) {
            ProjectRoleGrant grant = grantById.get(log.getProjectRoleGrantId());
            if (grant == null) {
                continue;
            }
            if (!systemAdmin && !permissionService.canAccessProject(currentUser.userId(), grant.getProjectId())) {
                continue;
            }
            logs.add(toGrantLogRecord(log, grant, lookup));
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        List<AuditLogRecordResponse> filtered = logs.stream()
                .filter(log -> actionType == null || actionType.isBlank() || actionType.equals(log.actionType()))
                .filter(log -> scopeType == null || scopeType.isBlank() || scopeType.equals(log.scopeType()))
                .filter(log -> projectId == null || Objects.equals(projectId, log.projectId()))
                .filter(log -> operatorUserId == null || Objects.equals(operatorUserId, log.operatorUser() == null ? null : log.operatorUser().userId()))
                .filter(log -> withinDate(log.createdAt(), dateFrom, dateTo))
                .filter(log -> normalizedKeyword == null || searchableText(log).contains(normalizedKeyword))
                .sorted(Comparator.comparing(AuditLogRecordResponse::createdAt).reversed())
                .toList();
        return new AuditLogQueryResponse(filtered);
    }

    public void logOperation(AdminOperationLog log) {
        log.setCreatedAt(log.getCreatedAt() == null ? LocalDateTime.now() : log.getCreatedAt());
        adminOperationLogDao.insert(log);
    }

    private AuditLogRecordResponse toAdminLogRecord(AdminOperationLog log, Lookup lookup) {
        return new AuditLogRecordResponse(
                "ADMIN-" + log.getAdminOperationLogId(),
                log.getScopeType(),
                log.getActionType(),
                log.getProjectId(),
                lookup.projectNameById.get(log.getProjectId()),
                lookup.userById.get(log.getOperatorUserId()),
                lookup.userById.get(log.getTargetUserId()),
                log.getGrantType(),
                log.getRoleCode(),
                log.getPermissionCode(),
                log.getBeforeSnapshotJson(),
                log.getAfterSnapshotJson(),
                log.getRemark(),
                log.getCreatedAt());
    }

    private AuditLogRecordResponse toGrantLogRecord(ProjectRoleGrantLog log, ProjectRoleGrant grant, Lookup lookup) {
        return new AuditLogRecordResponse(
                "GRANT-" + log.getGrantLogId(),
                "PROJECT_GRANT",
                log.getActionType(),
                grant.getProjectId(),
                lookup.projectNameById.get(grant.getProjectId()),
                lookup.userById.get(log.getOperatorUserId()),
                lookup.userById.get(grant.getGranteeUserId()),
                grant.getGrantRoleCode(),
                null,
                null,
                log.getBeforeSnapshotJson(),
                log.getAfterSnapshotJson(),
                log.getRemark(),
                log.getCreatedAt());
    }

    private boolean withinDate(LocalDateTime createdAt, LocalDate dateFrom, LocalDate dateTo) {
        if (createdAt == null) {
            return false;
        }
        LocalDate date = createdAt.toLocalDate();
        if (dateFrom != null && date.isBefore(dateFrom)) {
            return false;
        }
        if (dateTo != null && date.isAfter(dateTo)) {
            return false;
        }
        return true;
    }

    private String searchableText(AuditLogRecordResponse log) {
        return String.join(" ",
                nullSafe(log.scopeType()),
                nullSafe(log.actionType()),
                nullSafe(log.projectName()),
                log.operatorUser() == null ? "" : nullSafe(log.operatorUser().realName()),
                log.operatorUser() == null ? "" : nullSafe(log.operatorUser().username()),
                log.targetUser() == null ? "" : nullSafe(log.targetUser().realName()),
                log.targetUser() == null ? "" : nullSafe(log.targetUser().username()),
                nullSafe(log.grantType()),
                nullSafe(log.roleCode()),
                nullSafe(log.permissionCode()),
                nullSafe(log.remark()))
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private Lookup buildLookup() {
        Map<Long, AdminUserSummaryResponse> userById = appUserDao.selectAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        AppUser::getUserId,
                        user -> new AdminUserSummaryResponse(user.getUserId(), user.getUsername(), user.getRealName()),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, String> projectNameById = projectDao.selectAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Project::getProjectId,
                        Project::getProjectName,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return new Lookup(userById, projectNameById);
    }

    private record Lookup(
            Map<Long, AdminUserSummaryResponse> userById,
            Map<Long, String> projectNameById) {
    }
}
