package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.ModuleStateRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModuleStateRecordDao {
    int insert(ModuleStateRecord entity);

    ModuleStateRecord selectById(@Param("stateRecordId") Long stateRecordId);

    List<ModuleStateRecord> selectAll();

    int updateById(ModuleStateRecord entity);

    int deleteById(@Param("stateRecordId") Long stateRecordId);
}
