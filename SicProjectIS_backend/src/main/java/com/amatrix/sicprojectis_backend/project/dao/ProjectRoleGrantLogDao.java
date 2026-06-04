package com.amatrix.sicprojectis_backend.project.dao;

import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrantLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectRoleGrantLogDao {
    int insert(ProjectRoleGrantLog entity);

    ProjectRoleGrantLog selectById(@Param("grantLogId") Long grantLogId);

    List<ProjectRoleGrantLog> selectAll();

    List<ProjectRoleGrantLog> selectByGrantId(@Param("projectRoleGrantId") Long projectRoleGrantId);

    int updateById(ProjectRoleGrantLog entity);

    int deleteById(@Param("grantLogId") Long grantLogId);
}
