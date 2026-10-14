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
    /** 按申请人用户 ID 锁定账号行，供资格申请和审核事务串行处理。 */
    Long lockApplicant(@Param("id") Long id);
    /** 统计用户待部门审核或待科研审核的资格申请数，检查是否存在在途申请。 */
    Integer active(@Param("id") Long id);
    int insert(Map<String,Object> application);
    /** 按申请 ID、用户和可选部门范围查询资格申请，补充申请人与审核人的身份、角色，按申请 ID 倒序返回。 */
    List<Map<String,Object>> applications(@Param("filter") Map<String,Object> filter);
    int departmentReview(@Param("id") Long id,@Param("actor") Long actor,@Param("opinion") String opinion,
            @Param("remark") String remark,@Param("status") String status);
    int scienceReview(@Param("id") Long id,@Param("actor") Long actor,@Param("opinion") String opinion,
            @Param("remark") String remark,@Param("status") String status);
    int incrementVersion(@Param("id") Long id);
    /** 读取申请人当前授权版本，供资格或角色变更后的会话版本同步使用。 */
    Long version(@Param("id") Long id);
}
