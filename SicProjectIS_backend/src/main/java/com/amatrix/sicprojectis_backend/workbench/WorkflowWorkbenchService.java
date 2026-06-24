package com.amatrix.sicprojectis_backend.workbench;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleRuntimeContextViewDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleRuntimeContextView;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.PermissionService;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.workbench.dto.WorkflowWorkbenchItemResponse;

@Service
public class WorkflowWorkbenchService {
    private final ModuleRuntimeContextViewDao runtimeContextViewDao;
    private final ProjectDao projectDao;
    private final TaskInstanceDao taskInstanceDao;
    private final PermissionService permissionService;

    public WorkflowWorkbenchService(ModuleRuntimeContextViewDao runtimeContextViewDao, ProjectDao projectDao,
            TaskInstanceDao taskInstanceDao, PermissionService permissionService) {
        this.runtimeContextViewDao = runtimeContextViewDao;
        this.projectDao = projectDao;
        this.taskInstanceDao = taskInstanceDao;
        this.permissionService = permissionService;
    }

    public List<WorkflowWorkbenchItemResponse> listItems(AuthenticatedUser user) {
        Long userId = user == null ? null : user.userId();
        return runtimeContextViewDao.selectAll().stream()
                .filter(context -> userId != null && permissionService.canAccessProject(userId, context.getProjectId()))
                .map(context -> toItem(userId, context))
                .sorted(Comparator
                        .comparing(WorkflowWorkbenchItemResponse::lastTransitionTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WorkflowWorkbenchItemResponse::moduleInstanceId, Comparator.reverseOrder()))
                .toList();
    }

    private WorkflowWorkbenchItemResponse toItem(Long userId, ModuleRuntimeContextView context) {
        Project project = projectDao.selectById(context.getProjectId());
        boolean todo = taskInstanceDao.selectOpenByModuleInstanceId(context.getModuleInstanceId()).stream()
                .anyMatch(task -> userId.equals(task.getAssigneeUserId())
                        || permissionService.canOperateModuleNode(userId, context.getModuleInstanceId()));
        return new WorkflowWorkbenchItemResponse(
                context.getModuleInstanceId(),
                context.getProjectId(),
                project == null ? null : project.getProjectCode(),
                project == null ? null : project.getProjectName(),
                context.getModuleType(),
                project == null ? null : project.getLifecycleStage(),
                context.getWorkflowDefinitionId(),
                context.getCurrentState(),
                context.getCurrentNodeId(),
                context.getCurrentNodeName(),
                context.getCurrentCandidateRoleCode(),
                context.getCurrentSeq(),
                context.getCurrentRoundNo(),
                context.getLastTransitionTime(),
                context.getFinishedAt() != null,
                todo,
                permissionService.canOperateModuleNode(userId, context.getModuleInstanceId()));
    }
}
