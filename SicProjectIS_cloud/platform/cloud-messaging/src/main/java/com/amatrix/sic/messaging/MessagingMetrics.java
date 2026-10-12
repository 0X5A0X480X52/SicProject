package com.amatrix.sic.messaging;

import java.util.Properties;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.dao.DataAccessException;
import com.amatrix.sic.messaging.dao.MessagingMapper;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 注册各服务 Outbox 待发、失败、最老年龄以及公共死信队列积压 Gauge。
 */
public final class MessagingMetrics {
    private final MessagingMapper mapper;
    private final RabbitAdmin rabbitAdmin;

    MessagingMetrics(MessagingMapper mapper, RabbitAdmin rabbitAdmin, MeterRegistry registry, String serviceName) {
        this.mapper = mapper;
        this.rabbitAdmin = rabbitAdmin;
        Tags tags = Tags.of("service", serviceName);
        Gauge.builder("sic.outbox.pending", this, metrics -> metrics.count(mapper::pending))
                .description("Unpublished events in this service outbox").tags(tags).register(registry);
        Gauge.builder("sic.outbox.failed", this, metrics -> metrics.count(mapper::failedCount))
                .description("Outbox events waiting after at least one failed publish").tags(tags).register(registry);
        Gauge.builder("sic.outbox.oldest.age.seconds", this, MessagingMetrics::oldestPendingAge)
                .description("Age in seconds of the oldest unpublished outbox event").tags(tags).register(registry);
        Gauge.builder("sic.events.dead.queued", this, MessagingMetrics::deadLetterCount)
                .description("Messages currently waiting in the shared dead-letter queue").tags(tags).register(registry);
    }

    private double oldestPendingAge() {
        return count(mapper::oldestAge);
    }

    private double deadLetterCount() {
        if (rabbitAdmin == null) return Double.NaN;
        try {
            Properties properties = rabbitAdmin.getQueueProperties(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE);
            if (properties == null) return 0;
            Object value = properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            return value instanceof Number number ? number.doubleValue() : 0;
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private double count(java.util.function.LongSupplier query) {
        try {
            return query.getAsLong();
        } catch (DataAccessException ignored) {
            return Double.NaN;
        }
    }
}
