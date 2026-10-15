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
    /** 按用户 ID 读取 Project 本地身份快照，供用户目录和授权判断使用。 */
    UserSnapshotRow user(@Param("id") Long id);
    /** 批量读取用户本地身份快照，供目录组装和批量判权使用。 */
    List<UserSnapshotRow> users(@Param("ids") List<Long> ids);
    /** 读取全部启用用户快照，按真实姓名和用户 ID 排序供候选列表使用。 */
    List<UserSnapshotRow> enabled();
    int upsert(@Param("user") UserSummary user,@Param("rolesJson") String rolesJson);
}
