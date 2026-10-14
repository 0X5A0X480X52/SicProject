package com.amatrix.sic.workflow.dao;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.*;
/**
 * 结构化业务正文 MyBatis 接口：申请、合同、结题正文与成果、财务结算的读写。
 */
@Mapper
public interface StructuredBusinessMapper {
    /** 按项目 ID 取最近申请正文和元数据，供结构化业务读取使用。 */
    ApplicationDraftRow application(@Param("project") Long project);
    /** 按项目和模块 ID 取最近申请记录 ID，供正文写入定位使用。 */
    Long applicationId(@Param("project") Long project,@Param("module") Long module);
    int updateApplication(Map<String,Object> values);
    /** 按项目 ID 读取成果记录并按 ID 排序，供成果列表展示使用。 */
    List<Map<String,Object>> achievements(@Param("project") Long project);
    /** 按项目 ID 读取验收财务结算并按 ID 排序，供结算列表展示使用。 */
    List<Map<String,Object>> settlements(@Param("project") Long project);
    int achievement(Map<String,Object> values);
    int settlement(Map<String,Object> values);
    /** 按项目和模块类型读取合同或验收最近轮次正文 JSON，同轮次按 ID 倒序取一条。 */
    String draft(@Param("project") Long project,@Param("type") String type);
    int saveDraft(@Param("kind") String kind,@Param("module") Long module,@Param("round") int round,@Param("json") String json);
    /** 按项目和模块类型读取最新模块 ID 和当前轮次，供结构化正文关联使用。 */
    ModuleRound module(@Param("project") Long project,@Param("type") String type);
    /** 统计模块是否属于指定项目，供业务数据关联检查使用。 */
    int moduleProject(@Param("id") Long id,@Param("project") Long project);
    /** 统计验收记录是否属于指定模块，供财务结算关联校验使用。 */
    int relatedAcceptance(@Param("id") Long id,@Param("module") Long module);
    /** 统计状态记录是否属于指定模块和轮次，供业务历史关联校验使用。 */
    int relatedState(@Param("id") Long id,@Param("module") Long module,@Param("round") int round);
    /** 按项目、类型、当前节点和状态锁定最新未完成模块，供正文提交前检查轮次。 */
    BusinessTask lockTask(@Param("project") Long project,@Param("type") String type,@Param("node") String node,@Param("state") String state);
}
