package com.amatrix.sic.workflow.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectSnapshotMapper {
    int owner(@Param("project") Long project, @Param("owner") Long owner, @Param("name") String name,
            @Param("dept") Long dept, @Param("version") Long version, @Param("monotonic") boolean monotonic);
    int leaderTasks(@Param("project") Long project, @Param("owner") Long owner);
    int lifecycle(@Param("project") Long project, @Param("stage") String stage);
}
