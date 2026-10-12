package com.amatrix.sic.project.dao;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.ApplicationSagaRow;

/**
 * 申请 Saga MyBatis 接口：插入临时项目与负责人成员及 Saga 记录，推进 WORKFLOW_CREATED 与确认/失败/完成/补偿状态，查询可恢复与近期同 hash 的 Saga，并统计未完成/失败/最老年龄。
 */
@Mapper
public interface ApplicationSagaMapper {
    int insertProject(Map<String,Object> project);
    int insertOwner(Map<String,Object> project);
    int insertSaga(Map<String,Object> project);
    int workflowCreated(@Param("id") String id,@Param("moduleId") Long moduleId,@Param("response") String response);
    int confirmationFailed(@Param("id") String id,@Param("error") String error);
    List<String> recoverable();
    int retry(@Param("id") String id,@Param("error") String error);
    int activateProject(@Param("id") Long id);
    int failProject(@Param("id") Long id);
    int complete(@Param("id") String id,@Param("response") String response);
    int compensate(@Param("id") String id,@Param("error") String error);
    ApplicationSagaRow recent(@Param("actor") Long actor,@Param("hash") String hash,@Param("after") Instant after);
    ApplicationSagaRow saga(@Param("id") String id);
    Long incomplete();
    Long failed();
    Long oldestAge();
}
