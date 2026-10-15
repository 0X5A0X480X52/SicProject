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
    /** 读取模块指定轮次最大的状态记录 ID，供表单或结算关联轮次历史使用。 */
    Long latestState(@Param("module") Long module, @Param("round") int round);
    int settlement(Map<String,Object> values);
    int insertRecord(Map<String,Object> values);
    int updateRecord(@Param("module") Long module, @Param("id") Long id, @Param("pattern") String pattern,
            @Param("round") int round, @Param("json") String json, @Param("actor") Long actor);
    int deleteRecord(@Param("module") Long module, @Param("id") Long id, @Param("pattern") String pattern, @Param("round") int round);
    /** 按模块 ID 读取申请正文 JSON，供节点表单初始数据和业务聚合使用。 */
    String application(@Param("module") Long module);
    /** 按模块 ID 读取轮次最高的合同正文 JSON，供节点表单回填使用。 */
    String contract(@Param("module") Long module);
    /** 按模块 ID 读取轮次最高的验收正文 JSON，供节点表单回填使用。 */
    String acceptance(@Param("module") Long module);
    /** 按模块及表单编码或节点编码取 ID 最大的表单 JSON，供保存数据恢复使用。 */
    String stored(@Param("module") Long module, @Param("code") String code, @Param("node") String node);
    /** 查询模块中匹配节点前缀或精确编码的表单，按 ID 排序供历史和列表展示使用。 */
    List<NodeFormRecord> records(@Param("module") Long module, @Param("pattern") String pattern, @Param("node") String node);
    /** 统计指定模块是否存在，供表单目标模块检查使用。 */
    int moduleCount(@Param("id") Long id);
    /** 锁定模块并读取项目、类型、节点、状态、轮次和完成标记，供表单写入前校验。 */
    NodeFormModule lockModule(@Param("id") Long id);
}
