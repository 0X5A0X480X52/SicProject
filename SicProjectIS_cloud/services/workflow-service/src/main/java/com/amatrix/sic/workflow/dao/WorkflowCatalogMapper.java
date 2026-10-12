package com.amatrix.sic.workflow.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.WorkflowDefinitionSource;

/**
 * 目录查询 MyBatis 接口：按模块码取最新已发布定义的 BPMN XML（id 与 bpmnXml），或按定义 ID 取 BPMN XML，供运行期解析模型。
 */
@Mapper
public interface WorkflowCatalogMapper {
    WorkflowDefinitionSource active(@Param("moduleType") String moduleType);
    String bpmn(@Param("id") Long id);
}
