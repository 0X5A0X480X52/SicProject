package com.amatrix.sicprojectis_backend.system.dao;

import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserRoleDetailViewDao {
    List<UserRoleDetailView> selectByUserId(@Param("userId") Long userId);

    List<UserRoleDetailView> selectAll();
}
