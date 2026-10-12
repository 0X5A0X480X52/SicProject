package com.amatrix.sic.workflow.dao;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.WorkflowSagaState;
/**
 * 申请 Saga MyBatis 接口：按 sagaId 查询状态与模块、创建申请模块/项目快照/申请正文，并在补偿时按模块级联删除任务、状态、表单、材料引用、评审、验收与合同等关联数据。
 */
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
