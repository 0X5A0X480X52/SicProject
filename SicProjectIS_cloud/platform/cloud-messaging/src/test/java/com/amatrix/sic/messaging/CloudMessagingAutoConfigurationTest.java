package com.amatrix.sic.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

class CloudMessagingAutoConfigurationTest {
    @Test
    void loadsAfterJdbcTemplateAutoConfigurationInARealContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JdbcTemplateAutoConfiguration.class,
                        CloudMessagingAutoConfiguration.class))
                .withBean(DataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:h2:mem:messaging-auto-config;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(org.springframework.jdbc.core.JdbcTemplate.class);
                    assertThat(context).hasSingleBean(OutboxRepository.class);
                    assertThat(context).hasSingleBean(OutboxService.class);
                    assertThat(context).hasSingleBean(ConsumedEventGuard.class);
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                });
    }
}
