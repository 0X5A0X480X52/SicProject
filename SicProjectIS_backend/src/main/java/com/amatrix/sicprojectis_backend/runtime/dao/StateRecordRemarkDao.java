package com.amatrix.sicprojectis_backend.runtime.dao;

import com.amatrix.sicprojectis_backend.runtime.entity.StateRecordRemark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StateRecordRemarkDao {
    int insert(StateRecordRemark entity);

    StateRecordRemark selectById(@Param("remarkId") Long remarkId);

    List<StateRecordRemark> selectAll();

    int updateById(StateRecordRemark entity);

    int deleteById(@Param("remarkId") Long remarkId);
}
