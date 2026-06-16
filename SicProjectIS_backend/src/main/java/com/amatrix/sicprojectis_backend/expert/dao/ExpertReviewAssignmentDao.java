package com.amatrix.sicprojectis_backend.expert.dao;

import java.util.List;

import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExpertReviewAssignmentDao {
    int insert(ExpertReviewAssignment entity);

    ExpertReviewAssignment selectById(@Param("assignmentId") Long assignmentId);

    List<ExpertReviewAssignment> selectByBatchId(@Param("batchId") Long batchId);

    List<ExpertReviewAssignment> selectAll();

    int updateById(ExpertReviewAssignment entity);

    int deleteById(@Param("assignmentId") Long assignmentId);
}
