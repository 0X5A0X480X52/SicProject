package com.amatrix.sic.notification.entity;

public record PendingNotification(String eventId,Long userId,String eventName,String payloadJson) {}
