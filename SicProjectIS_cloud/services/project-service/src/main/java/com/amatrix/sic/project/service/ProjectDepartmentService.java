package com.amatrix.sic.project.service;

import java.util.Map;
import java.util.UUID;
import com.amatrix.sic.contracts.*;
import com.amatrix.sic.project.client.IamDirectoryClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;
import com.amatrix.sic.project.dao.ProjectAuthorizationMapper;
import com.amatrix.sic.project.dao.ProjectAuditMapper;
import com.amatrix.sic.messaging.OutboxService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectDepartmentService {
    private final IamDirectoryClient iam;
    private final InternalTokenProvider tokens;
    private final ProjectAuthorizationMapper projects;
    private final ProjectAuditMapper audit;
    private final OutboxService outbox;
    private final TransactionTemplate transactions;

    public ProjectDepartmentService(IamDirectoryClient iam, InternalTokenProvider tokens,
            ProjectAuthorizationMapper projects, ProjectAuditMapper audit, OutboxService outbox,
            TransactionTemplate transactions) {
        this.iam=iam; this.tokens=tokens; this.projects=projects; this.audit=audit;
        this.outbox=outbox; this.transactions=transactions;
    }

    public DepartmentSummary requireEnabled(Long deptId) {
        if (deptId == null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "请先联系管理员分配部门，再发起项目申请");
        ApiResponse<DepartmentSummary> response;
        try { response = iam.department(tokens.authorization(), deptId); }
        catch (feign.FeignException.NotFound missing) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所属部门不存在，请联系管理员处理");
        }
        if (!response.success() || response.data() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "部门信息暂不可用，请稍后重试");
        if (!response.data().enabled()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "所属部门已停用，请联系管理员处理");
        return response.data();
    }

    /** IAM lookup precedes the local transaction; project ownership never follows user transfers. */
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasRole('SCIENCE_ADMIN')")
    public Map<String,Object> assignMissing(Long projectId, ActorContext actor, Long deptId, String reason) {
        if (!actor.hasRole("SYSTEM_ADMIN") && !actor.hasRole("SCIENCE_ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有全局管理员可以补充项目归属部门");
        if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写归属部门补充原因");
        DepartmentSummary dept = requireEnabled(deptId);
        return transactions.execute(status -> {
            var project = projects.lockProject(projectId);
            if (project == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
            if (project.deptId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "项目已有归属部门，不能通过补充入口变更");
            projects.assignDepartment(projectId, dept.deptId(), dept.deptName());
            audit.insert(Map.of("id", UUID.randomUUID().toString(), "action", "ASSIGN_PROJECT_DEPARTMENT",
                    "projectId", projectId, "projectName", project.name(), "operatorId", actor.userId(),
                    "operatorName", actor.username(), "after", dept.deptId().toString(), "remark", reason.trim()));
            var payload = new java.util.LinkedHashMap<String,Object>();
            payload.put("projectId", projectId); payload.put("deptId", dept.deptId());
            payload.put("ownerUserId", project.ownerUserId()); payload.put("ownerName", project.ownerName());
            payload.put("projectVersion", projects.version(projectId));
            outbox.enqueue("project.authorization", EventEnvelope.create("PROJECT_DEPARTMENT_ASSIGNED", "PROJECT",
                    projectId.toString(), null, null, actor.userId(), payload));
            return Map.<String,Object>of("projectId", projectId, "deptId", dept.deptId(), "deptName", dept.deptName());
        });
    }
}
