package com.amatrix.sic.workflow.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.ExpertGrantScope;
import com.amatrix.sic.workflow.entity.WorkflowTaskScope;

/**
 * 批量判权 MyBatis 接口：按项目 ID 集合取 OPEN 任务与专家可评审模块、按模块解析所属项目，并按定义分页取关联项目 ID。
 */
@Mapper
public interface WorkflowScopeMapper {
    List<WorkflowTaskScope> openTasks(@Param("ids") List<Long> ids);
    List<Long> expertModules(@Param("userId") Long userId, @Param("ids") List<Long> ids);
    Long projectId(@Param("moduleId") Long moduleId);
    List<Long> definitionProjects(@Param("definitionId") Long definitionId, @Param("afterProjectId") long afterProjectId);
    ExpertGrantScope expertGrant(@Param("userId") Long userId, @Param("moduleId") Long moduleId);
}
