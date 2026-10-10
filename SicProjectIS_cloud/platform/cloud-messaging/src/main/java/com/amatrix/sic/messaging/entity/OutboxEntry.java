package com.amatrix.sic.messaging.entity;

import java.time.Instant;

public record OutboxEntry(String eventId,String eventType,String routingKey,String payloadJson,Instant createdAt,int attempts) {}
