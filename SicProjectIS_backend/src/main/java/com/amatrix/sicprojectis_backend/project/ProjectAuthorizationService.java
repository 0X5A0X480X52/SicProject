package com.amatrix.sicprojectis_backend.project;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectMemberDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectRoleGrantLogDao;
import com.amatrix.sicprojectis_backend.project.dto.AdminProjectAuthorizationsResponse;
import com.amatrix.sicprojectis_backend.project.dto.AssignProjectExpertRequest;
import com.amatrix.sicprojectis_backend.project.dto.AssignProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.BatchProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.BatchProjectMemberRequest;
import com.amatrix.sicprojectis_backend.project.dto.BatchRevokeProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.ChangeProjectLeaderRequest;
import com.amatrix.sicprojectis_backend.project.dto.ProjectAuthorizationCapabilitiesResponse;
import com.amatrix.sicprojectis_backend.project.dto.ProjectAuthorizationDetailResponse;
import com.amatrix.sicprojectis_backend.project.dto.ProjectGrantResponse;
import com.amatrix.sicprojectis_backend.project.dto.ProjectAuthorizationMutationResponse;
import com.amatrix.sicprojectis_backend.project.dto.ProjectMemberResponse;
import com.amatrix.sicprojectis_backend.project.dto.ProjectSummaryResponse;
import com.amatrix.sicprojectis_backend.project.dto.RevokeProjectGrantRequest;
import com.amatrix.sicprojectis_backend.project.dto.UpsertProjectMemberRequest;
import com.amatrix.sicprojectis_backend.project.dto.UserSummaryResponse;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.project.entity.ProjectMember;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrantLog;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.system.AdminAuditLogService;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.dao.DepartmentDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.dto.AdminCountItemResponse;
import com.amatrix.sicprojectis_backend.system.dto.ChangeDiffSummaryResponse;
import com.amatrix.sicprojectis_backend.system.entity.AdminOperationLog;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.system.entity.Department;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectAuthorizationService {
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String SCIENCE_ADMIN = "SCIENCE_ADMIN";
    private static final String DEPT_ADMIN = "DEPT_ADMIN";
    private static final String EXPERT = "EXPERT";
    private static final String FINANCE_ADMIN = "FINANCE_ADMIN";
    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";
    private static final String MEMBER_ROLE_LEADER = "LEADER";
    private static final String MEMBER_ROLE_MEMBER = "MEMBER";

    private final ProjectDao projectDao;
    private final ProjectMemberDao projectMemberDao;
    private final ProjectRoleGrantDao projectRoleGrantDao;
    private final ProjectRoleGrantLogDao projectRoleGrantLogDao;
    private final AppUserDao appUserDao;
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final DepartmentDao departmentDao;
    private final PermissionService permissionService;
    private final AdminAuditLogService adminAuditLogService;

    public ProjectAuthorizationService(
            ProjectDao projectDao,
            ProjectMemberDao projectMemberDao,
            ProjectRoleGrantDao projectRoleGrantDao,
            ProjectRoleGrantLogDao projectRoleGrantLogDao,
            AppUserDao appUserDao,
            UserRoleDetailViewDao userRoleDetailViewDao,
            DepartmentDao departmentDao,
            PermissionService permissionService,
            AdminAuditLogService adminAuditLogService) {
        this.projectDao = projectDao;
        this.projectMemberDao = projectMemberDao;
        this.projectRoleGrantDao = projectRoleGrantDao;
        this.projectRoleGrantLogDao = projectRoleGrantLogDao;
        this.appUserDao = appUserDao;
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.departmentDao = departmentDao;
        this.permissionService = permissionService;
        this.adminAuditLogService = adminAuditLogService;
    }

    public List<ProjectSummaryResponse> listAccessibleProjects(AuthenticatedUser currentUser) {
        Lookup lookup = buildLookup();
        List<UserRoleDetailView> currentUserRoles = userRoleDetailViewDao.selectByUserId(currentUser.userId());
        Set<String> roleCodes = currentUserRoles.stream()
                .map(UserRoleDetailView::getRoleCode)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Long currentUserDeptId = currentUserRoles.stream()
                .map(UserRoleDetailView::getDeptId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (roleCodes.contains(SYSTEM_ADMIN) || roleCodes.contains(SCIENCE_ADMIN)) {
            return projectDao.selectAll().stream()
                    .sorted(Comparator.comparing(Project::getProjectId))
                    .map(project -> toProjectSummary(project, lookup))
                    .toList();
        }

        Set<Long> memberProjectIds = projectMemberDao.selectAll().stream()
                .filter(member -> Objects.equals(member.getUserId(), currentUser.userId()))
                .map(ProjectMember::getProjectId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Long> grantProjectIds = projectRoleGrantDao.selectActiveByGranteeUserId(currentUser.userId()).stream()
                .map(ProjectRoleGrant::getProjectId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return projectDao.selectAll().stream()
                .filter(project -> canAccessProjectFromSnapshot(currentUser.userId(), project, roleCodes,
                        currentUserDeptId, memberProjectIds, grantProjectIds)
                        || permissionService.canAccessProject(currentUser.userId(), project.getProjectId()))
                .sorted(Comparator.comparing(Project::getProjectId))
                .map(project -> toProjectSummary(project, lookup))
                .toList();
    }

    public AdminProjectAuthorizationsResponse listManageableProjects(AuthenticatedUser currentUser) {
        requireCanViewProjectManagement(currentUser.userId());
        List<ProjectRoleGrant> visibleActiveGrants = projectRoleGrantDao.selectAll().stream()
                .filter(grant -> ACTIVE.equals(grant.getStatus()))
                .filter(grant -> canViewProject(currentUser.userId(), grant.getProjectId()))
                .toList();
        List<AdminCountItemResponse> grantTypeCounts = visibleActiveGrants.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProjectRoleGrant::getGrantRoleCode,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new AdminCountItemResponse(entry.getKey(), entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AdminCountItemResponse::key))
                .toList();
        return new AdminProjectAuthorizationsResponse(listAccessibleProjects(currentUser), grantTypeCounts);
    }

    public ProjectAuthorizationDetailResponse getAuthorizationDetail(AuthenticatedUser currentUser, Long projectId) {
        Project project = requireProject(projectId);
        if (!canViewProject(currentUser.userId(), projectId)) {
            throw forbidden("You do not have access to this project");
        }
        Lookup lookup = buildLookup();
        return buildAuthorizationDetail(currentUser.userId(), project, lookup);
    }

    @Transactional
    public ProjectAuthorizationDetailResponse changeLeader(
            AuthenticatedUser currentUser,
            Long projectId,
            ChangeProjectLeaderRequest request) {
        Project project = requireProject(projectId);
        requireCanManageLeader(currentUser.userId(), project);

        AppUser nextLeader = requireEnabledUser(request.userId(), "Leader user is required");
        LocalDateTime now = LocalDateTime.now();
        Long previousLeaderUserId = project.getLeaderUserId();

        if (Objects.equals(previousLeaderUserId, nextLeader.getUserId())) {
            return buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
        }

        revokeActiveGrant(
                project.getProjectId(),
                ProjectGrantRoleCodes.PROJECT_LEADER_BINDING,
                previousLeaderUserId,
                currentUser.userId(),
                normalizeOptional(request.reason()));

        project.setLeaderUserId(nextLeader.getUserId());
        projectDao.updateById(project);

        ensureProjectMember(project.getProjectId(), nextLeader.getUserId(), MEMBER_ROLE_LEADER, normalizeOptional(request.reason()), now);
        if (previousLeaderUserId != null && !Objects.equals(previousLeaderUserId, nextLeader.getUserId())) {
            ProjectMember previousLeader = projectMemberDao.selectByProjectIdAndUserId(project.getProjectId(), previousLeaderUserId);
            if (previousLeader != null && MEMBER_ROLE_LEADER.equals(previousLeader.getMemberRole())) {
                previousLeader.setMemberRole(MEMBER_ROLE_MEMBER);
                projectMemberDao.updateById(previousLeader);
            }
        }

        upsertGrant(
                project.getProjectId(),
                ProjectGrantRoleCodes.PROJECT_LEADER_BINDING,
                nextLeader.getUserId(),
                currentUser.userId(),
                "PROJECT",
                null,
                null,
                normalizeOptional(request.reason()),
                now,
                null);

        AdminOperationLog log = new AdminOperationLog();
        log.setScopeType("PROJECT_LEADER");
        log.setActionType("UPDATE");
        log.setOperatorUserId(currentUser.userId());
        log.setTargetUserId(nextLeader.getUserId());
        log.setProjectId(projectId);
        log.setGrantType(ProjectGrantRoleCodes.PROJECT_LEADER_BINDING);
        log.setBeforeSnapshotJson(previousLeaderUserId == null ? null : "leaderUserId=" + previousLeaderUserId);
        log.setAfterSnapshotJson("leaderUserId=" + nextLeader.getUserId());
        log.setRemark(normalizeOptional(request.reason()));
        adminAuditLogService.logOperation(log);

        return buildAuthorizationDetail(currentUser.userId(), requireProject(projectId), buildLookup());
    }

    @Transactional
    public ProjectAuthorizationDetailResponse upsertMember(
            AuthenticatedUser currentUser,
            Long projectId,
            UpsertProjectMemberRequest request) {
        Project project = requireProject(projectId);
        requireCanManageMembers(currentUser.userId(), project);

        AppUser member = requireEnabledUser(request.userId(), "Member user is required");
        LocalDateTime now = LocalDateTime.now();
        String memberRole = Objects.equals(project.getLeaderUserId(), member.getUserId()) ? MEMBER_ROLE_LEADER : MEMBER_ROLE_MEMBER;

        ensureProjectMember(projectId, member.getUserId(), memberRole, normalizeOptional(request.responsibility()), now);
        upsertGrant(
                projectId,
                ProjectGrantRoleCodes.PROJECT_MEMBER_BINDING,
                member.getUserId(),
                currentUser.userId(),
                "PROJECT",
                null,
                null,
                normalizeOptional(request.responsibility()),
                now,
                null);

        return buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
    }

    @Transactional
    public ProjectAuthorizationMutationResponse upsertMembers(
            AuthenticatedUser currentUser,
            Long projectId,
            BatchProjectMemberRequest request) {
        Project project = requireProject(projectId);
        requireCanManageMembers(currentUser.userId(), project);
        List<Long> userIds = normalizeDistinctUserIds(request.userIds(), "At least one member is required");
        Set<Long> existingUserIds = projectMemberDao.selectByProjectId(projectId).stream()
                .map(ProjectMember::getUserId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LocalDateTime now = LocalDateTime.now();
        List<String> added = new java.util.ArrayList<>();
        for (Long userId : userIds) {
            AppUser member = requireEnabledUser(userId, "Selected user is disabled or missing");
            String memberRole = Objects.equals(project.getLeaderUserId(), member.getUserId()) ? MEMBER_ROLE_LEADER : MEMBER_ROLE_MEMBER;
            ensureProjectMember(projectId, member.getUserId(), memberRole, normalizeOptional(request.responsibility()), now);
            upsertGrant(
                    projectId,
                    ProjectGrantRoleCodes.PROJECT_MEMBER_BINDING,
                    member.getUserId(),
                    currentUser.userId(),
                    "PROJECT",
                    null,
                    null,
                    normalizeOptional(request.responsibility()),
                    now,
                    null);
            if (!existingUserIds.contains(member.getUserId())) {
                added.add(member.getUsername());
            }
        }
        ChangeDiffSummaryResponse diff = new ChangeDiffSummaryResponse(added.stream().sorted().toList(), List.of());
        ProjectAuthorizationDetailResponse detail = buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
        return new ProjectAuthorizationMutationResponse(
                detail,
                diff,
                userIds.size(),
                "Saved " + userIds.size() + " project member assignment(s)");
    }

    @Transactional
    public ProjectAuthorizationDetailResponse removeMember(
            AuthenticatedUser currentUser,
            Long projectId,
            Long userId) {
        Project project = requireProject(projectId);
        requireCanManageMembers(currentUser.userId(), project);

        if (Objects.equals(project.getLeaderUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current leader cannot be removed as a member");
        }

        projectMemberDao.deleteByProjectIdAndUserId(projectId, userId);
        revokeActiveGrant(
                projectId,
                ProjectGrantRoleCodes.PROJECT_MEMBER_BINDING,
                userId,
                currentUser.userId(),
                "Member removed");

        return buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
    }

    @Transactional
    public ProjectAuthorizationDetailResponse assignExpert(
            AuthenticatedUser currentUser,
            Long projectId,
            AssignProjectExpertRequest request) {
        Project project = requireProject(projectId);
        requireCanManageExperts(currentUser.userId(), project);

        AppUser expert = requireEnabledUser(request.userId(), "Expert user is required");
        if (!permissionService.hasRole(expert.getUserId(), EXPERT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected user does not have global EXPERT role");
        }

        upsertGrant(
                projectId,
                ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT,
                expert.getUserId(),
                currentUser.userId(),
                "MODULE",
                normalizeRequired(request.moduleType(), "Module type is required"),
                request.roundNo(),
                normalizeOptional(request.reason()),
                LocalDateTime.now(),
                normalizeOptional(request.taskNodeId()));

        return buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
    }

    @Transactional
    public ProjectAuthorizationMutationResponse assignExperts(
            AuthenticatedUser currentUser,
            Long projectId,
            BatchProjectGrantRequest request) {
        return assignGrants(
                currentUser,
                projectId,
                request,
                ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT,
                "MODULE",
                EXPERT,
                "expert");
    }

    @Transactional
    public ProjectAuthorizationDetailResponse assignFinance(
            AuthenticatedUser currentUser,
            Long projectId,
            AssignProjectGrantRequest request) {
        Project project = requireProject(projectId);
        requireCanManageFinance(currentUser.userId(), project);

        AppUser user = requireEnabledUser(request.userId(), "Finance user is required");
        if (!permissionService.hasRole(user.getUserId(), FINANCE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected user does not have global FINANCE_ADMIN role");
        }

        upsertGrant(
                projectId,
                ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT,
                user.getUserId(),
                currentUser.userId(),
                "PROJECT",
                normalizeOptional(request.moduleType()),
                request.roundNo(),
                normalizeOptional(request.reason()),
                LocalDateTime.now(),
                normalizeOptional(request.taskNodeId()));

        return buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
    }

    @Transactional
    public ProjectAuthorizationMutationResponse assignFinances(
            AuthenticatedUser currentUser,
            Long projectId,
            BatchProjectGrantRequest request) {
        return assignGrants(
                currentUser,
                projectId,
                request,
                ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT,
                "PROJECT",
                FINANCE_ADMIN,
                "finance");
    }

    @Transactional
    public ProjectAuthorizationDetailResponse assignProxy(
            AuthenticatedUser currentUser,
            Long projectId,
            AssignProjectGrantRequest request) {
        Project project = requireProject(projectId);
        requireCanManageProxy(currentUser.userId(), project);

        AppUser user = requireEnabledUser(request.userId(), "Proxy user is required");
        upsertGrant(
                projectId,
                ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT,
                user.getUserId(),
                currentUser.userId(),
                "PROJECT",
                normalizeOptional(request.moduleType()),
                request.roundNo(),
                normalizeOptional(request.reason()),
                LocalDateTime.now(),
                normalizeOptional(request.taskNodeId()));

        return buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
    }

    @Transactional
    public ProjectAuthorizationMutationResponse assignProxies(
            AuthenticatedUser currentUser,
            Long projectId,
            BatchProjectGrantRequest request) {
        return assignGrants(
                currentUser,
                projectId,
                request,
                ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT,
                "PROJECT",
                null,
                "proxy");
    }

    @Transactional
    public ProjectAuthorizationDetailResponse revokeGrant(
            AuthenticatedUser currentUser,
            Long projectId,
            Long grantId,
            RevokeProjectGrantRequest request) {
        Project project = requireProject(projectId);
        ProjectRoleGrant grant = projectRoleGrantDao.selectById(grantId);
        if (grant == null || !Objects.equals(grant.getProjectId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project grant not found");
        }
        requireCanRevokeGrant(currentUser.userId(), project, grant);
        revokeGrant(grant, currentUser.userId(), normalizeOptional(request.reason()));
        return buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
    }

    @Transactional
    public ProjectAuthorizationMutationResponse revokeGrants(
            AuthenticatedUser currentUser,
            Long projectId,
            BatchRevokeProjectGrantRequest request) {
        Project project = requireProject(projectId);
        List<Long> grantIds = normalizeDistinctUserIds(request.grantIds(), "At least one grant must be selected");
        List<String> removed = new java.util.ArrayList<>();
        for (Long grantId : grantIds) {
            ProjectRoleGrant grant = projectRoleGrantDao.selectById(grantId);
            if (grant == null || !Objects.equals(grant.getProjectId(), projectId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project grant not found: " + grantId);
            }
            requireCanRevokeGrant(currentUser.userId(), project, grant);
            if (ACTIVE.equals(grant.getStatus())) {
                AppUser grantee = appUserDao.selectById(grant.getGranteeUserId());
                removed.add(grantee == null ? String.valueOf(grant.getGranteeUserId()) : grantee.getUsername());
                revokeGrant(grant, currentUser.userId(), normalizeOptional(request.reason()));
            }
        }
        ProjectAuthorizationDetailResponse detail = buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
        return new ProjectAuthorizationMutationResponse(
                detail,
                new ChangeDiffSummaryResponse(List.of(), removed.stream().sorted().toList()),
                removed.size(),
                "Revoked " + removed.size() + " project grant(s)");
    }

    private ProjectAuthorizationDetailResponse buildAuthorizationDetail(Long currentUserId, Project project, Lookup lookup) {
        UserSummaryResponse leader = lookup.userById.get(project.getLeaderUserId());
        List<ProjectMemberResponse> members = projectMemberDao.selectByProjectId(project.getProjectId()).stream()
                .sorted(Comparator.comparing(ProjectMember::getProjectMemberId))
                .map(member -> new ProjectMemberResponse(
                        member.getProjectMemberId(),
                        member.getMemberRole(),
                        member.getResponsibility(),
                        member.getJoinedAt(),
                        lookup.userById.get(member.getUserId())))
                .toList();
        List<ProjectGrantResponse> expertGrants = listGrantResponses(project.getProjectId(), ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT, lookup);
        List<ProjectGrantResponse> financeGrants = listGrantResponses(project.getProjectId(), ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT, lookup);
        List<ProjectGrantResponse> proxyGrants = listGrantResponses(project.getProjectId(), ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT, lookup);
        List<UserSummaryResponse> users = lookup.userById.values().stream()
                .sorted(Comparator.comparing(UserSummaryResponse::userId))
                .toList();

        return new ProjectAuthorizationDetailResponse(
                toProjectSummary(project, lookup),
                leader,
                members,
                expertGrants,
                financeGrants,
                proxyGrants,
                users,
                new ProjectAuthorizationCapabilitiesResponse(
                        canManageLeader(currentUserId, project),
                        canManageMembers(currentUserId, project),
                        canManageExperts(currentUserId, project),
                        canManageFinance(currentUserId, project),
                        canManageProxy(currentUserId, project)));
    }

    private ProjectAuthorizationMutationResponse assignGrants(
            AuthenticatedUser currentUser,
            Long projectId,
            BatchProjectGrantRequest request,
            String grantRoleCode,
            String grantScope,
            String requiredGlobalRole,
            String label) {
        Project project = requireProject(projectId);
        if (ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT.equals(grantRoleCode)) {
            requireCanManageExperts(currentUser.userId(), project);
        } else if (ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT.equals(grantRoleCode)) {
            requireCanManageFinance(currentUser.userId(), project);
        } else if (ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT.equals(grantRoleCode)) {
            requireCanManageProxy(currentUser.userId(), project);
        }
        String moduleType = ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT.equals(grantRoleCode)
                ? normalizeRequired(request.moduleType(), "Module type is required")
                : normalizeOptional(request.moduleType());
        String taskNodeId = normalizeOptional(request.taskNodeId());
        String reason = normalizeOptional(request.reason());

        List<Long> userIds = normalizeDistinctUserIds(request.userIds(), "At least one user is required");
        LocalDateTime now = LocalDateTime.now();
        List<String> added = new java.util.ArrayList<>();
        for (Long userId : userIds) {
            AppUser user = requireEnabledUser(userId, "Selected user is disabled or missing");
            if (requiredGlobalRole != null && !permissionService.hasRole(user.getUserId(), requiredGlobalRole)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Selected user does not have global " + requiredGlobalRole + " role");
            }
            List<ProjectRoleGrant> before = projectRoleGrantDao.selectMatchingActiveGrant(
                    projectId,
                    moduleType,
                    grantRoleCode,
                    user.getUserId(),
                    request.roundNo(),
                    taskNodeId);
            upsertGrant(
                    projectId,
                    grantRoleCode,
                    user.getUserId(),
                    currentUser.userId(),
                    grantScope,
                    moduleType,
                    request.roundNo(),
                    reason,
                    now,
                    taskNodeId);
            if (before.isEmpty()) {
                added.add(user.getUsername());
            }
        }
        ProjectAuthorizationDetailResponse detail = buildAuthorizationDetail(currentUser.userId(), project, buildLookup());
        return new ProjectAuthorizationMutationResponse(
                detail,
                new ChangeDiffSummaryResponse(added.stream().sorted().toList(), List.of()),
                userIds.size(),
                "Saved " + userIds.size() + " " + label + " grant(s)");
    }

    private List<ProjectGrantResponse> listGrantResponses(Long projectId, String grantRoleCode, Lookup lookup) {
        return projectRoleGrantDao.selectActiveByProjectAndGrantRole(projectId, grantRoleCode).stream()
                .sorted(Comparator.comparing(ProjectRoleGrant::getProjectRoleGrantId))
                .map(grant -> toGrantResponse(grant, lookup))
                .toList();
    }

    private ProjectSummaryResponse toProjectSummary(Project project, Lookup lookup) {
        Department department = lookup.departmentById.get(project.getDeptId());
        UserSummaryResponse leader = lookup.userById.get(project.getLeaderUserId());
        return new ProjectSummaryResponse(
                project.getProjectId(),
                project.getProjectCode(),
                project.getProjectName(),
                project.getDeptId(),
                department == null ? null : department.getDeptName(),
                project.getLeaderUserId(),
                leader == null ? null : leader.realName(),
                project.getProjectType(),
                project.getProjectLevel(),
                project.getLifecycleStage());
    }

    private boolean canAccessProjectFromSnapshot(
            Long userId,
            Project project,
            Set<String> roleCodes,
            Long userDeptId,
            Set<Long> memberProjectIds,
            Set<Long> grantProjectIds) {
        if (roleCodes.contains(DEPT_ADMIN) && Objects.equals(userDeptId, project.getDeptId())) {
            return true;
        }
        return Objects.equals(project.getLeaderUserId(), userId)
                || memberProjectIds.contains(project.getProjectId())
                || grantProjectIds.contains(project.getProjectId());
    }
    private ProjectGrantResponse toGrantResponse(ProjectRoleGrant grant, Lookup lookup) {
        return new ProjectGrantResponse(
                grant.getProjectRoleGrantId(),
                grant.getGrantRoleCode(),
                grant.getGrantScope(),
                grant.getModuleType(),
                grant.getRoundNo(),
                grant.getTaskNodeId(),
                grant.getStatus(),
                grant.getGrantReason(),
                grant.getEffectiveFrom(),
                grant.getEffectiveTo(),
                lookup.userById.get(grant.getGranteeUserId()),
                lookup.userById.get(grant.getGrantedByUserId()));
    }

    private Lookup buildLookup() {
        Map<Long, Department> departmentById = departmentDao.selectAll().stream()
                .collect(LinkedHashMap::new, (map, department) -> map.put(department.getDeptId(), department), Map::putAll);

        Map<Long, List<UserRoleDetailView>> roleDetailsByUserId = userRoleDetailViewDao.selectAll().stream()
                .filter(detail -> detail.getUserId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        UserRoleDetailView::getUserId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        Map<Long, UserSummaryResponse> userById = new LinkedHashMap<>();
        for (AppUser user : appUserDao.selectAll()) {
            List<UserRoleDetailView> details = roleDetailsByUserId.getOrDefault(user.getUserId(), List.of());
            String deptName = null;
            if (!details.isEmpty()) {
                deptName = details.getFirst().getDeptName();
            } else if (user.getDeptId() != null) {
                Department department = departmentById.get(user.getDeptId());
                deptName = department == null ? null : department.getDeptName();
            }
            List<String> roleCodes = details.stream()
                    .map(UserRoleDetailView::getRoleCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            userById.put(user.getUserId(), new UserSummaryResponse(
                    user.getUserId(),
                    user.getUsername(),
                    user.getRealName(),
                    user.getDeptId(),
                    deptName,
                    roleCodes));
        }
        return new Lookup(userById, departmentById);
    }

    private void ensureProjectMember(
            Long projectId,
            Long userId,
            String memberRole,
            String responsibility,
            LocalDateTime now) {
        ProjectMember existing = projectMemberDao.selectByProjectIdAndUserId(projectId, userId);
        if (existing == null) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(userId);
            member.setMemberRole(memberRole);
            member.setResponsibility(responsibility);
            member.setJoinedAt(now);
            projectMemberDao.insert(member);
            return;
        }
        existing.setMemberRole(memberRole);
        existing.setResponsibility(responsibility);
        projectMemberDao.updateById(existing);
    }

    private ProjectRoleGrant upsertGrant(
            Long projectId,
            String grantRoleCode,
            Long granteeUserId,
            Long operatorUserId,
            String grantScope,
            String moduleType,
            Integer roundNo,
            String grantReason,
            LocalDateTime now,
            String taskNodeId) {
        List<ProjectRoleGrant> matches = projectRoleGrantDao.selectMatchingActiveGrant(
                projectId,
                moduleType,
                grantRoleCode,
                granteeUserId,
                roundNo,
                taskNodeId);
        if (!matches.isEmpty()) {
            ProjectRoleGrant existing = matches.getFirst();
            ProjectRoleGrant before = copyGrant(existing);
            existing.setGrantReason(grantReason);
            existing.setGrantedByUserId(operatorUserId);
            existing.setGrantScope(grantScope);
            existing.setUpdatedAt(now);
            projectRoleGrantDao.updateById(existing);
            logGrantChange(existing.getProjectRoleGrantId(), "UPDATED", operatorUserId, before, existing, grantReason);
            return existing;
        }

        ProjectRoleGrant grant = new ProjectRoleGrant();
        grant.setProjectId(projectId);
        grant.setModuleType(moduleType);
        grant.setGrantRoleCode(grantRoleCode);
        grant.setGranteeUserId(granteeUserId);
        grant.setGrantedByUserId(operatorUserId);
        grant.setGrantScope(grantScope);
        grant.setRoundNo(roundNo);
        grant.setTaskNodeId(taskNodeId);
        grant.setStatus(ACTIVE);
        grant.setEffectiveFrom(now);
        grant.setGrantReason(grantReason);
        grant.setCreatedAt(now);
        grant.setUpdatedAt(now);
        projectRoleGrantDao.insert(grant);
        logGrantChange(grant.getProjectRoleGrantId(), "GRANT", operatorUserId, null, grant, grantReason);
        return grant;
    }

    private void revokeActiveGrant(
            Long projectId,
            String grantRoleCode,
            Long granteeUserId,
            Long operatorUserId,
            String reason) {
        if (granteeUserId == null) {
            return;
        }
        for (ProjectRoleGrant grant : projectRoleGrantDao.selectActiveForUserAndProject(granteeUserId, projectId)) {
            if (grantRoleCode.equals(grant.getGrantRoleCode())) {
                revokeGrant(grant, operatorUserId, reason);
            }
        }
    }

    private void revokeGrant(ProjectRoleGrant grant, Long operatorUserId, String reason) {
        if (grant == null || !ACTIVE.equals(grant.getStatus())) {
            return;
        }
        ProjectRoleGrant before = copyGrant(grant);
        LocalDateTime now = LocalDateTime.now();
        grant.setStatus(REVOKED);
        grant.setEffectiveTo(now);
        grant.setUpdatedAt(now);
        projectRoleGrantDao.updateById(grant);
        logGrantChange(grant.getProjectRoleGrantId(), "REVOKE", operatorUserId, before, grant, reason);
    }

    private void logGrantChange(
            Long grantId,
            String actionType,
            Long operatorUserId,
            ProjectRoleGrant before,
            ProjectRoleGrant after,
            String remark) {
        ProjectRoleGrantLog log = new ProjectRoleGrantLog();
        log.setProjectRoleGrantId(grantId);
        log.setActionType(actionType);
        log.setOperatorUserId(operatorUserId);
        log.setBeforeSnapshotJson(toJson(before));
        log.setAfterSnapshotJson(toJson(after));
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        projectRoleGrantLogDao.insert(log);
    }

    private String toJson(ProjectRoleGrant grant) {
        return grant == null ? null : grant.toString();
    }

    private ProjectRoleGrant copyGrant(ProjectRoleGrant grant) {
        ProjectRoleGrant copy = new ProjectRoleGrant();
        copy.setProjectRoleGrantId(grant.getProjectRoleGrantId());
        copy.setProjectId(grant.getProjectId());
        copy.setModuleType(grant.getModuleType());
        copy.setGrantRoleCode(grant.getGrantRoleCode());
        copy.setGranteeUserId(grant.getGranteeUserId());
        copy.setGrantedByUserId(grant.getGrantedByUserId());
        copy.setGrantScope(grant.getGrantScope());
        copy.setRoundNo(grant.getRoundNo());
        copy.setTaskNodeId(grant.getTaskNodeId());
        copy.setStatus(grant.getStatus());
        copy.setEffectiveFrom(grant.getEffectiveFrom());
        copy.setEffectiveTo(grant.getEffectiveTo());
        copy.setGrantReason(grant.getGrantReason());
        copy.setCreatedAt(grant.getCreatedAt());
        copy.setUpdatedAt(grant.getUpdatedAt());
        return copy;
    }

    private boolean canViewProject(Long userId, Long projectId) {
        return permissionService.canAccessProject(userId, projectId)
                || hasAnyRole(userId, SYSTEM_ADMIN, SCIENCE_ADMIN)
                || (permissionService.hasRole(userId, DEPT_ADMIN) && isProjectInUserDepartment(userId, requireProject(projectId).getDeptId()));
    }

    private boolean canManageLeader(Long userId, Project project) {
        return hasAnyRole(userId, SYSTEM_ADMIN, SCIENCE_ADMIN);
    }

    private boolean canManageMembers(Long userId, Project project) {
        if (hasAnyRole(userId, SYSTEM_ADMIN, SCIENCE_ADMIN)) {
            return true;
        }
        return permissionService.hasRole(userId, DEPT_ADMIN)
                && isProjectInUserDepartment(userId, project.getDeptId());
    }

    private boolean canManageExperts(Long userId, Project project) {
        return hasAnyRole(userId, SYSTEM_ADMIN, SCIENCE_ADMIN);
    }

    private boolean canManageFinance(Long userId, Project project) {
        return hasAnyRole(userId, SYSTEM_ADMIN, SCIENCE_ADMIN);
    }

    private boolean canManageProxy(Long userId, Project project) {
        return hasAnyRole(userId, SYSTEM_ADMIN, SCIENCE_ADMIN);
    }

    private void requireCanViewProjectManagement(Long userId) {
        if (!hasAnyRole(userId, SYSTEM_ADMIN, SCIENCE_ADMIN, DEPT_ADMIN)) {
            throw forbidden("You do not have permission to view project authorization workspace");
        }
    }

    private void requireCanManageLeader(Long userId, Project project) {
        if (!canManageLeader(userId, project)) {
            throw forbidden("You do not have permission to change the project leader");
        }
    }

    private void requireCanManageMembers(Long userId, Project project) {
        if (!canManageMembers(userId, project)) {
            throw forbidden("You do not have permission to manage project members");
        }
    }

    private void requireCanManageExperts(Long userId, Project project) {
        if (!canManageExperts(userId, project)) {
            throw forbidden("You do not have permission to assign project experts");
        }
    }

    private void requireCanManageFinance(Long userId, Project project) {
        if (!canManageFinance(userId, project)) {
            throw forbidden("You do not have permission to assign project finance handlers");
        }
    }

    private void requireCanManageProxy(Long userId, Project project) {
        if (!canManageProxy(userId, project)) {
            throw forbidden("You do not have permission to assign project proxy recorders");
        }
    }

    private void requireCanRevokeGrant(Long userId, Project project, ProjectRoleGrant grant) {
        String grantRoleCode = grant.getGrantRoleCode();
        if (ProjectGrantRoleCodes.PROJECT_MODULE_EXPERT_ASSIGNMENT.equals(grantRoleCode)) {
            requireCanManageExperts(userId, project);
            return;
        }
        if (ProjectGrantRoleCodes.PROJECT_FINANCE_HANDLER_ASSIGNMENT.equals(grantRoleCode)) {
            requireCanManageFinance(userId, project);
            return;
        }
        if (ProjectGrantRoleCodes.PROJECT_PROXY_RECORDER_ASSIGNMENT.equals(grantRoleCode)) {
            requireCanManageProxy(userId, project);
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This grant type cannot be revoked through the generic endpoint");
    }

    private boolean hasAnyRole(Long userId, String... roleCodes) {
        for (String roleCode : roleCodes) {
            if (permissionService.hasRole(userId, roleCode)) {
                return true;
            }
        }
        return false;
    }

    private List<Long> normalizeDistinctUserIds(List<Long> values, String message) {
        if (values == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        List<Long> normalized = values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private boolean isProjectInUserDepartment(Long userId, Long deptId) {
        AppUser user = appUserDao.selectById(userId);
        return user != null && Objects.equals(user.getDeptId(), deptId);
    }

    private Project requireProject(Long projectId) {
        Project project = projectDao.selectById(projectId);
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return project;
    }

    private AppUser requireEnabledUser(Long userId, String message) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        AppUser user = appUserDao.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected user is disabled or missing");
        }
        return user;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private record Lookup(
            Map<Long, UserSummaryResponse> userById,
            Map<Long, Department> departmentById) {
    }
}
