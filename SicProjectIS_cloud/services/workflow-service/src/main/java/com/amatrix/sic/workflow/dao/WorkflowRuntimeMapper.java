package com.amatrix.sic.workflow.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.workflow.entity.LeaderTask;
import com.amatrix.sic.workflow.entity.ModuleRecord;
import com.amatrix.sic.workflow.entity.ReviewBatch;

@Mapper
public interface WorkflowRuntimeMapper {
    ModuleRecord module(@Param("id") Long id,@Param("lock") boolean lock);
    Long nodeId(@Param("definition") Long definition,@Param("node") String node);
    String nodeCode(@Param("id") Long id);
    List<Map<String,Object>> history(@Param("module") Long module);
    List<Map<String,Object>> tasks(@Param("module") Long module);
    List<Map<String,Object>> workbench();
    List<Map<String,Object>> workbenchCandidates(@Param("view") String view,@Param("userId") Long userId,
            @Param("moduleType") String moduleType,@Param("keyword") String keyword,
            @Param("beforeTime") java.time.LocalDateTime beforeTime,@Param("beforeId") Long beforeId);
    int insertModule(Map<String,Object> values);
    int insertState(Map<String,Object> values);
    int insertTask(@Param("module") Long module,@Param("node") String node,@Param("assignee") Long assignee,
            @Param("role") String role,@Param("round") int round);
    Long batchModule(@Param("id") Long id);
    ReviewBatch lockBatch(@Param("id") Long id);
    List<ReviewBatch> batches(@Param("module") Long module,@Param("round") int round);
    int assignmentCount(@Param("id") Long id);
    List<Long> missingSuccessors();
    List<LeaderTask> leaderTasks();
    int updateLeaderTask(@Param("id") Long id,@Param("owner") Long owner);
    List<Long> endedExpertBatches();
    List<Long> nodeExpertBatches(@Param("module") Long module,@Param("round") int round,@Param("node") String node);
    Map<String,Object> endedScope(@Param("id") Long id);
    List<Long> experts(@Param("id") Long id);
    int endBatch(@Param("id") Long id);
    StateRecordContract latestState(@Param("module") Long module);
    int completeTasks(@Param("module") Long module);
    int advanceModule(@Param("id") Long id,@Param("node") String node,@Param("state") String state,
            @Param("seq") int seq,@Param("round") int round,@Param("finished") boolean finished);
    int reference(@Param("module") Long module,@Param("seq") int seq,@Param("version") Long version,@Param("code") String code);
    Long findModule(@Param("project") Long project,@Param("type") String type);
    Boolean limitedProject(@Param("project") Long project);
    String finalReviewResult(@Param("module") Long module);
    String acceptancePayload(@Param("module") Long module);
}
