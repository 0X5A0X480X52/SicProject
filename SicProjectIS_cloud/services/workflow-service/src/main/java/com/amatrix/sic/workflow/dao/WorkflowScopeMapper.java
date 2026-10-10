package com.amatrix.sic.workflow.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.ExpertGrantScope;
import com.amatrix.sic.workflow.entity.WorkflowTaskScope;

@Mapper
public interface WorkflowScopeMapper {
    List<WorkflowTaskScope> openTasks(@Param("ids") List<Long> ids);
    List<Long> expertModules(@Param("userId") Long userId, @Param("ids") List<Long> ids);
    Long projectId(@Param("moduleId") Long moduleId);
    ExpertGrantScope expertGrant(@Param("userId") Long userId, @Param("moduleId") Long moduleId);
}
