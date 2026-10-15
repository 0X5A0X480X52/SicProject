package com.amatrix.sic.iam.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.amatrix.sic.iam.dao.OverviewMapper;
import com.amatrix.sic.contracts.IamOverviewStats;

/**
 * 只读汇总用户/启用用户/角色/权限计数与角色人数分布，组装管理员概览 IamOverviewStats；本身是 Mapper 的薄封装。
 */
@Service
public class IamOverviewService {
    private final OverviewMapper mapper;
    public IamOverviewService(OverviewMapper mapper) { this.mapper=mapper; }
    @Transactional(readOnly=true)
    public IamOverviewStats overview() {
        var counts=mapper.counters();
        return new IamOverviewStats(counts.users(),counts.enabledUsers(),counts.roles(),counts.permissions(),mapper.roleCounts());
    }
}
