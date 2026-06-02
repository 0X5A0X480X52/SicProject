package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleDao {
    int insert(Role entity);

    Role selectById(@Param("roleId") Long roleId);

    List<Role> selectAll();

    int updateById(Role entity);

    int deleteById(@Param("roleId") Long roleId);
}
