package com.amatrix.sic.workflow.service.runtime;

import java.util.*;
import java.time.LocalDateTime;
import com.amatrix.sic.contracts.*;
import com.amatrix.sic.workflow.dao.WorkflowRuntimeMapper;
import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;
import com.amatrix.sic.workflow.client.ProjectAccessClient;
import com.amatrix.sic.workflow.client.auth.WorkflowInternalTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 工作台视图查询：按可操作模块或 Project 批量访问判定过滤候选行，游标式分页并标注待办与可操作性。
 * 候选模块采用游标分批读取，经过当前权限筛选后形成页面；hasMore 表示还有可见记录，不提供未经筛选的总数。
 */
@Service
public class WorkflowWorkbenchQueryService {
    private final WorkflowRuntimeMapper mapper;
    private final WorkflowOperationPolicy operations;
    private final ProjectAccessClient projects;
    private final WorkflowInternalTokenProvider tokens;
    public WorkflowWorkbenchQueryService(WorkflowRuntimeMapper mapper, WorkflowOperationPolicy operations,
            ProjectAccessClient projects, WorkflowInternalTokenProvider tokens) {
        this.mapper=mapper; this.operations=operations; this.projects=projects; this.tokens=tokens;
    }
    /**
     * 按更新时间和模块 ID 分批读候选，再检查当前可读或可办理资格后分页；
     * 跳过数量按授权后的记录计算，避免历史页泄露不可见项目。
     */
    @PreAuthorize("isAuthenticated()")
    public Map<String,Object> query(ActorContext actor,String view,String module,String keyword,int page,int size) {
        if (!Set.of("TODO","OPERATED","VISIBLE","FINISHED").contains(view)
                || page<1 || page>1000 || size<1 || size>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid workbench query");
        if (module != null && !Set.of("APPLICATION","CONTRACT","ACCEPTANCE").contains(module))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid module type");
        String search=keyword==null || keyword.isBlank()?null:keyword.trim();
        if(search!=null && search.length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Keyword too long");
        int skip=(page-1)*size;
        List<Map<String,Object>> selected=new ArrayList<>();
        LocalDateTime before=null; Long beforeId=null;
        while(selected.size()<=size) {
            var rows=mapper.workbenchCandidates(view,actor.userId(),module,search,before,beforeId);
            if(rows.isEmpty())break;
            var ids=rows.stream().map(row -> (Long)row.get("moduleInstanceId")).toList();
            var operable=operations.operableModuleIds(actor,ids);
            Set<Long> readable=new HashSet<>();
            if("TODO".equals(view))readable.addAll(operable);
            else {
                var targets=rows.stream().map(row -> new ProjectOperationTarget(String.valueOf(row.get("moduleInstanceId")),
                        (Long)row.get("projectId"),"VIEW_WORKFLOW",null,null,null)).toList();
                var response=projects.decideBatch(tokens.authorization(),new ProjectAccessBatchRequest(actor,targets));
                if(response==null || !response.success() || response.data()==null)
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"项目权限暂不可用");
                for(var decision:response.data())if(decision.allowed())
                    targets.stream().filter(target -> Objects.equals(target.decisionId(),decision.decisionId())
                        && Objects.equals(target.projectId(),decision.projectId()))
                        .forEach(target -> readable.add(Long.valueOf(target.decisionId())));
            }
            for(var row:rows) {
                Long id=(Long)row.get("moduleInstanceId");
                if(!readable.contains(id))continue;
                if(skip>0){skip--;continue;}
                row.put("todo",operable.contains(id));row.put("canOperate",operable.contains(id));
                selected.add(row);
                if(selected.size()>size)break;
            }
            var last=rows.getLast(); before=(LocalDateTime)last.get("lastTransitionTime");
            beforeId=(Long)last.get("moduleInstanceId");
            if(rows.size()<100)break;
        }
        boolean more=selected.size()>size;
        return Map.of("items",more?selected.subList(0,size):selected,"page",page,"pageSize",size,"hasMore",more);
    }
}
