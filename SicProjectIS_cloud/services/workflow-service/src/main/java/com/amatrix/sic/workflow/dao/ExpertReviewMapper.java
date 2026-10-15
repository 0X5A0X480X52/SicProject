package com.amatrix.sic.workflow.dao;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.*;
/**
 * 评审读写 MyBatis 接口：批次、专家分配、评分与批次状态的插入、锁定与更新。
 */
@Mapper
public interface ExpertReviewMapper {
    /** 按批次 ID 读取评审批次；lock 为 true 时加行锁，供批次修改和评分事务使用。 */
    ExpertBatch batch(@Param("id") Long id,@Param("lock") boolean lock);
    /** 锁定指派并读取批次、专家和评审状态，供评分提交或指派变更使用。 */
    ExpertAssignment lockAssignment(@Param("id") Long id);
    /** 按指派 ID 读取所属批次 ID，供定位批次及按顺序获取锁使用。 */
    Long assignmentBatch(@Param("id") Long id);
    /** 锁定未完成模块，读取定义、当前节点、状态和轮次供评审操作校验。 */
    ExpertModule lockModule(@Param("id") Long id);
    /** 统计节点配置是否匹配配置 ID、定义 ID 和编码，防止跨定义引用节点。 */
    int nodeMatches(@Param("id") Long id,@Param("definition") Long definition,@Param("node") String node);
    /** 按节点配置 ID 和定义 ID 读取节点编码，供评审节点范围定位使用。 */
    String nodeCode(@Param("id") Long id,@Param("definition") Long definition);
    /** 读取模块对应项目、模块编码及指定配置节点编码，供专家授权范围组装。 */
    ExpertGrant grant(@Param("node") Long node,@Param("module") Long module);
    /** 统计专家在指定模块、轮次和 NULL 安全匹配节点下的指派数，供重复指派检查。 */
    int matchingAssignments(@Param("module") Long module,@Param("round") int round,@Param("node") Long node,@Param("expert") Long expert);
    /** 统计专家在指定批次中的指派数，供批次内重复指派检查。 */
    int assigned(@Param("batch") Long batch,@Param("expert") Long expert);
    /** 读取批次中已提交、有效、无利益冲突的非空总分，按分数排序供评审汇总使用。 */
    List<BigDecimal> scores(@Param("id") Long id);
    /** 统计批次全部指派数，供评审完成条件计算使用。 */
    int assignedCount(@Param("id") Long id);
    /** 统计批次已提交指派数，供判断提交进度使用。 */
    int submittedCount(@Param("id") Long id);
    int insertBatch(Map<String,Object> values);
    int insertAssignment(@Param("batch") Long batch,@Param("expert") Long expert,@Param("name") String name,@Param("org") String org,@Param("title") String title);
    int insertScore(Map<String,Object> values);
    int submit(Map<String,Object> values);
    int aggregate(Map<String,Object> values);
    int deleteScores(@Param("id") Long id);
    int deleteAssignment(@Param("id") Long id);
}
