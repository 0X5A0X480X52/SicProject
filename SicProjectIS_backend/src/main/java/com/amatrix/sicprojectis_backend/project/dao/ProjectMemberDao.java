package com.amatrix.sicprojectis_backend.project.dao;

import com.amatrix.sicprojectis_backend.project.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMemberDao {
    int insert(ProjectMember entity);

    ProjectMember selectById(@Param("projectMemberId") Long projectMemberId);

    List<ProjectMember> selectAll();

    List<ProjectMember> selectByProjectId(@Param("projectId") Long projectId);

    ProjectMember selectByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

    int countByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

    int updateById(ProjectMember entity);

    int deleteByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

    int deleteById(@Param("projectMemberId") Long projectMemberId);
}
