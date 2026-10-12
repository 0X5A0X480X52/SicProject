package com.amatrix.sic.iam.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 专家资格 MyBatis 接口：锁定申请人、判断是否有生效申请、插入与筛选申请、部门/科技处审核写回，以及授权版本读取与递增。
 */
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
