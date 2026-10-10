package com.amatrix.sic.workflow.dao;

import org.apache.ibatis.annotations.Mapper;
import com.amatrix.sic.contracts.UserSummary;

@Mapper
public interface WorkflowUserSnapshotMapper { int upsert(UserSummary user); }
