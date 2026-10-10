package com.amatrix.sic.iam.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QualificationMapper {
    Long lockApplicant(@Param("id") Long id);
    Integer active(@Param("id") Long id);
    int insert(Map<String,Object> application);
    List<Map<String,Object>> applications(@Param("filter") Map<String,Object> filter);
    int departmentReview(@Param("id") Long id,@Param("actor") Long actor,@Param("opinion") String opinion,
            @Param("remark") String remark,@Param("status") String status);
    int scienceReview(@Param("id") Long id,@Param("actor") Long actor,@Param("opinion") String opinion,
            @Param("remark") String remark,@Param("status") String status);
    int incrementVersion(@Param("id") Long id);
    Long version(@Param("id") Long id);
}
