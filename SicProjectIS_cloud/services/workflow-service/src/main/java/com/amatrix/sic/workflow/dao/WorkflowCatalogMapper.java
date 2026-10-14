package com.amatrix.sic.workflow.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.WorkflowDefinitionSource;

/**
 * 目录查询 MyBatis 接口：按模块码取最新已发布定义的 BPMN XML（id 与 bpmnXml），或按定义 ID 取 BPMN XML，供运行期解析模型。
 */
@Mapper
public interface WorkflowCatalogMapper {
    /** 按模块类型取版本最高的已发布定义 ID 和 BPMN XML，供运行时加载使用。 */
    WorkflowDefinitionSource active(@Param("moduleType") String moduleType);
    /** 按定义 ID 读取 BPMN XML，供指定版本模型加载使用。 */
    String bpmn(@Param("id") Long id);
}
