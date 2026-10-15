package com.amatrix.sic.contracts;

/**
 * 跨服务部门摘要：部门 ID、部门名称与启用状态。
 * @param deptId IAM 部门主键
 * @param deptName 当前部门名称，用于 Project 归属快照
 * @param enabled 是否允许用于新项目申请或归属修复；查询成功不等于 enabled=true
 */
public record DepartmentSummary(Long deptId, String deptName, boolean enabled) {}
