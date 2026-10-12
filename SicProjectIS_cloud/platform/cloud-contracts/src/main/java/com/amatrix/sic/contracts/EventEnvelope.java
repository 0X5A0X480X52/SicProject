package com.amatrix.sic.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * 统一事件信封：事件 ID/版本/时间、聚合、关联/因果 ID、操作人及 payload；create 自动生成事件标识。
 * @param eventId 单次事件的去重标识，消费时与 consumer_name 共同识别是否已处理
 * @param eventVersion 事件协议版本，不是用户权限版本或项目数据版本
 * @param correlationId 跨步骤关联标识，可为空；不作为授权凭据
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        String correlationId,
        String causationId,
        Long actorUserId,
        T payload) {

    public static <T> EventEnvelope<T> create(
            String eventType,
            String aggregateType,
            String aggregateId,
            String correlationId,
            String causationId,
            Long actorUserId,
            T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, Instant.now(), aggregateType, aggregateId,
                correlationId, causationId, actorUserId, payload);
    }
}
