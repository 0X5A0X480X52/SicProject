package com.amatrix.sic.notification;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public final class NotificationMetrics {
    private final JdbcTemplate jdbc;

    NotificationMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("sic.notification.queued", this, value -> value.query(
                        "SELECT COUNT(*) FROM pending_notification WHERE status='QUEUED'"))
                .description("Durable SSE notifications awaiting delivery").register(registry);
        Gauge.builder("sic.notification.oldest.age.seconds", this, value -> value.query(
                        "SELECT COALESCE(TIMESTAMPDIFF(SECOND,MIN(created_at),CURRENT_TIMESTAMP),0) "
                                + "FROM pending_notification WHERE status='QUEUED'"))
                .description("Age in seconds of the oldest queued SSE notification").register(registry);
    }

    private double query(String sql) {
        try {
            Number value = jdbc.queryForObject(sql, Number.class);
            return value == null ? 0 : value.doubleValue();
        } catch (DataAccessException ignored) {
            return Double.NaN;
        }
    }
}
