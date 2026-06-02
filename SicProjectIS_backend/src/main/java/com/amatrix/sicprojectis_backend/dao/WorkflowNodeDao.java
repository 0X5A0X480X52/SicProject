package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.WorkflowNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowNodeDao {
    int insert(WorkflowNode entity);

    WorkflowNode selectById(@Param("workflowNodeId") Long workflowNodeId);

    List<WorkflowNode> selectAll();

    int updateById(WorkflowNode entity);

    int deleteById(@Param("workflowNodeId") Long workflowNodeId);
}
