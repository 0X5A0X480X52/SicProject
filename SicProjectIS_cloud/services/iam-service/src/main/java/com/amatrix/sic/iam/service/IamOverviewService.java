package com.amatrix.sic.iam.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.amatrix.sic.iam.dao.OverviewMapper;
import com.amatrix.sic.contracts.IamOverviewStats;

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
