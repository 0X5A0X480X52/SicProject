package com.amatrix.sic.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ReturnCallback;
import com.rabbitmq.client.ReturnListener;

import com.amatrix.sic.messaging.CloudMessagingAutoConfiguration;

class DeadLetterReplayServiceTest {
    private final DeadLetterReplayService service = new DeadLetterReplayService();

    @Test
    void republishesToOriginalRoutingKeyBeforeAcknowledging() throws Exception {
        Channel channel = mock(Channel.class);
        ReturnListener listener = mock(ReturnListener.class);
        when(channel.addReturnListener(any(com.rabbitmq.client.ReturnCallback.class))).thenReturn(listener);
        when(channel.basicGet(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE, false))
                .thenReturn(deadLetter(41L, "workflow.module-state-changed"))
                .thenReturn((GetResponse) null);

        assertThat(service.replay(channel, 10, 5_000)).isEqualTo(1);

        ArgumentCaptor<AMQP.BasicProperties> properties = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        InOrder ordered = inOrder(channel);
        ordered.verify(channel).basicPublish(eq("sic.events"), eq("workflow.module-state-changed"), eq(true),
                properties.capture(), eq("payload".getBytes(StandardCharsets.UTF_8)));
        ordered.verify(channel).waitForConfirmsOrDie(5_000);
        ordered.verify(channel).basicAck(41L, false);
        assertThat(properties.getValue().getHeaders()).containsEntry("x-sic-replay-count", 1)
                .containsKey("x-sic-replayed-at");
        assertThat(properties.getValue().getExpiration()).isNull();
        verify(channel).removeReturnListener(listener);
    }

    @Test
    void requeuesCurrentMessageWhenPublisherConfirmFails() throws Exception {
        Channel channel = mock(Channel.class);
        when(channel.addReturnListener(any(com.rabbitmq.client.ReturnCallback.class)))
                .thenReturn(mock(ReturnListener.class));
        when(channel.basicGet(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE, false))
                .thenReturn(deadLetter(42L, "material.reservation.confirm"));
        doThrow(new IOException("confirm failed")).when(channel).waitForConfirmsOrDie(5_000);

        assertThatThrownBy(() -> service.replay(channel, 1, 5_000)).isInstanceOf(IOException.class)
                .hasMessageContaining("confirm failed");
        verify(channel).basicNack(42L, false, true);
    }

    @Test
    void rejectsUnknownRoutesWithoutLosingTheMessage() throws Exception {
        Channel channel = mock(Channel.class);
        when(channel.addReturnListener(any(com.rabbitmq.client.ReturnCallback.class)))
                .thenReturn(mock(ReturnListener.class));
        when(channel.basicGet(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE, false))
                .thenReturn(deadLetter(43L, "untrusted.route"));

        assertThatThrownBy(() -> service.replay(channel, 1, 5_000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("allowlisted");
        verify(channel).basicNack(43L, false, true);
    }

    @Test
    void requeuesMandatoryPublishWhenNoConsumerRouteExists() throws Exception {
        Channel channel = mock(Channel.class);
        ReturnListener listener = mock(ReturnListener.class);
        AtomicReference<ReturnCallback> callback = new AtomicReference<>();
        when(channel.addReturnListener(any(ReturnCallback.class))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return listener;
        });
        when(channel.basicGet(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE, false))
                .thenReturn(deadLetter(44L, "workflow.module-state-changed"));
        org.mockito.Mockito.doAnswer(invocation -> {
            callback.get().handle(new Return(312, "NO_ROUTE", "sic.events",
                    "workflow.module-state-changed", invocation.getArgument(3), invocation.getArgument(4)));
            return null;
        }).when(channel).basicPublish(eq("sic.events"), eq("workflow.module-state-changed"), eq(true),
                any(AMQP.BasicProperties.class), any(byte[].class));

        assertThatThrownBy(() -> service.replay(channel, 1, 5_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unroutable");
        verify(channel).basicNack(44L, false, true);
        verify(channel, never()).basicAck(44L, false);
    }

    @Test
    void readsByteEncodedRoutingKeysFromRabbitHeaders() {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(Map.of(
                "x-death", List.of(Map.of("routing-keys",
                        new Object[] { "untrusted.route".getBytes(StandardCharsets.UTF_8),
                                "document.generation.requested".getBytes(StandardCharsets.UTF_8) })))).build();

        assertThat(DeadLetterReplayService.originalRoutingKey(properties))
                .isEqualTo("document.generation.requested");
    }

    private static GetResponse deadLetter(long deliveryTag, String routingKey) {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType("application/json").deliveryMode(2).expiration("1000").headers(Map.of(
                        "x-death", List.of(Map.of("exchange", "sic.events",
                                "routing-keys", List.of(routingKey)))))
                .build();
        return new GetResponse(new Envelope(deliveryTag, false, "sic.events.dlx", routingKey + ".dead"),
                properties, "payload".getBytes(StandardCharsets.UTF_8), 0);
    }
}
