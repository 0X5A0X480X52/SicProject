package com.amatrix.sic.workflow.dao;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.WorkflowSagaState;
@Mapper
public interface WorkflowSagaMapper {
    WorkflowSagaState find(@Param("saga") String saga);
    Long module(@Param("saga") String saga);
    int insertModule(Map<String,Object> values);
    int snapshot(Map<String,Object> values);
    int application(Map<String,Object> values);
    int deleteScores(@Param("id") Long id);
    int deleteAssignments(@Param("id") Long id);
    int deleteBatches(@Param("id") Long id);
    int deleteMaterials(@Param("id") Long id);
    int deleteReviews(@Param("id") Long id);
    int deleteAcceptance(@Param("id") Long id);
    int deleteContract(@Param("id") Long id);
    int deleteApplication(@Param("id") Long id);
    int deleteForms(@Param("id") Long id);
    int deleteTasks(@Param("id") Long id);
    int deleteStates(@Param("id") Long id);
    int deleteModule(@Param("id") Long id);
}
