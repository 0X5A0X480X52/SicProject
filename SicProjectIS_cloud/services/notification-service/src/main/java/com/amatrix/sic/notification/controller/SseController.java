package com.amatrix.sic.notification.controller;

import com.amatrix.sic.notification.service.PendingNotificationDeliveryService;
import com.amatrix.sic.notification.sse.SseConnectionRegistry;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * GET /api/sse/subscribe，从 JWT 识别用户，注册 SSE 连接并补发该用户待投递消息。
 */
@RestController
@RequestMapping("/api/sse")
public class SseController{
    private final SseConnectionRegistry registry;private final PendingNotificationDeliveryService delivery;public SseController(SseConnectionRegistry registry,PendingNotificationDeliveryService delivery){this.registry=registry;this.delivery=delivery;}
    /**
     * {@code GET /api/sse/subscribe}：为当前账号建立业务通知的 SSE 长连接。
     * <p>账号由已验证 JWT subject 确定；返回 text/event-stream 的 SseEmitter，不使用 ApiResponse。
     * <p>先注册连接，再补发该账号待投递通知；连接事件和心跳由注册表维护，断线后客户端需要重连。
     * <p>通知用于提示重新查询，不能作为业务状态或权限凭据；重连也不保证恢复全部历史事件。
     */
    @GetMapping(value="/subscribe",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt){Long userId=Long.valueOf(jwt.getSubject());SseEmitter emitter=registry.subscribe(userId);delivery.replayTo(userId);return emitter;}
}
