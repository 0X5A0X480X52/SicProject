package com.amatrix.sic.workflow.dao;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.*;
@Mapper
public interface StructuredBusinessMapper {
    ApplicationDraftRow application(@Param("project") Long project);
    Long applicationId(@Param("project") Long project,@Param("module") Long module);
    int updateApplication(Map<String,Object> values);
    List<Map<String,Object>> achievements(@Param("project") Long project);
    List<Map<String,Object>> settlements(@Param("project") Long project);
    int achievement(Map<String,Object> values);
    int settlement(Map<String,Object> values);
    String draft(@Param("project") Long project,@Param("type") String type);
    int saveDraft(@Param("kind") String kind,@Param("module") Long module,@Param("round") int round,@Param("json") String json);
    ModuleRound module(@Param("project") Long project,@Param("type") String type);
    int moduleProject(@Param("id") Long id,@Param("project") Long project);
    int relatedAcceptance(@Param("id") Long id,@Param("module") Long module);
    int relatedState(@Param("id") Long id,@Param("module") Long module,@Param("round") int round);
    BusinessTask lockTask(@Param("project") Long project,@Param("type") String type,@Param("node") String node,@Param("state") String state);
}
