package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.MaterialType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialTypeDao {
    int insert(MaterialType entity);

    MaterialType selectById(@Param("materialTypeId") Long materialTypeId);

    List<MaterialType> selectAll();

    int updateById(MaterialType entity);

    int deleteById(@Param("materialTypeId") Long materialTypeId);
}
