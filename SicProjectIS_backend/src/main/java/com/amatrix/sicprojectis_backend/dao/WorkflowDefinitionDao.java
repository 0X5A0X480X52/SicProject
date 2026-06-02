package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.WorkflowDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowDefinitionDao {
    int insert(WorkflowDefinition entity);

    WorkflowDefinition selectById(@Param("workflowDefinitionId") Long workflowDefinitionId);

    List<WorkflowDefinition> selectAll();

    int updateById(WorkflowDefinition entity);

    int deleteById(@Param("workflowDefinitionId") Long workflowDefinitionId);
}
