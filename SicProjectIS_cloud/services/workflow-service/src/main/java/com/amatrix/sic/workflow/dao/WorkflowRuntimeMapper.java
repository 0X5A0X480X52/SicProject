package com.amatrix.sic.workflow.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.contracts.StateRecordContract;
import com.amatrix.sic.workflow.entity.LeaderTask;
import com.amatrix.sic.workflow.entity.ModuleRecord;
import com.amatrix.sic.workflow.entity.ReviewBatch;

/**
 * 运行器 MyBatis 接口：模块实例（可加行锁）与任务/状态历史的读写、序号推进、材料引用，以及工作台候选分页、评审批次与分配计数、缺失后继/负责人任务/专家访问范围恢复查询。
 */
@Mapper
public interface WorkflowRuntimeMapper {
    /** 按模块 ID 读取实例；lock 为 true 时加行锁，供状态迁移事务使用。 */
    ModuleRecord module(@Param("id") Long id,@Param("lock") boolean lock);
    /** 按定义 ID 和节点编码定位配置 ID，供状态历史及任务关联使用。 */
    Long nodeId(@Param("definition") Long definition,@Param("node") String node);
    /** 按节点配置 ID 读取节点编码，供运行时转换和展示使用。 */
    String nodeCode(@Param("id") Long id);
    /** 读取模块全部状态历史并按序号排序，供运行轨迹展示使用。 */
    List<Map<String,Object>> history(@Param("module") Long module);
    /** 读取模块 OPEN 任务并按 ID 排序，供当前任务展示和操作校验使用。 */
    List<Map<String,Object>> tasks(@Param("module") Long module);
    /** 查询未完成且当前节点有 OPEN 任务的模块，补充项目快照并按更新时间倒序返回工作台候选。 */
    List<Map<String,Object>> workbench();
    /** 按待办、完成或已办理视图及类型、关键字、时间/ID 游标取最多 100 个候选，最终权限由服务层过滤。 */
    List<Map<String,Object>> workbenchCandidates(@Param("view") String view,@Param("userId") Long userId,
            @Param("moduleType") String moduleType,@Param("keyword") String keyword,
            @Param("beforeTime") java.time.LocalDateTime beforeTime,@Param("beforeId") Long beforeId);
    int insertModule(Map<String,Object> values);
    int insertState(Map<String,Object> values);
    int insertTask(@Param("module") Long module,@Param("node") String node,@Param("assignee") Long assignee,
            @Param("role") String role,@Param("round") int round);
    /** 按评审批次 ID 定位模块 ID，供先锁模块再锁批次的流程使用。 */
    Long batchModule(@Param("id") Long id);
    /** 锁定批次并读取状态、轮次和节点，供运行时评审推进使用。 */
    ReviewBatch lockBatch(@Param("id") Long id);
    /** 按模块和轮次查询批次，供当前轮次评审操作使用。 */
    List<ReviewBatch> batches(@Param("module") Long module,@Param("round") int round);
    /** 统计批次全部专家指派数，供批次推进校验使用。 */
    int assignmentCount(@Param("id") Long id);
    /** 取最多 50 个申请或合同已批准完成但缺少下一模块的实例 ID，供后继恢复使用。 */
    List<Long> missingSuccessors();
    /** 取最多 200 个未完成模块当前节点的 PROJECT_LEADER 待办，供负责人指派恢复使用。 */
    List<LeaderTask> leaderTasks();
    int updateLeaderTask(@Param("id") Long id,@Param("owner") Long owner);
    /** 取最多 100 个模块结束、轮次改变或已离开评审节点但未标记访问结束的批次，供权限回收恢复。 */
    List<Long> endedExpertBatches();
    /** 查询模块指定轮次和归一化评审节点下未结束访问的批次 ID，供节点退出时回收权限。 */
    List<Long> nodeExpertBatches(@Param("module") Long module,@Param("round") int round,@Param("node") String node);
    /** 读取未标记访问结束批次的项目、模块、轮次和归一化节点，供权限回收事件组装。 */
    Map<String,Object> endedScope(@Param("id") Long id);
    /** 按批次 ID 读取去重专家 ID 并排序，供专家授权回收使用。 */
    List<Long> experts(@Param("id") Long id);
    int endBatch(@Param("id") Long id);
    /** 按模块 ID 取序号最大的状态记录，供当前运行状态响应组装。 */
    StateRecordContract latestState(@Param("module") Long module);
    int completeTasks(@Param("module") Long module);
    int advanceModule(@Param("id") Long id,@Param("node") String node,@Param("state") String state,
            @Param("seq") int seq,@Param("round") int round,@Param("finished") boolean finished);
    int reference(@Param("module") Long module,@Param("seq") int seq,@Param("version") Long version,@Param("code") String code);
    /** 按项目和模块类型取 ID 最小的已有实例，供后继模块幂等创建检查。 */
    Long findModule(@Param("project") Long project,@Param("type") String type);
    /** 按项目 ID 读取申请的限额标记，供业务流转规则使用。 */
    Boolean limitedProject(@Param("project") Long project);
    /** 读取模块最近一个 COMPLETED 批次的最终结果，供评审后迁移判断使用。 */
    String finalReviewResult(@Param("module") Long module);
    /** 按模块 ID 取 ID 最大的验收正文 JSON，供运行时验收业务读取。 */
    String acceptancePayload(@Param("module") Long module);
}
