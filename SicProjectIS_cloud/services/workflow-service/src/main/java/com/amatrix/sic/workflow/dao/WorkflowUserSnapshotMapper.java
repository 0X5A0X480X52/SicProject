package com.amatrix.sic.workflow.dao;

import org.apache.ibatis.annotations.Mapper;
import com.amatrix.sic.contracts.UserSummary;

/**
 * 用户快照 MyBatis 接口：按 IAM 摘要 upsert 本地 user_snapshot 的账号、姓名、启用状态与来源版本（以 authVersion 单调保护）。
 */
@Mapper
public interface WorkflowUserSnapshotMapper { int upsert(UserSummary user); }
