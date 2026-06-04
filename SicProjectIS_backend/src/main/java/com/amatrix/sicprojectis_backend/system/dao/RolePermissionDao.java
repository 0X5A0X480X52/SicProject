package com.amatrix.sicprojectis_backend.system.dao;

import com.amatrix.sicprojectis_backend.system.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionDao {
    int insert(RolePermission entity);

    RolePermission selectById(@Param("rolePermissionId") Long rolePermissionId);

    List<RolePermission> selectAll();

    List<RolePermission> selectByRoleId(@Param("roleId") Long roleId);

    RolePermission selectByRoleIdAndPermissionId(
            @Param("roleId") Long roleId,
            @Param("permissionId") Long permissionId);

    int updateById(RolePermission entity);

    int deleteByRoleIdAndPermissionId(
            @Param("roleId") Long roleId,
            @Param("permissionId") Long permissionId);

    int deleteById(@Param("rolePermissionId") Long rolePermissionId);
}
