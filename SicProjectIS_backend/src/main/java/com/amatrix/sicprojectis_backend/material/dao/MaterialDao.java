package com.amatrix.sicprojectis_backend.material.dao;

import com.amatrix.sicprojectis_backend.material.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialDao {
    int insert(Material entity);

    Material selectById(@Param("materialId") Long materialId);

    List<Material> selectAll();

    int updateById(Material entity);

    int deleteById(@Param("materialId") Long materialId);
}
