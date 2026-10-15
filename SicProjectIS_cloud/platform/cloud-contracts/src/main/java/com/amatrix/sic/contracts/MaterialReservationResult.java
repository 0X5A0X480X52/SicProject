package com.amatrix.sic.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 材料预留结果：预留 ID、状态、过期时间、版本列表和版本到类型映射。
 * @param reservationId 租约 ID，用于失败释放和后续确认，不是 commandId 或材料版本 ID
 * @param status RESERVED、CONFIRMED、RELEASED 或 EXPIRED 等当前状态
 * @param expiresAt RESERVED 租约的过期时刻；过期清理与迟到确认由 Material 状态规则处理
 * @param materialVersionIds 本租约包含的具体材料版本 ID
 * @param materialTypeCodes 材料版本 ID 到类型代码的映射，用于 Workflow 写状态引用
 */
public record MaterialReservationResult(
        String reservationId,
        String status,
        Instant expiresAt,
        List<Long> materialVersionIds,
        Map<Long, String> materialTypeCodes) {
}
