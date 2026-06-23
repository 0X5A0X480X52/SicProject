package com.amatrix.sicprojectis_backend.runtime.dao;

import com.amatrix.sicprojectis_backend.runtime.entity.ProjectModuleInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectModuleInstanceDao {
    int insert(ProjectModuleInstance entity);

    ProjectModuleInstance selectById(@Param("moduleInstanceId") Long moduleInstanceId);

    ProjectModuleInstance selectByIdForUpdate(@Param("moduleInstanceId") Long moduleInstanceId);

    ProjectModuleInstance selectByProjectIdAndModuleType(@Param("projectId") Long projectId, @Param("moduleType") String moduleType);

    List<ProjectModuleInstance> selectAll();

    int updateById(ProjectModuleInstance entity);

    int deleteById(@Param("moduleInstanceId") Long moduleInstanceId);
}
