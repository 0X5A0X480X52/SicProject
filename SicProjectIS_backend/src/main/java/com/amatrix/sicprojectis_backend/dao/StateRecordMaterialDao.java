package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.StateRecordMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StateRecordMaterialDao {
    int insert(StateRecordMaterial entity);

    StateRecordMaterial selectById(@Param("recordMaterialId") Long recordMaterialId);

    List<StateRecordMaterial> selectAll();

    int updateById(StateRecordMaterial entity);

    int deleteById(@Param("recordMaterialId") Long recordMaterialId);
}
