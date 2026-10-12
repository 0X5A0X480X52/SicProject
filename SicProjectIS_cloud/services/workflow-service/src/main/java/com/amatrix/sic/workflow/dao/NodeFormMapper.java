package com.amatrix.sic.workflow.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.NodeFormModule;
import com.amatrix.sic.workflow.entity.NodeFormRecord;

/**
 * 节点表单 MyBatis 接口：按模块、节点、轮次读写表单记录及历史记录查询。
 */
@Mapper
public interface NodeFormMapper {
    int store(@Param("module") Long module, @Param("key") String key, @Param("round") int round,
            @Param("json") String json, @Param("actor") Long actor);
    int updateApplication(@Param("module") Long module, @Param("project") Long project,
            @Param("draft") Map<?,?> draft, @Param("json") String json);
    int storeDraft(@Param("kind") String kind, @Param("module") Long module, @Param("round") int round, @Param("json") String json);
    Long latestState(@Param("module") Long module, @Param("round") int round);
    int settlement(Map<String,Object> values);
    int insertRecord(Map<String,Object> values);
    int updateRecord(@Param("module") Long module, @Param("id") Long id, @Param("pattern") String pattern,
            @Param("round") int round, @Param("json") String json, @Param("actor") Long actor);
    int deleteRecord(@Param("module") Long module, @Param("id") Long id, @Param("pattern") String pattern, @Param("round") int round);
    String application(@Param("module") Long module);
    String contract(@Param("module") Long module);
    String acceptance(@Param("module") Long module);
    String stored(@Param("module") Long module, @Param("code") String code, @Param("node") String node);
    List<NodeFormRecord> records(@Param("module") Long module, @Param("pattern") String pattern, @Param("node") String node);
    int moduleCount(@Param("id") Long id);
    NodeFormModule lockModule(@Param("id") Long id);
}
