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

    int updateById(ProjectMember entity);

    int deleteById(@Param("projectMemberId") Long projectMemberId);
}
