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
    Map<String,Object> batch(@Param("id") Long id);
    List<Map<String,Object>> batches(@Param("module") Long module);
    List<Map<String,Object>> assignments(@Param("ids") List<Long> ids);
    List<Map<String,Object>> scores(@Param("ids") List<Long> ids);
    List<Map<String,Object>> mine(@Param("userId") Long userId,@Param("assignmentId") Long assignmentId,
            @Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
}
