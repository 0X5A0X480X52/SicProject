package com.amatrix.sicprojectis_backend.project.dao;

import com.amatrix.sicprojectis_backend.project.entity.ProjectApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectApplicationDao {
    int insert(ProjectApplication entity);

    ProjectApplication selectById(@Param("applicationId") Long applicationId);

    List<ProjectApplication> selectAll();

    int updateById(ProjectApplication entity);

    int deleteById(@Param("applicationId") Long applicationId);
}
