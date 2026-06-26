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

    List<TaskInstance> selectOpenByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);

    List<TaskInstance> selectOpenByModuleInstanceIdAndNodeId(@Param("moduleInstanceId") Long moduleInstanceId, @Param("nodeId") String nodeId);

    int countOpenAssignedByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    int countOpenByProjectIdAndCandidateRoleCode(@Param("projectId") Long projectId, @Param("candidateRoleCode") String candidateRoleCode);

    int closeOpenByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);

    int updateById(TaskInstance entity);

    int deleteById(@Param("taskInstanceId") Long taskInstanceId);
}
