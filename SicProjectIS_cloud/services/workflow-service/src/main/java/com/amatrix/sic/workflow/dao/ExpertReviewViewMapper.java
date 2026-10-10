package com.amatrix.sic.workflow.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExpertReviewViewMapper {
    Map<String,Object> batch(@Param("id") Long id);
    List<Map<String,Object>> batches(@Param("module") Long module);
    List<Map<String,Object>> assignments(@Param("ids") List<Long> ids);
    List<Map<String,Object>> scores(@Param("ids") List<Long> ids);
}
