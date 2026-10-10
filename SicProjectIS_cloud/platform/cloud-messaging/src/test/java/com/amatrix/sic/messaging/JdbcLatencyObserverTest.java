package com.amatrix.sic.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class JdbcLatencyObserverTest {
    @Test @SuppressWarnings("unchecked") void observationPreservesTransactionConnectionAndRollback() throws Exception {
        var original=new DriverManagerDataSource("jdbc:h2:mem:observe-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var registries=mock(ObjectProvider.class);
        var meters=new SimpleMeterRegistry();when(registries.getIfUnique()).thenReturn(meters);
        var observer=new JdbcLatencyObserver(new MockEnvironment(),registries);
        var source=(DataSource)observer.postProcessAfterInitialization(original,"dataSource");
        observer.afterSingletonsInstantiated();
        assertThat(source.unwrap(DriverManagerDataSource.class)).isSameAs(original);
        var jdbc=new JdbcTemplate(source);jdbc.execute("CREATE TABLE sample(id BIGINT PRIMARY KEY)");
        var transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
        MDC.put("traceId","jdbc-test-trace");
        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                Connection first=DataSourceUtils.getConnection(source),second=DataSourceUtils.getConnection(source);
                assertThat(first).isSameAs(second);
                DataSourceUtils.releaseConnection(first,source);DataSourceUtils.releaseConnection(second,source);
                jdbc.update("INSERT INTO sample VALUES(?)",1);
                throw new IllegalStateException("Rollback fixture");
            })).isInstanceOf(IllegalStateException.class);
        } finally { MDC.clear(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample",Long.class)).isZero();
        assertThat(meters.find("sic.jdbc.duration").tag("phase","statement").tag("operation","insert").timer().count()).isEqualTo(1);
        assertThat(meters.find("sic.jdbc.duration").tag("phase","hold").timer().count()).isEqualTo(3);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO missing_table VALUES(?)",1))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(meters.find("sic.jdbc.duration").tag("phase","prepare").tag("outcome","error").timer().count()).isEqualTo(1);
    }

    @Test @SuppressWarnings("unchecked") void sqlTraceIsValidatedAndMetricsFailureCannotMaskSqlFailure() throws Exception {
        var registries=mock(ObjectProvider.class);var registry=mock(MeterRegistry.class);
        when(registries.getIfUnique()).thenReturn(registry);
        when(registry.timer(anyString(),any(String[].class))).thenThrow(new IllegalStateException("Injected metrics failure"));
        var observer=new JdbcLatencyObserver(new MockEnvironment(),registries);observer.afterSingletonsInstantiated();
        var connection=mock(Connection.class);
        var delegate=mock(DataSource.class);when(delegate.getConnection()).thenReturn(connection);
        var source=(DataSource)observer.postProcessAfterInitialization(delegate,"dataSource");
        var wrapped=source.getConnection();
        try {
            MDC.put("traceId","valid-trace-123");wrapped.prepareStatement("SELECT 1");
            verify(connection).prepareStatement("/* sic_trace=valid-trace-123 */ SELECT 1");
            MDC.put("traceId","invalid*/ SELECT secret");wrapped.prepareStatement("SELECT 2");
            verify(connection).prepareStatement("SELECT 2");
            var failure=new java.sql.SQLException("Original driver failure");
            doThrow(failure).when(connection).close();
            assertThatThrownBy(wrapped::close).isSameAs(failure);
        } finally { MDC.clear(); }
    }
}
