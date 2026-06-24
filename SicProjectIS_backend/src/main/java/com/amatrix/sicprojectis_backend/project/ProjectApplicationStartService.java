package com.amatrix.sicprojectis_backend.project;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.project.dao.ProjectApplicationDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectDao;
import com.amatrix.sicprojectis_backend.project.dao.ProjectMemberDao;
import com.amatrix.sicprojectis_backend.project.dto.StartProjectApplicationRequest;
import com.amatrix.sicprojectis_backend.project.dto.StartProjectApplicationResponse;
import com.amatrix.sicprojectis_backend.project.entity.Project;
import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import com.amatrix.sicprojectis_backend.project.entity.ProjectMember;
import com.amatrix.sicprojectis_backend.runtime.dao.ModuleStateRecordDao;
import com.amatrix.sicprojectis_backend.runtime.dao.ProjectModuleInstanceDao;
import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import com.amatrix.sicprojectis_backend.runtime.statemachine.ModuleStateChangedEvent;
import com.amatrix.sicprojectis_backend.runtime.statemachine.dto.StateTransitionResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.task.dao.TaskInstanceDao;
import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;
import com.amatrix.sicprojectis_backend.workflow.FlowableBpmnDefinitionParser;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult;
import com.amatrix.sicprojectis_backend.workflow.WorkflowBpmnParseResult.NodeConfig;
import com.amatrix.sicprojectis_backend.workflow.dao.WorkflowDefinitionDao;
import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowDefinition;

@Service
public class ProjectApplicationStartService {
    private static final String MODULE_TYPE_APPLICATION = "APPLICATION";
    private static final String START_NODE_ID = "StartEvent";
    private static final String SUBMIT_APPLICATION_NODE_ID = "SubmitApplicationTask";
    private static final String MEMBER_ROLE_LEADER = "LEADER";

    private final ProjectDao projectDao;
    private final ProjectMemberDao projectMemberDao;
    private final ProjectApplicationDao projectApplicationDao;
    private final ProjectModuleInstanceDao moduleDao;
    private final ModuleStateRecordDao stateRecordDao;
    private final TaskInstanceDao taskDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final FlowableBpmnDefinitionParser parser;
    private final AppUserDao appUserDao;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectApplicationStartService(
            ProjectDao projectDao,
            ProjectMemberDao projectMemberDao,
            ProjectApplicationDao projectApplicationDao,
            ProjectModuleInstanceDao moduleDao,
            ModuleStateRecordDao stateRecordDao,
            TaskInstanceDao taskDao,
            WorkflowDefinitionDao workflowDefinitionDao,
            FlowableBpmnDefinitionParser parser,
            AppUserDao appUserDao,
            ApplicationEventPublisher eventPublisher) {
        this.projectDao = projectDao;
        this.projectMemberDao = projectMemberDao;
        this.projectApplicationDao = projectApplicationDao;
        this.moduleDao = moduleDao;
        this.stateRecordDao = stateRecordDao;
        this.taskDao = taskDao;
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.parser = parser;
        this.appUserDao = appUserDao;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public StartProjectApplicationResponse start(AuthenticatedUser user, StartProjectApplicationRequest request) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required");
        }
        if (request == null) {
            throw badRequest("Request body is required");
        }
        String projectName = required(request.projectName(), "Project name is required");
        AppUser applicant = appUserDao.selectById(user.userId());
        if (applicant == null || !Boolean.TRUE.equals(applicant.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Applicant account is not available");
        }
        WorkflowDefinition definition = workflowDefinitionDao.selectLatestActiveByModuleType(MODULE_TYPE_APPLICATION);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active APPLICATION workflow definition not found");
        }
        WorkflowBpmnParseResult model = parser.parse(definition.getBpmnXml());
        NodeConfig startNode = findNode(model, START_NODE_ID);
        NodeConfig submitNode = findNode(model, SUBMIT_APPLICATION_NODE_ID);
        LocalDateTime now = LocalDateTime.now();

        Project project = new Project();
        project.setProjectCode(firstNonBlank(request.projectCode(), generatedProjectCode(now, user.userId())));
        project.setProjectName(projectName);
        project.setLeaderUserId(user.userId());
        project.setDeptId(applicant.getDeptId());
        project.setProjectType(blankToNull(request.projectType()));
        project.setProjectLevel(blankToNull(request.projectLevel()));
        project.setApprovedAmount(request.approvedAmount());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setLifecycleStage(MODULE_TYPE_APPLICATION);
        projectDao.insert(project);

        ProjectMember leader = new ProjectMember();
        leader.setProjectId(project.getProjectId());
        leader.setUserId(user.userId());
        leader.setMemberRole(MEMBER_ROLE_LEADER);
        leader.setResponsibility("项目申请负责人");
        leader.setJoinedAt(now);
        projectMemberDao.insert(leader);

        ProjectApplication application = new ProjectApplication();
        application.setProjectId(project.getProjectId());
        application.setApplicationTitle(firstNonBlank(request.applicationTitle(), projectName));
        application.setIsLimitedProject(Boolean.TRUE.equals(request.isLimitedProject()));
        application.setApplicationSummary(blankToNull(request.applicationSummary()));
        projectApplicationDao.insert(application);

        ProjectModuleInstance module = new ProjectModuleInstance();
        module.setProjectId(project.getProjectId());
        module.setModuleType(MODULE_TYPE_APPLICATION);
        module.setWorkflowDefinitionId(definition.getWorkflowDefinitionId());
        module.setStartedAt(now);
        moduleDao.insert(module);

        ModuleStateRecord record = new ModuleStateRecord();
        record.setModuleInstanceId(module.getModuleInstanceId());
        record.setSeq(1);
        record.setRoundNo(1);
        record.setEventType("APPLICATION_SELF_STARTED");
        record.setFromState(startNode.stateCode());
        record.setToState(submitNode.stateCode());
        record.setFromNodeId(startNode.nodeId());
        record.setToNodeId(submitNode.nodeId());
        record.setResult("STARTED");
        record.setSummary("Project application started by applicant");
        record.setCreatedAt(now);
        stateRecordDao.insert(record);

        TaskInstance task = new TaskInstance();
        task.setModuleInstanceId(module.getModuleInstanceId());
        task.setNodeId(submitNode.nodeId());
        task.setStateCode(submitNode.stateCode());
        task.setCandidateRoleCode(submitNode.candidateRoleCode());
        task.setTaskStatus("OPEN");
        task.setRoundNo(1);
        task.setCreatedAt(now);
        taskDao.insert(task);

        eventPublisher.publishEvent(new ModuleStateChangedEvent(
                project.getProjectId(),
                module.getModuleInstanceId(),
                module.getModuleType(),
                record.getFromState(),
                record.getToState(),
                record.getSeq(),
                record.getEventType(),
                record.getCreatedAt()));

        return new StartProjectApplicationResponse(
                project.getProjectId(),
                module.getModuleInstanceId(),
                new StateTransitionResponse(record, submitNode.nodeId(), submitNode.stateCode(), false));
    }

    private NodeConfig findNode(WorkflowBpmnParseResult model, String nodeId) {
        return model.nodes().stream()
                .filter(node -> Objects.equals(node.nodeId(), nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow node not found: " + nodeId));
    }

    private String generatedProjectCode(LocalDateTime now, Long userId) {
        return "APP-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + userId;
    }

    private String required(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw badRequest(message);
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        String normalized = blankToNull(first);
        return normalized == null ? second : normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
