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
    /** 按创建时间取最多 20 个已到重试时间的 WORKFLOW_CREATED Saga ID，供申请确认恢复使用。 */
    List<String> recoverable();
    int retry(@Param("id") String id,@Param("error") String error);
    int activateProject(@Param("id") Long id);
    int failProject(@Param("id") Long id);
    int complete(@Param("id") String id,@Param("response") String response);
    int compensate(@Param("id") String id,@Param("error") String error);
    /** 按申请人、请求哈希和起始时间取最近一次 Saga，供重复申请请求幂等复用。 */
    ApplicationSagaRow recent(@Param("actor") Long actor,@Param("hash") String hash,@Param("after") Instant after);
    /** 按 Saga ID 读取项目、申请人、状态和已保存响应，供申请状态查询使用。 */
    ApplicationSagaRow saga(@Param("id") String id);
    /** 按项目 ID 加行锁，供申请确认时先锁项目、再锁 Saga 的事务顺序使用。 */
    Long lockProject(@Param("id") Long id);
    /** 按 Saga ID 锁定并读取状态和响应，供确认或重复恢复时复核状态。 */
    ApplicationSagaRow lockSaga(@Param("id") String id);
    /** 统计 PROJECT_CREATED 和 WORKFLOW_CREATED 状态的未完成 Saga 数，供恢复积压指标使用。 */
    Long incomplete();
    /** 统计 FAILED 或 COMPENSATED 状态的 Saga 数，供失败及补偿指标使用。 */
    Long failed();
    /** 计算最早未完成 Saga 距当前的秒数，无积压时返回 0，供恢复年龄指标使用。 */
    Long oldestAge();
}
