package com.amatrix.sicprojectis_backend.project.dao;

import com.amatrix.sicprojectis_backend.project.entity.ProjectAcceptance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectAcceptanceDao {
    int insert(ProjectAcceptance entity);

    ProjectAcceptance selectById(@Param("acceptanceId") Long acceptanceId);

    List<ProjectAcceptance> selectAll();

    int updateById(ProjectAcceptance entity);

    int deleteById(@Param("acceptanceId") Long acceptanceId);
}
