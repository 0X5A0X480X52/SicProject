package com.amatrix.sic.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Project 通过 Feign 发给 Workflow 的申请创建命令；Workflow 以 sagaId 幂等创建申请模块和初始业务快照。
 * @param sagaId 跨服务 Saga UUID，也是远程创建、状态查询和补偿的关联键
 * @param projectId Project 已创建的临时项目主键
 * @param actorUserId 发起申请的 IAM 用户 ID，用于初始任务、状态记录和事件操作人
 * @param projectCode Project 最终采用的项目编号
 * @param projectName 必填项目名称
 * @param projectType 可选项目类别快照
 * @param projectLevel 可选项目级别快照
 * @param approvedAmount 可选批准金额快照
 * @param startDate 可选计划开始日期
 * @param endDate 可选计划结束日期
 * @param applicationTitle 申请标题；Project 已为空白值回退为项目名称
 * @param limitedProject 是否为限额项目，Project 已将 null 规范为 false
 * @param applicationSummary 可选申请摘要，写入 Workflow 初始申请正文
 */
public record ApplicationSagaCommand(
        UUID sagaId,
        Long projectId,
        Long actorUserId,
        String projectCode,
        String projectName,
        String projectType,
        String projectLevel,
        BigDecimal approvedAmount,
        LocalDate startDate,
        LocalDate endDate,
        String applicationTitle,
        Boolean limitedProject,
        String applicationSummary) {
}
