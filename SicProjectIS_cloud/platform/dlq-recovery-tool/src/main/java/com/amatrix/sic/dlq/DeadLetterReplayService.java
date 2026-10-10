package com.amatrix.sic.dlq;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ReturnListener;

import com.amatrix.sic.messaging.CloudMessagingAutoConfiguration;

public final class DeadLetterReplayService {
    private static final String EVENT_EXCHANGE = "sic.events";
    private static final Set<String> ALLOWED_ROUTING_KEYS = Set.of(
            "iam.changed",
            "workflow.project-lifecycle-changed",
            "workflow.expert-review-completed",
            "workflow.module-state-changed",
            "material.reservation.confirm",
            "document.generation.requested");

    public int replay(Channel channel, int limit, long confirmTimeoutMillis) throws Exception {
        channel.confirmSelect();
        AtomicReference<Return> returned = new AtomicReference<>();
        ReturnListener returnListener = channel.addReturnListener(returned::set);
        int replayed = 0;
        try {
            while (replayed < limit) {
                GetResponse response = channel.basicGet(CloudMessagingAutoConfiguration.DEAD_LETTER_QUEUE, false);
                if (response == null) break;
                long deliveryTag = response.getEnvelope().getDeliveryTag();
                try {
                    String routingKey = originalRoutingKey(response.getProps());
                    returned.set(null);
                    channel.basicPublish(EVENT_EXCHANGE, routingKey, true,
                            replayProperties(response.getProps()), response.getBody());
                    channel.waitForConfirmsOrDie(confirmTimeoutMillis);
                    Return unroutable = returned.get();
                    if (unroutable != null) {
                        throw new IllegalStateException("Replayed message was unroutable: "
                                + unroutable.getReplyCode() + " " + unroutable.getReplyText());
                    }
                    channel.basicAck(deliveryTag, false);
                    replayed++;
                } catch (Exception failure) {
                    channel.basicNack(deliveryTag, false, true);
                    throw failure;
                }
            }
            return replayed;
        } finally {
            channel.removeReturnListener(returnListener);
        }
    }

    static String originalRoutingKey(AMQP.BasicProperties properties) {
        Map<String, Object> headers = properties == null ? null : properties.getHeaders();
        Object rawDeaths = headers == null ? null : headers.get("x-death");
        if (rawDeaths instanceof List<?> deaths) {
            for (Object rawDeath : deaths) {
                if (!(rawDeath instanceof Map<?, ?> death)) continue;
                for (String routingKey : routingKeys(death.get("routing-keys"))) {
                    if (ALLOWED_ROUTING_KEYS.contains(routingKey)) return routingKey;
                }
            }
        }
        throw new IllegalArgumentException("Dead letter has no allowlisted original routing key");
    }

    private static List<String> routingKeys(Object raw) {
        if (raw instanceof List<?> values) return values.stream().map(DeadLetterReplayService::text).toList();
        if (raw instanceof Object[] values) return java.util.Arrays.stream(values)
                .map(DeadLetterReplayService::text).toList();
        return List.of();
    }

    private static String text(Object value) {
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return value == null ? null : value.toString();
    }

    private static AMQP.BasicProperties replayProperties(AMQP.BasicProperties original) {
        Map<String, Object> headers = new HashMap<>();
        if (original != null && original.getHeaders() != null) headers.putAll(original.getHeaders());
        int replayCount = headers.get("x-sic-replay-count") instanceof Number count ? count.intValue() : 0;
        headers.put("x-sic-replay-count", replayCount + 1);
        headers.put("x-sic-replayed-at", Instant.now().toString());
        AMQP.BasicProperties source = original == null ? new AMQP.BasicProperties.Builder().build() : original;
        return source.builder().headers(headers).expiration(null).build();
    }
}
