package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionDao {
    int insert(RolePermission entity);

    RolePermission selectById(@Param("rolePermissionId") Long rolePermissionId);

    List<RolePermission> selectAll();

    int updateById(RolePermission entity);

    int deleteById(@Param("rolePermissionId") Long rolePermissionId);
}
