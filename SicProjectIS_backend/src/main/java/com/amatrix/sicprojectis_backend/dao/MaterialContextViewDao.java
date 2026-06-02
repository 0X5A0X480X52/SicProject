package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.MaterialContextView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialContextViewDao {
    MaterialContextView selectByMaterialVersionId(@Param("materialVersionId") Long materialVersionId);

    List<MaterialContextView> selectAll();
}
