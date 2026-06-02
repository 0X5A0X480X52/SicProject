package com.amatrix.sicprojectis_backend.material.dao;

import com.amatrix.sicprojectis_backend.material.entity.MaterialVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialVersionDao {
    int insert(MaterialVersion entity);

    MaterialVersion selectById(@Param("materialVersionId") Long materialVersionId);

    List<MaterialVersion> selectAll();

    int updateById(MaterialVersion entity);

    int deleteById(@Param("materialVersionId") Long materialVersionId);
}
