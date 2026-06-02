package com.amatrix.sicprojectis_backend.task.dao;

import com.amatrix.sicprojectis_backend.task.entity.TaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskInstanceDao {
    int insert(TaskInstance entity);

    TaskInstance selectById(@Param("taskInstanceId") Long taskInstanceId);

    List<TaskInstance> selectAll();

    int updateById(TaskInstance entity);

    int deleteById(@Param("taskInstanceId") Long taskInstanceId);
}
