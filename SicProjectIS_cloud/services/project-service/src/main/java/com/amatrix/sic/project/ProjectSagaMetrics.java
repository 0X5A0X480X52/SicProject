package com.amatrix.sic.project;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public final class ProjectSagaMetrics {
    private final JdbcTemplate jdbc;

    ProjectSagaMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("sic.saga.incomplete", this, value -> value.query(
                        "SELECT COUNT(*) FROM application_saga WHERE status IN ('PROJECT_CREATED','WORKFLOW_CREATED')"))
                .description("Project application sagas requiring completion or recovery").register(registry);
        Gauge.builder("sic.saga.failed", this, value -> value.query(
                        "SELECT COUNT(*) FROM application_saga WHERE status IN ('FAILED','COMPENSATED')"))
                .description("Project application sagas that failed or were compensated").register(registry);
        Gauge.builder("sic.saga.oldest.age.seconds", this, value -> value.query(
                        "SELECT COALESCE(TIMESTAMPDIFF(SECOND,MIN(created_at),CURRENT_TIMESTAMP),0) "
                                + "FROM application_saga WHERE status IN ('PROJECT_CREATED','WORKFLOW_CREATED')"))
                .description("Age in seconds of the oldest incomplete project application saga").register(registry);
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
