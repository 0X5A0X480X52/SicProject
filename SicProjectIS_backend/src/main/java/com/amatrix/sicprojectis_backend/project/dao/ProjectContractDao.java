package com.amatrix.sicprojectis_backend.project.dao;

import com.amatrix.sicprojectis_backend.project.entity.ProjectContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectContractDao {
    int insert(ProjectContract entity);

    ProjectContract selectById(@Param("contractId") Long contractId);

    ProjectContract selectByProjectId(@Param("projectId") Long projectId);

    List<ProjectContract> selectAll();

    int updateById(ProjectContract entity);

    int deleteById(@Param("contractId") Long contractId);
}
