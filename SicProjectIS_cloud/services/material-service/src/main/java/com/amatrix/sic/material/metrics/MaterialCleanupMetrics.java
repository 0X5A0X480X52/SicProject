package com.amatrix.sic.material.metrics;

import org.springframework.dao.DataAccessException;
import com.amatrix.sic.material.dao.ObjectDeletionMapper;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 暴露 PENDING 对象删除任务数量及其中已失败过的任务数量；没有材料预留数量指标。
 */
@Component
public final class MaterialCleanupMetrics {
    private final ObjectDeletionMapper mapper;

    MaterialCleanupMetrics(ObjectDeletionMapper mapper, MeterRegistry registry) {
        this.mapper = mapper;
        Gauge.builder("sic.material.object.deletion.pending", this,
                        value -> value.count(value.mapper::pendingCount))
                .description("MinIO objects waiting for durable deletion").register(registry);
        Gauge.builder("sic.material.object.deletion.failed", this,
                        value -> value.count(value.mapper::failedCount))
                .description("MinIO object deletions waiting after a failed attempt").register(registry);
    }

    private double count(java.util.function.Supplier<? extends Number> query) {
        try {
            Number value = query.get();
            return value == null ? 0 : value.doubleValue();
        } catch (DataAccessException ignored) {
            return Double.NaN;
        }
    }
}
