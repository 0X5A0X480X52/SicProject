package com.amatrix.sic.workflow.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.ExpertGrantScope;
import com.amatrix.sic.workflow.entity.WorkflowTaskScope;

/**
 * 批量判权 MyBatis 接口：按项目 ID 集合取 OPEN 任务与专家可评审模块、按模块解析所属项目，并按定义分页取关联项目 ID。
 */
@Mapper
public interface WorkflowScopeMapper {
    /** 批量查询候选未完成模块当前节点的 OPEN 任务及项目、类型，供节点操作范围判断。 */
    List<WorkflowTaskScope> openTasks(@Param("ids") List<Long> ids);
    /** 查询候选模块中专家持有有效且状态为 ASSIGNED 指派的模块 ID，供工作台候选筛选。 */
    List<Long> expertModules(@Param("userId") Long userId, @Param("ids") List<Long> ids);
    /** 按模块 ID 读取项目 ID，供项目范围授权定位使用。 */
    Long projectId(@Param("moduleId") Long moduleId);
    /** 按项目 ID 游标取最多 100 个引用指定定义的项目 ID 并去重，供定义访问检查。 */
    List<Long> definitionProjects(@Param("definitionId") Long definitionId, @Param("afterProjectId") long afterProjectId);
    /** 查询当前模块专家待办下有效指派对应的最新批次授权信息，供缺失项目专家 grant 时同步。 */
    ExpertGrantScope expertGrant(@Param("userId") Long userId, @Param("moduleId") Long moduleId);
}
