package com.amatrix.sic.workflow.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评审读模型 MyBatis 接口：批次、分配、评分与本人评审历史的查询。
 */
@Mapper
public interface ExpertReviewViewMapper {
    /** 按批次 ID 读取评审批次详情，供评审视图组装使用。 */
    Map<String,Object> batch(@Param("id") Long id);
    /** 查询模块全部批次并补充配置节点编码，按批次 ID 排序供评审视图使用。 */
    List<Map<String,Object>> batches(@Param("module") Long module);
    /** 批量读取候选批次的专家指派，按 ID 排序供评审详情组装使用。 */
    List<Map<String,Object>> assignments(@Param("ids") List<Long> ids);
    /** 批量读取候选指派的评分明细，按 ID 排序供评审详情和本人历史展示使用。 */
    List<Map<String,Object>> scores(@Param("ids") List<Long> ids);
    /** 分页查询专家指派及项目信息，按指派 ID 或派生状态筛选，按指派时间、ID 倒序返回。 */
    List<Map<String,Object>> mine(@Param("userId") Long userId,@Param("assignmentId") Long assignmentId,
            @Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
}
