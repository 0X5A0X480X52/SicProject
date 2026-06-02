package com.amatrix.sicprojectis_backend.project.dao;

import com.amatrix.sicprojectis_backend.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectDao {
    int insert(Project entity);

    Project selectById(@Param("projectId") Long projectId);

    List<Project> selectAll();

    int updateById(Project entity);

    int deleteById(@Param("projectId") Long projectId);
}
