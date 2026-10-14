package com.amatrix.sic.workflow.dao;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.DefinitionRow;
/**
 * 定义 MyBatis 接口：定义与版本状态写入、发布/停用切换、BPMN XML 读取，以及节点与连线配置的删除重建。
 */
@Mapper
public interface WorkflowDefinitionMapper {
    /** 按定义 ID 读取完整记录，供定义详情和生命周期操作使用。 */
    DefinitionRow find(@Param("id") Long id);
    /** 按 ID 列出全部定义，供定义管理和资产列表组装使用。 */
    List<DefinitionRow> all();
    /** 按编码读取版本最高的已发布定义，供创建实例时选择定义使用。 */
    DefinitionRow latest(@Param("code") String code);
    /** 按编码和版本号定位定义 ID，供发行资产安装和版本重复检查。 */
    Long versionId(@Param("code") String code,@Param("version") int version);
    /** 统计指定编码下已有节点配置的定义数，供启动检查发行流程是否已安装。 */
    int installed(@Param("code") String code);
    /** 判断是否存在版本号更高的已发布定义，供补齐资产时避免覆盖发布选择。 */
    boolean newerPublished(@Param("code") String code,@Param("version") int version);
    /** 统计定义节点配置数，供模型完整性和资产初始化检查使用。 */
    int nodeCount(@Param("id") Long id);
    /** 统计引用定义的实例数，供定义修改及资产替换前检查使用。 */
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
