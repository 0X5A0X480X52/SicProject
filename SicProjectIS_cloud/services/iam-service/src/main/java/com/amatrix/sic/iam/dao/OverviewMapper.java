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
    IamCounters counters();
    List<IamOverviewStats.CountItem> roleCounts();
}
