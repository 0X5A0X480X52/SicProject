package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionDao {
    int insert(Permission entity);

    Permission selectById(@Param("permissionId") Long permissionId);

    List<Permission> selectAll();

    int updateById(Permission entity);

    int deleteById(@Param("permissionId") Long permissionId);
}
