package com.amatrix.sic.workflow.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.amatrix.sic.workflow.entity.WorkflowDefinitionSource;

@Mapper
public interface WorkflowCatalogMapper {
    WorkflowDefinitionSource active(@Param("moduleType") String moduleType);
    String bpmn(@Param("id") Long id);
}
