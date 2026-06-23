package com.amatrix.sicprojectis_backend.runtime.dao;

import com.amatrix.sicprojectis_backend.runtime.entity.ModuleStateRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModuleStateRecordDao {
    int insert(ModuleStateRecord entity);

    ModuleStateRecord selectById(@Param("stateRecordId") Long stateRecordId);

    ModuleStateRecord selectLatestByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);

    List<ModuleStateRecord> selectByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);

    List<ModuleStateRecord> selectAll();

    int updateById(ModuleStateRecord entity);

    int deleteById(@Param("stateRecordId") Long stateRecordId);
}
