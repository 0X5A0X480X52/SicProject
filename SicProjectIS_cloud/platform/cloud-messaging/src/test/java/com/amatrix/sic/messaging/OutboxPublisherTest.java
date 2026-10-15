package com.amatrix.sic.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxPublisherTest {
    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final OutboxProperties properties = new OutboxProperties("sic.events", 10, 1000, true, 1000, 30, 300);

    @Test
    void marksPublishedOnlyAfterPositiveBrokerConfirmation() {
        OutboxRecord event = event();
        when(repository.claimPending(eq(10), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq("sic.events"), eq("workflow.changed"), eq(event.payloadJson()),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(repository, rabbit, properties).publishPending();

        verify(repository).markPublished(eq(event.eventId()), anyString());
        verify(repository, never()).markFailed(eq(event.eventId()), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void schedulesRetryWhenBrokerRejectsMessage() {
        OutboxRecord event = event();
        when(repository.claimPending(eq(10), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).convertAndSend(eq("sic.events"), eq("workflow.changed"), eq(event.payloadJson()),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(repository, rabbit, properties).publishPending();

        verify(repository, never()).markPublished(eq(event.eventId()), anyString());
        verify(repository).markFailed(eq(event.eventId()), anyString(), anyString(), any(Instant.class));
    }

    private static OutboxRecord event() {
        return new OutboxRecord(UUID.randomUUID(), "MODULE_STATE_CHANGED", "workflow.changed", "{}",
                Instant.now(), 0);
    }
}
