package com.amatrix.sic.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import com.amatrix.sic.messaging.dao.MessagingMapper;

/**
 * 在 consumed_event 插入 eventId+consumerName；重复键返回 false。应和业务处理同事务，失败回滚后可重试。
 * 去重记录要与消费产生的业务写入使用同一本地事务，回滚后事件才可重新处理。
 */
public class ConsumedEventGuard {
    private final MessagingMapper mapper;

    public ConsumedEventGuard(MessagingMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在业务消费事务中登记 eventId 与消费者名称；重复事件返回 false，事务回滚则去重标记也回滚。
     */
    public boolean begin(UUID eventId, String consumerName) {
        try {
            mapper.consume(eventId.toString(),consumerName,Instant.now());
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
