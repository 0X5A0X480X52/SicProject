package com.amatrix.sic.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.amatrix.sic.contracts.StateTransitionContract;

/** 项目申请启动的前端请求和 Saga 完成响应。 */
public final class ProjectDtos {
    private ProjectDtos() {
    }

    /**
     * 创建项目并启动 APPLICATION 模块的请求。
     * @param projectCode 可选项目编号；空白时由服务生成 {@code P-xxxxxxxx}
     * @param projectName 必填项目名称，参与近期重复请求 hash
     * @param projectType 可选项目类别，原样写入项目及 Workflow 快照
     * @param projectLevel 可选项目级别
     * @param approvedAmount 可选批准金额
     * @param startDate 可选计划开始日期
     * @param endDate 可选计划结束日期；同时提供日期时不得早于 startDate
     * @param applicationTitle 可选申请标题；空白时使用 projectName
     * @param isLimitedProject 仅 {@code true} 表示限额项目，空值按 false
     * @param applicationSummary 可选申请摘要，写入初始申请正文
     */
    public record StartProjectApplicationRequest(
            String projectCode, String projectName, String projectType, String projectLevel,
            BigDecimal approvedAmount, LocalDate startDate, LocalDate endDate, String applicationTitle,
            Boolean isLimitedProject, String applicationSummary) {
    }

    /**
     * Project 与 Workflow 两侧均创建并确认后的申请启动结果。
     * @param projectId Project 项目主键
     * @param moduleInstanceId Workflow APPLICATION 模块实例 ID
     * @param transition Workflow 初始状态记录及当前节点，不代表申请流程已经结束
     */
    public record StartProjectApplicationResponse(
            Long projectId, Long moduleInstanceId, StateTransitionContract transition) {
    }
}
