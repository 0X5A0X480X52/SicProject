package com.amatrix.sicprojectis_backend.workflow.dao;

import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeConfigView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowNodeConfigViewDao {
    WorkflowNodeConfigView selectByWorkflowNodeId(@Param("workflowNodeId") Long workflowNodeId);

    List<WorkflowNodeConfigView> selectAll();
}
