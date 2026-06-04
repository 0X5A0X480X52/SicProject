package com.amatrix.sicprojectis_backend.system.dao;

import com.amatrix.sicprojectis_backend.system.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserRoleDao {
    int insert(UserRole entity);

    UserRole selectById(@Param("userRoleId") Long userRoleId);

    List<UserRole> selectAll();

    List<UserRole> selectByUserId(@Param("userId") Long userId);

    UserRole selectByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int updateById(UserRole entity);

    int deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteById(@Param("userRoleId") Long userRoleId);
}
