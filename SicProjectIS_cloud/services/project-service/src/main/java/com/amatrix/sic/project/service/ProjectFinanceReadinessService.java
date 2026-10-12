package com.amatrix.sic.project.service;

import java.util.Map;
import com.amatrix.sic.project.dao.ProjectAuthorizationMapper;
import com.amatrix.sic.project.service.directory.ProjectUserDirectory;
import org.springframework.stereotype.Service;

/**
 * 按项目、模块、轮次、节点取财务授权候选人，统计启用且具 FINANCE_ADMIN 角色的人数，返回财务办理是否就绪及提示。
 * 全局财务角色本身不等于项目办理授权；候选人需同时拥有匹配范围的授权和 IAM 当前有效身份。
 */
@Service
public class ProjectFinanceReadinessService {
    private final ProjectAuthorizationMapper grants;
    private final ProjectUserDirectory users;
    public ProjectFinanceReadinessService(ProjectAuthorizationMapper grants, ProjectUserDirectory users) {
        this.grants=grants; this.users=users;
    }
    /**
     * 先筛选有效且范围匹配的财务授权，再从 IAM 获取候选人的当前角色与启用状态。
     * 这里只报告配置情况，不推进流程或自动添加授权。
     */
    public Map<String,Object> readiness(Long project, String module, Integer round, String node) {
        var ids=grants.financeCandidates(project,module,round,node);
        long count=ids.isEmpty()?0:users.fetchAll(ids).stream()
                .filter(user -> user.enabled() && user.roleCodes().contains("FINANCE_ADMIN")).count();
        return Map.of("ready",count>0,"handlerCount",count,"status",count>0?"READY":"WAITING_CONFIGURATION",
                "message",count>0?"财务办理人已配置":"尚未配置有效的项目财务办理人，请联系系统管理员或科研管理员分配项目财务授权");
    }
}
