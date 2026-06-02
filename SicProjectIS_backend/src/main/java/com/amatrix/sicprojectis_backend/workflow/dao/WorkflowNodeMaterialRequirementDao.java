package com.amatrix.sicprojectis_backend.workflow.dao;

import com.amatrix.sicprojectis_backend.workflow.entity.WorkflowNodeMaterialRequirement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowNodeMaterialRequirementDao {
    int insert(WorkflowNodeMaterialRequirement entity);

    WorkflowNodeMaterialRequirement selectById(@Param("requirementId") Long requirementId);

    List<WorkflowNodeMaterialRequirement> selectAll();

    int updateById(WorkflowNodeMaterialRequirement entity);

    int deleteById(@Param("requirementId") Long requirementId);
}
