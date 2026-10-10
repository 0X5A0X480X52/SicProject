package com.amatrix.sic.project.dao;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.project.entity.ApplicationSagaRow;

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
