package com.amatrix.sicprojectis_backend.system.dao;

import com.amatrix.sicprojectis_backend.system.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminOperationLogDao {
    int insert(AdminOperationLog entity);

    AdminOperationLog selectById(@Param("adminOperationLogId") Long adminOperationLogId);

    List<AdminOperationLog> selectAll();

    int updateById(AdminOperationLog entity);

    int deleteById(@Param("adminOperationLogId") Long adminOperationLogId);
}
