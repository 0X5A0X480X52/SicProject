package com.amatrix.sic.workflow.dao;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.DefinitionRow;
@Mapper
public interface WorkflowDefinitionMapper {
    DefinitionRow find(@Param("id") Long id);
    List<DefinitionRow> all();
    DefinitionRow latest(@Param("code") String code);
    Long versionId(@Param("code") String code,@Param("version") int version);
    int installed(@Param("code") String code);
    boolean newerPublished(@Param("code") String code,@Param("version") int version);
    int nodeCount(@Param("id") Long id);
    int moduleCount(@Param("id") Long id);
    int insert(Map<String,Object> values);
    int replaceEmpty(Map<String,Object> values);
    int deactivate(@Param("code") String code,@Param("id") Long id);
    int publish(Map<String,Object> values);
    int deleteTransitions(@Param("id") Long id);
    int deleteNodes(@Param("id") Long id);
    int insertNode(Map<String,Object> values);
    int insertTransition(Map<String,Object> values);
}
