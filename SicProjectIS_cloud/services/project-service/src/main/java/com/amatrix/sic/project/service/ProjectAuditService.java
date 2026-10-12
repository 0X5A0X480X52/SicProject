package com.amatrix.sic.project.service;

import com.amatrix.sic.project.client.IamDirectoryClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amatrix.sic.project.dao.ProjectAuditMapper;
import com.amatrix.sic.project.dao.ProjectAuthorizationMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.amatrix.sic.contracts.IamOverviewStats;

/**
 * 调用 IAM 聚合用户/角色统计，结合本地授权统计生成概览；按条件查询管理日志并执行概览/审计权限校验。
 */
@Service
public class ProjectAuditService {
    private final ProjectAuditMapper mapper;
    private final ProjectAuthorizationMapper grants;
    private final IamDirectoryClient identities;
    private final InternalTokenProvider tokens;
    public ProjectAuditService(ProjectAuditMapper mapper,ProjectAuthorizationMapper grants, IamDirectoryClient identities, InternalTokenProvider tokens) {
        this.mapper = mapper;this.grants = grants;
        this.identities = identities;
        this.tokens = tokens;
    }

    @PreAuthorize("hasAuthority('PERM_ADMIN_OVERVIEW')")
    public Map<String, Object> overview() {
        IamOverviewStats iam = identities.overviewStats(tokens.authorization()).data();
        if (iam == null) throw new IllegalStateException("IAM overview statistics are unavailable");
        long grants=this.mapper.activeGrants(),logs=this.mapper.logCount();
        List<Map<String,Object>> grantCounts=this.grants.grantCounts();
        List<Map<String,Object>> roleCounts = iam.roleCounts().stream()
                .map(item -> linked("key",item.key(),"label",item.label(),"count",item.count())).toList();
        return linked("totalUsers", iam.totalUsers(), "enabledUsers", iam.enabledUsers(), "totalRoles", iam.totalRoles(),
                "totalPermissions", iam.totalPermissions(), "activeProjectGrants", grants, "auditLogCount", logs,
                "grantTypeCounts", grantCounts, "roleCounts", roleCounts);
    }

    @PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
    public Map<String, Object> logs(String keyword, String actionType, String scopeType, Long projectId,
            Long operatorUserId, LocalDate dateFrom, LocalDate dateTo) {
        var filter=linked("keyword",text(keyword)?"%"+keyword.trim()+"%":null,
                "actionType",text(actionType)?actionType:null,"scopeType",text(scopeType)?scopeType:null,
                "projectId",projectId,"operatorId",operatorUserId,"from",dateFrom==null?null:dateFrom.atStartOfDay(),
                "to",dateTo==null?null:dateTo.plusDays(1).atStartOfDay());
        List<Map<String,Object>> rows=mapper.logs(filter);
        rows.forEach(row -> {
            row.put("operatorUser",summary((Long)row.remove("operatorId"),(String)row.remove("operatorName")));
            row.put("targetUser",summary((Long)row.remove("targetId"),(String)row.remove("targetName")));
        });
        return linked("logs", rows);
    }

    private static Map<String,Object> summary(Long id,String name) { return id==null?null:linked("userId",id,"username",name,"realName",name); }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static Map<String,Object> linked(Object...pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put(String.valueOf(pairs[i]),pairs[i+1]);return m;}
}
