package com.amatrix.sicprojectis_backend.dao;

import com.amatrix.sicprojectis_backend.entity.StateRecordContextView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StateRecordContextViewDao {
    List<StateRecordContextView> selectByStateRecordId(@Param("stateRecordId") Long stateRecordId);

    List<StateRecordContextView> selectAll();
}
