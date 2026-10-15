package com.amatrix.sic.project.metrics;

import org.springframework.dao.DataAccessException;
import com.amatrix.sic.project.dao.ApplicationSagaMapper;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 统计未结束 Saga、失败/补偿 Saga 及最老未完成 Saga 年龄。
 */
@Component
public final class ProjectSagaMetrics {
    private final ApplicationSagaMapper mapper;

    ProjectSagaMetrics(ApplicationSagaMapper mapper, MeterRegistry registry) {
        this.mapper = mapper;
        Gauge.builder("sic.saga.incomplete",this,value -> value.query(value.mapper::incomplete))
                .description("Project application sagas requiring completion or recovery").register(registry);
        Gauge.builder("sic.saga.failed",this,value -> value.query(value.mapper::failed))
                .description("Project application sagas that failed or were compensated").register(registry);
        Gauge.builder("sic.saga.oldest.age.seconds",this,value -> value.query(value.mapper::oldestAge))
                .description("Age in seconds of the oldest incomplete project application saga").register(registry);
    }

    private double query(java.util.function.Supplier<? extends Number> query) {
        try {
            Number value = query.get();
            return value == null ? 0 : value.doubleValue();
        } catch (DataAccessException ignored) {
            return Double.NaN;
        }
    }
}
