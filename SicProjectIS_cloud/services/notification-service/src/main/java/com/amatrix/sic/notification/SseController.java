package com.amatrix.sic.notification;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class SseController{
    private final SseConnectionRegistry registry;private final PendingNotificationDeliveryService delivery;public SseController(SseConnectionRegistry registry,PendingNotificationDeliveryService delivery){this.registry=registry;this.delivery=delivery;}
    @GetMapping(value="/subscribe",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt){Long userId=Long.valueOf(jwt.getSubject());SseEmitter emitter=registry.subscribe(userId);delivery.replayTo(userId);return emitter;}
}
