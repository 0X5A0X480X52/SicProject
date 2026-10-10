package com.amatrix.sic.dlq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(DeadLetterReplayProperties.class)
public class DlqRecoveryToolApplication {
    private static final Logger log = LoggerFactory.getLogger(DlqRecoveryToolApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(DlqRecoveryToolApplication.class, args);
        System.exit(SpringApplication.exit(context));
    }

    @Bean
    ApplicationRunner replayDeadLetters(RabbitTemplate rabbitTemplate, DeadLetterReplayProperties properties) {
        return arguments -> {
            properties.validate();
            Integer replayed = rabbitTemplate.execute(channel -> {
                try {
                    return new DeadLetterReplayService().replay(channel, properties.maxMessages(),
                            properties.confirmTimeout().toMillis());
                } catch (Exception failure) {
                    throw new AmqpException("Dead-letter replay stopped; current message was requeued", failure);
                }
            });
            log.info("Dead-letter recovery completed: replayed={} requestedLimit={}",
                    replayed == null ? 0 : replayed, properties.maxMessages());
        };
    }
}
