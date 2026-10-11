package com.amatrix.sic.workflow.service.review;

import java.util.*;
import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.workflow.dao.ExpertReviewViewMapper;
import com.amatrix.sic.workflow.policy.WorkflowOperationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExpertPersonalReviewService {
    private final ExpertReviewViewMapper mapper;
    private final WorkflowOperationPolicy operations;
    public ExpertPersonalReviewService(ExpertReviewViewMapper mapper,WorkflowOperationPolicy operations) {
        this.mapper=mapper; this.operations=operations;
    }
    @PreAuthorize("isAuthenticated()")
    public Map<String,Object> list(ActorContext actor,String status,int page,int size) {
        if(page<1 || page>1000 || size<1 || size>100 || (status!=null
                && !Set.of("PENDING","WAITING_REVIEW","SUBMITTED","COMPLETED","EXPIRED").contains(status)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid review query");
        var rows=mapper.mine(actor.userId(),null,status,(page-1)*size,size+1);
        boolean more=rows.size()>size;
        var selected=more?rows.subList(0,size):rows;
        var ids=selected.stream().filter(row -> "PENDING".equals(row.get("status")))
                .map(row -> (Long)row.get("moduleInstanceId")).distinct().toList();
        var operable=operations.operableModuleIds(actor,ids);
        selected.forEach(row -> row.put("canReview","PENDING".equals(row.get("status")) && operable.contains(row.get("moduleInstanceId"))));
        return Map.of("items",selected,"page",page,"pageSize",size,"hasMore",more);
    }
    @PreAuthorize("isAuthenticated()")
    public Map<String,Object> detail(ActorContext actor,Long id) {
        var rows=mapper.mine(actor.userId(),id,null,0,1);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"评审任务不存在或不属于当前账号");
        var row=rows.getFirst();
        row.put("canReview","PENDING".equals(row.get("status"))
                && operations.exactCanOperate(actor,(Long)row.get("moduleInstanceId")));
        row.put("scores",mapper.scores(List.of(id)));
        return row;
    }
}
