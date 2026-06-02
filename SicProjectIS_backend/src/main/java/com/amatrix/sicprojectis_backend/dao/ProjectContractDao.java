package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.ProjectContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectContractDao {
    int insert(ProjectContract entity);

    ProjectContract selectById(@Param("contractId") Long contractId);

    List<ProjectContract> selectAll();

    int updateById(ProjectContract entity);

    int deleteById(@Param("contractId") Long contractId);
}
