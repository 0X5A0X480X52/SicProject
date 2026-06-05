package com.amatrix.sicprojectis_backend.workflow.dao;

import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowDefinitionDao {
    int insert(WorkflowDefinition entity);

    WorkflowDefinition selectById(@Param("workflowDefinitionId") Long workflowDefinitionId);

    WorkflowDefinition selectLatestActiveByModuleType(@Param("moduleType") String moduleType);

    List<WorkflowDefinition> selectAll();

    int updateById(WorkflowDefinition entity);

    int deleteById(@Param("workflowDefinitionId") Long workflowDefinitionId);
}
