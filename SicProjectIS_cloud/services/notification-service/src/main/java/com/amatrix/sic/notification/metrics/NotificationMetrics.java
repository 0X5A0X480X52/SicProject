package com.amatrix.sic.notification.metrics;

import org.springframework.dao.DataAccessException;
import com.amatrix.sic.notification.dao.NotificationMapper;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 暴露待投递通知数量与最老 QUEUED 通知年龄。
 */
@Component
public final class NotificationMetrics {
    private final NotificationMapper notifications;

    NotificationMetrics(NotificationMapper notifications, MeterRegistry registry) {
        this.notifications = notifications;
        Gauge.builder("sic.notification.queued", this, value -> value.query(notifications::queuedCount))
                .description("Durable SSE notifications awaiting delivery").register(registry);
        Gauge.builder("sic.notification.oldest.age.seconds", this, value -> value.query(notifications::oldestAge))
                .description("Age in seconds of the oldest queued SSE notification").register(registry);
    }

    private double query(java.util.function.LongSupplier query) {
        try {
            return query.getAsLong();
        } catch (DataAccessException ignored) {
            return Double.NaN;
        }
    }
}
