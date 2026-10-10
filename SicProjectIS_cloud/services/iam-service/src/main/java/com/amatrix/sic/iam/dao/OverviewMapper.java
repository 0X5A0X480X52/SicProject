package com.amatrix.sic.iam.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.amatrix.sic.iam.entity.IamCounters;
import com.amatrix.sic.contracts.IamOverviewStats;

@Mapper
public interface OverviewMapper {
    IamCounters counters();
    List<IamOverviewStats.CountItem> roleCounts();
}
