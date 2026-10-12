package com.amatrix.sic.project.dao;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.*;

/**
 * 批量判权 MyBatis 接口：按项目 ID 集合取项目版本与负责人、当前用户的成员关系、有效授权范围。
 */
@Mapper
public interface ProjectScopeMapper {
    List<ProjectScopeRow> projects(@Param("ids") Collection<Long> ids);
    List<Long> memberships(@Param("userId") Long userId,@Param("ids") Collection<Long> ids);
    List<ProjectGrantScopeRow> grants(@Param("userId") Long userId,@Param("ids") Collection<Long> ids);
}
