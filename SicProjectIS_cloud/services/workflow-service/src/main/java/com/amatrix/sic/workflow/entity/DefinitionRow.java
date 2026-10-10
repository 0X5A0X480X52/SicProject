package com.amatrix.sic.workflow.entity;
public record DefinitionRow(Long id,String code,String name,int version,String status,boolean published,String bpmnXml) {}
