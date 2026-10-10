package com.amatrix.sic.project.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectAuditMapper {
    int insert(Map<String,Object> log);
    int insertIam(Map<String,Object> log);
    Long activeGrants();
    Long logCount();
    List<Map<String,Object>> logs(@Param("filter") Map<String,Object> filter);
    String userName(@Param("id") Long id);
}
