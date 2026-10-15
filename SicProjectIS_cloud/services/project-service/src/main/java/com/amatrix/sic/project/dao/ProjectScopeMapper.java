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
    /** 批量读取候选 ACTIVE 项目的版本、负责人和部门，供项目范围判权使用。 */
    List<ProjectScopeRow> projects(@Param("ids") Collection<Long> ids);
    /** 查询用户在候选项目集合中的成员项目 ID，供批量访问判断使用。 */
    List<Long> memberships(@Param("userId") Long userId,@Param("ids") Collection<Long> ids);
    /** 读取用户在候选项目内处于有效期的 ACTIVE 授权范围，供策略层进一步判权。 */
    List<ProjectGrantScopeRow> grants(@Param("userId") Long userId,@Param("ids") Collection<Long> ids);
}
