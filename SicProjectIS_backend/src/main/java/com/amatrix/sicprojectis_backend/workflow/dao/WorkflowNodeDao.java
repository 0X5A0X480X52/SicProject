package com.amatrix.sicprojectis_backend.workflow.dao;

import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowNodeDao {
    int insert(WorkflowNode entity);

    WorkflowNode selectById(@Param("workflowNodeId") Long workflowNodeId);

    List<WorkflowNode> selectByWorkflowDefinitionId(@Param("workflowDefinitionId") Long workflowDefinitionId);

    List<WorkflowNode> selectAll();

    int updateById(WorkflowNode entity);

    int deleteByWorkflowDefinitionId(@Param("workflowDefinitionId") Long workflowDefinitionId);

    int deleteById(@Param("workflowNodeId") Long workflowNodeId);
}
