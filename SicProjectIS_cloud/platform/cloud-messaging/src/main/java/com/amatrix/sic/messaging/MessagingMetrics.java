package com.amatrix.sic.messaging;

import java.util.Properties;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/** Low-cardinality operational gauges for each service-owned outbox and the shared DLQ. */
public final class MessagingMetrics {
    private final JdbcTemplate jdbc;
    private final RabbitAdmin rabbitAdmin;

    MessagingMetrics(JdbcTemplate jdbc, RabbitAdmin rabbitAdmin, MeterRegistry registry, String serviceName) {
        this.jdbc = jdbc;
        this.rabbitAdmin = rabbitAdmin;
        Tags tags = Tags.of("service", serviceName);
        Gauge.builder("sic.outbox.pending", this, metrics -> metrics.count(
                        "SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL"))
                .description("Unpublished events in this service outbox").tags(tags).register(registry);
        Gauge.builder("sic.outbox.failed", this, metrics -> metrics.count(
                        "SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL AND attempts>0"))
                .description("Outbox events waiting after at least one failed publish").tags(tags).register(registry);
        Gauge.builder("sic.outbox.oldest.age.seconds", this, MessagingMetrics::oldestPendingAge)
                .description("Age in seconds of the oldest unpublished outbox event").tags(tags).register(registry);
        Gauge.builder("sic.events.dead.queued", this, MessagingMetrics::deadLetterCount)
                .description("Messages currently waiting in the shared dead-letter queue").tags(tags).register(registry);
    }

    private double oldestPendingAge() {
        return count("SELECT COALESCE(TIMESTAMPDIFF(SECOND,MIN(created_at),CURRENT_TIMESTAMP),0) "
                + "FROM outbox_event WHERE published_at IS NULL");
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

    private double count(String sql) {
        try {
            Number value = jdbc.queryForObject(sql, Number.class);
            return value == null ? 0 : value.doubleValue();
        } catch (DataAccessException ignored) {
            return Double.NaN;
        }
    }
}
