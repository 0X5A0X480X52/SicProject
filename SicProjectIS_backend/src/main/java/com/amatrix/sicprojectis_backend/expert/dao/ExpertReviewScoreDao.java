package com.amatrix.sicprojectis_backend.expert.dao;

import java.util.List;

import com.amatrix.sicprojectis_backend.expert.entity.ExpertReviewScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExpertReviewScoreDao {
    int insert(ExpertReviewScore entity);

    ExpertReviewScore selectById(@Param("scoreId") Long scoreId);

    List<ExpertReviewScore> selectByAssignmentId(@Param("assignmentId") Long assignmentId);

    List<ExpertReviewScore> selectAll();

    int updateById(ExpertReviewScore entity);

    int deleteById(@Param("scoreId") Long scoreId);
}
