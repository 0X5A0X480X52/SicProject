package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DepartmentDao {
    int insert(Department entity);

    Department selectById(@Param("deptId") Long deptId);

    List<Department> selectAll();

    int updateById(Department entity);

    int deleteById(@Param("deptId") Long deptId);
}
