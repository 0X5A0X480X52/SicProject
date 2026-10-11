package com.amatrix.sic.project.service;

import java.util.Map;
import com.amatrix.sic.project.dao.ProjectAuthorizationMapper;
import com.amatrix.sic.project.service.directory.ProjectUserDirectory;
import org.springframework.stereotype.Service;

@Service
public class ProjectFinanceReadinessService {
    private final ProjectAuthorizationMapper grants;
    private final ProjectUserDirectory users;
    public ProjectFinanceReadinessService(ProjectAuthorizationMapper grants, ProjectUserDirectory users) {
        this.grants=grants; this.users=users;
    }
    public Map<String,Object> readiness(Long project, String module, Integer round, String node) {
        var ids=grants.financeCandidates(project,module,round,node);
        long count=ids.isEmpty()?0:users.fetchAll(ids).stream()
                .filter(user -> user.enabled() && user.roleCodes().contains("FINANCE_ADMIN")).count();
        return Map.of("ready",count>0,"handlerCount",count,"status",count>0?"READY":"WAITING_CONFIGURATION",
                "message",count>0?"财务办理人已配置":"尚未配置有效的项目财务办理人，请联系系统管理员或科研管理员分配项目财务授权");
    }
}
