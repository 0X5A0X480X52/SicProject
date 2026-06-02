package com.amatrix.sicprojectis_backend.system.dao;

import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppUserDao {
    int insert(AppUser entity);

    AppUser selectById(@Param("userId") Long userId);

    List<AppUser> selectAll();

    int updateById(AppUser entity);

    int deleteById(@Param("userId") Long userId);
}
