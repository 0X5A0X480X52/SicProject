package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.ModuleRuntimeContextView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModuleRuntimeContextViewDao {
    ModuleRuntimeContextView selectByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);

    List<ModuleRuntimeContextView> selectAll();
}
