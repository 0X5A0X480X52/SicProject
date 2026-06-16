package com.amatrix.sicprojectis_backend.expert.dao;

import java.util.List;

import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExpertReviewBatchDao {
    int insert(ExpertReviewBatch entity);

    ExpertReviewBatch selectById(@Param("batchId") Long batchId);

    List<ExpertReviewBatch> selectByModuleInstanceId(@Param("moduleInstanceId") Long moduleInstanceId);

    List<ExpertReviewBatch> selectAll();

    int updateById(ExpertReviewBatch entity);

    int deleteById(@Param("batchId") Long batchId);
}
