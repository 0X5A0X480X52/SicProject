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
    /** 统计状态为 ACTIVE 的项目授权记录数，供管理概览使用。 */
    Long activeGrants();
    /** 统计全部管理操作日志数，供审计概览使用。 */
    Long logCount();
    /** 按关键字、操作、范围、项目、操作者及时间区间筛选日志，按时间倒序取最多 1000 条。 */
    List<Map<String,Object>> logs(@Param("filter") Map<String,Object> filter);
    /** 按用户 ID 读取本地身份快照真实姓名，供审计展示和名称快照组装使用。 */
    String userName(@Param("id") Long id);
}
