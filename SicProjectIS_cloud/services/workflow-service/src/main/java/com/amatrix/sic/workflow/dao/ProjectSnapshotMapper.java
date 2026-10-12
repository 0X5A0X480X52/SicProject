package com.amatrix.sic.workflow.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目快照 MyBatis 接口：更新项目负责人/部门与来源版本（支持单调条件）、把当前 OPEN 的负责人任务改派给新负责人，以及更新生命周期阶段。
 */
@Mapper
public interface ProjectSnapshotMapper {
    int owner(@Param("project") Long project, @Param("owner") Long owner, @Param("name") String name,
            @Param("dept") Long dept, @Param("version") Long version, @Param("monotonic") boolean monotonic);
    int leaderTasks(@Param("project") Long project, @Param("owner") Long owner);
    int lifecycle(@Param("project") Long project, @Param("stage") String stage);
}
