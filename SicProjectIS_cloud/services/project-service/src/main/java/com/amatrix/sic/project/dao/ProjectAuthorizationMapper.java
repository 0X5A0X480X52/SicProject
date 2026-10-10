package com.amatrix.sic.project.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.ProjectRecord;

@Mapper
public interface ProjectAuthorizationMapper {
    ProjectRecord project(@Param("id") Long id);
    ProjectRecord lockProject(@Param("id") Long id);
    List<ProjectRecord> manageable(@Param("global") boolean global,@Param("department") boolean department,@Param("deptId") Long deptId);
    List<Map<String,Object>> members(@Param("id") Long id);
    List<Map<String,Object>> grants(@Param("id") Long id,@Param("role") String role);
    List<Map<String,Object>> grantCounts();
    int changeLeader(@Param("id") Long id,@Param("userId") Long userId,@Param("name") String name);
    int demoteMember(@Param("id") Long id,@Param("userId") Long userId);
    int upsertMember(Map<String,Object> member);
    int deleteMember(@Param("id") Long id,@Param("userId") Long userId);
    int duplicateGrant(Map<String,Object> grant);
    int insertGrant(Map<String,Object> grant);
    String grantRole(@Param("id") Long id,@Param("projectId") Long projectId);
    int revokeGrant(@Param("id") Long id);
    int revokeExpert(Map<String,Object> grant);
    int endExpertAccess(@Param("projectId") Long projectId,@Param("moduleType") String moduleType,
            @Param("roundNo") Integer roundNo,@Param("node") String node,@Param("experts") List<Long> experts);
    int touch(@Param("id") Long id);
    Long version(@Param("id") Long id);
    int lifecycle(@Param("id") Long id,@Param("stage") String stage);
}
