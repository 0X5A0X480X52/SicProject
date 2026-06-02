package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserRoleDao {
    int insert(UserRole entity);

    UserRole selectById(@Param("userRoleId") Long userRoleId);

    List<UserRole> selectAll();

    int updateById(UserRole entity);

    int deleteById(@Param("userRoleId") Long userRoleId);
}
