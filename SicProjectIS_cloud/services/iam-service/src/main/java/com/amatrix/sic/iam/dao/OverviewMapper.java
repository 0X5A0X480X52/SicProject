package com.amatrix.sic.iam.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.amatrix.sic.iam.entity.IamCounters;
import com.amatrix.sic.contracts.IamOverviewStats;

/**
 * 概览 MyBatis 接口：一次取用户/角色/权限计数，并按角色统计人数。
 */
@Mapper
public interface OverviewMapper {
    /** 汇总用户总数、启用用户数、角色数和权限数，供 IAM 管理概览使用。 */
    IamCounters counters();
    /** 按角色统计关联用户数，保留零用户角色，供角色分布展示使用。 */
    List<IamOverviewStats.CountItem> roleCounts();
}
