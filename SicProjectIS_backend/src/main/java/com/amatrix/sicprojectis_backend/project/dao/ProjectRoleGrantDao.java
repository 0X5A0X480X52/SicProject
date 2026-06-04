package com.amatrix.sicprojectis_backend.project.dao;

import com.amatrix.sicprojectis_backend.project.entity.ProjectRoleGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectRoleGrantDao {
    int insert(ProjectRoleGrant entity);

    ProjectRoleGrant selectById(@Param("projectRoleGrantId") Long projectRoleGrantId);

    List<ProjectRoleGrant> selectAll();

    List<ProjectRoleGrant> selectByProjectId(@Param("projectId") Long projectId);

    List<ProjectRoleGrant> selectActiveByProjectId(@Param("projectId") Long projectId);

    List<ProjectRoleGrant> selectActiveByGranteeUserId(@Param("granteeUserId") Long granteeUserId);

    List<ProjectRoleGrant> selectActiveByProjectAndGrantRole(
            @Param("projectId") Long projectId,
            @Param("grantRoleCode") String grantRoleCode);

    List<ProjectRoleGrant> selectActiveForUserAndProject(
            @Param("granteeUserId") Long granteeUserId,
            @Param("projectId") Long projectId);

    List<ProjectRoleGrant> selectMatchingActiveGrant(
            @Param("projectId") Long projectId,
            @Param("moduleType") String moduleType,
            @Param("grantRoleCode") String grantRoleCode,
            @Param("granteeUserId") Long granteeUserId,
            @Param("roundNo") Integer roundNo,
            @Param("taskNodeId") String taskNodeId);

    int updateById(ProjectRoleGrant entity);

    int deleteById(@Param("projectRoleGrantId") Long projectRoleGrantId);
}
