package com.amatrix.sic.project.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审计与概览 MyBatis 接口：写入本地及 IAM 管理日志、按条件筛选日志、统计有效授权与日志数、按用户 ID 取名。
 */
@Mapper
public interface ProjectAuditMapper {
    int insert(Map<String,Object> log);
    int insertIam(Map<String,Object> log);
    Long activeGrants();
    Long logCount();
    List<Map<String,Object>> logs(@Param("filter") Map<String,Object> filter);
    String userName(@Param("id") Long id);
}
