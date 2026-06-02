package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.WorkflowNodeConfigView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowNodeConfigViewDao {
    WorkflowNodeConfigView selectById(@Param("workflowNodeId") Long workflowNodeId);

    List<WorkflowNodeConfigView> selectAll();
}
