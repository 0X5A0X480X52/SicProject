package com.amatrix.sicprojectis_backend.expertqualification.dao;

import java.util.List;

import com.amatrix.sicprojectis_backend.expertqualification.entity.ExpertQualificationApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExpertQualificationApplicationDao {
    int insert(ExpertQualificationApplication entity);

    ExpertQualificationApplication selectById(@Param("applicationId") Long applicationId);

    List<ExpertQualificationApplication> selectAll();

    List<ExpertQualificationApplication> selectByApplicantUserId(@Param("applicantUserId") Long applicantUserId);

    int countActiveByApplicantUserId(@Param("applicantUserId") Long applicantUserId);

    int updateById(ExpertQualificationApplication entity);

    int deleteById(@Param("applicationId") Long applicationId);
}
