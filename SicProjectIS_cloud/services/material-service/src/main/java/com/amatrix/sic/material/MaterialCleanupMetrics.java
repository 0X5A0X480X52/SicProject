package com.amatrix.sic.material;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public final class MaterialCleanupMetrics {
    private final JdbcTemplate jdbc;

    MaterialCleanupMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("sic.material.object.deletion.pending", this,
                        value -> value.count("SELECT COUNT(*) FROM object_deletion_job WHERE status='PENDING'"))
                .description("MinIO objects waiting for durable deletion").register(registry);
        Gauge.builder("sic.material.object.deletion.failed", this,
                        value -> value.count("SELECT COUNT(*) FROM object_deletion_job WHERE status='PENDING' AND attempts>0"))
                .description("MinIO object deletions waiting after a failed attempt").register(registry);
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
