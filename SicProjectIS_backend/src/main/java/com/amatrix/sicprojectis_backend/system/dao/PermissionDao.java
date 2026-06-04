package com.amatrix.sicprojectis_backend.system.dao;

import com.amatrix.sicprojectis_backend.system.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionDao {
    int insert(Permission entity);

    Permission selectById(@Param("permissionId") Long permissionId);

    Permission selectByCode(@Param("permissionCode") String permissionCode);

    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    List<Permission> selectAll();

    int updateById(Permission entity);

    int deleteById(@Param("permissionId") Long permissionId);
}
