package com.amatrix.sic.project.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.UserSnapshotRow;
import com.amatrix.sic.contracts.UserSummary;

/**
 * 用户快照 MyBatis 接口：按 ID 单个或批量读取快照、读取全部启用用户，以及按 IAM 摘要 upsert 快照与角色 JSON。
 */
@Mapper
public interface UserSnapshotMapper {
    UserSnapshotRow user(@Param("id") Long id);
    List<UserSnapshotRow> users(@Param("ids") List<Long> ids);
    List<UserSnapshotRow> enabled();
    int upsert(@Param("user") UserSummary user,@Param("rolesJson") String rolesJson);
}
