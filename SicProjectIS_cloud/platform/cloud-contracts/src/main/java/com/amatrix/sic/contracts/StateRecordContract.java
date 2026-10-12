package com.amatrix.sic.contracts;

import java.time.LocalDateTime;

/**
 * 状态历史记录对外结构：序号、轮次、事件、前后状态/节点、结果、摘要和时间。
 * @param stateRecordId 本次历史记录的数据库 ID，不等同于 seq
 * @param seq 模块内的状态序号，每次成功迁移递增，前端以最新序号提交后续动作
 * @param roundNo 业务轮次；退回重提的轮次变化与状态序号递增分别处理
 */
public record StateRecordContract(
        Long stateRecordId,
        Long moduleInstanceId,
        Integer seq,
        Integer roundNo,
        String eventType,
        String fromState,
        String toState,
        String fromNodeId,
        String toNodeId,
        String result,
        String summary,
        LocalDateTime createdAt) {
}
