package com.amatrix.sicprojectis_backend.workflow.dao;

import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeDocumentConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowNodeDocumentConfigDao {
    int insert(WorkflowNodeDocumentConfig entity);

    WorkflowNodeDocumentConfig selectById(@Param("documentConfigId") Long documentConfigId);

    List<WorkflowNodeDocumentConfig> selectByWorkflowNodeId(@Param("workflowNodeId") Long workflowNodeId);

    List<WorkflowNodeDocumentConfig> selectAll();

    int updateById(WorkflowNodeDocumentConfig entity);

    int deleteByWorkflowNodeId(@Param("workflowNodeId") Long workflowNodeId);

    int deleteById(@Param("documentConfigId") Long documentConfigId);
}
