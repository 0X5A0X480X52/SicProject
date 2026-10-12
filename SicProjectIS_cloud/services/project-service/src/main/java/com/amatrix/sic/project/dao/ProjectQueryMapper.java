package com.amatrix.sic.project.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目列表 MyBatis 接口：以单条 SQL 按全局/部门/负责人/成员/有效授权（含专家与财务开关）过滤 ACTIVE 项目。
 */
@Mapper
public interface ProjectQueryMapper {
    List<Map<String,Object>> accessible(@Param("userId") Long userId,@Param("deptId") Long deptId,
        @Param("global") boolean global,@Param("department") boolean department,
        @Param("expert") boolean expert,@Param("finance") boolean finance);
}
