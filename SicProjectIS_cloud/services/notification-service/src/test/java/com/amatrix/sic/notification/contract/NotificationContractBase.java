package com.amatrix.sic.notification.contract;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.amatrix.sic.notification.PendingNotificationDeliveryService;
import com.amatrix.sic.notification.SseConnectionRegistry;
import com.amatrix.sic.notification.SseController;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

public abstract class NotificationContractBase {
    @BeforeEach
    void setUpContractProvider() {
        SseConnectionRegistry registry = mock(SseConnectionRegistry.class);
        PendingNotificationDeliveryService delivery = mock(PendingNotificationDeliveryService.class);
        AtomicReference<SseEmitter> subscribed = new AtomicReference<>();

        when(registry.subscribe(eq(7L))).thenAnswer(invocation -> {
            SseEmitter emitter = new SseEmitter(5_000L);
            subscribed.set(emitter);
            emitter.send(SseEmitter.event().name("CONNECTED").data(Map.of("connected", true)));
            return emitter;
        });
        doAnswer(invocation -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("moduleInstanceId", 20);
            payload.put("state", "APPROVED");
            SseEmitter emitter = subscribed.get();
            emitter.send(SseEmitter.event().name("MODULE_STATE_CHANGED").data(payload));
            emitter.complete();
            return null;
        }).when(delivery).replayTo(7L);

        Jwt jwt = Jwt.withTokenValue("contract.jwt.token").header("alg", "none").subject("7")
                .claim("username", "bob").claim("roles", List.of("PROJECT_LEADER"))
                .claim("permissions", List.of()).claim("ver", 1L).claim("jti", "contract-jti")
                .issuedAt(Instant.parse("2029-01-02T03:04:05Z"))
                .expiresAt(Instant.parse("2030-01-02T03:04:05Z")).build();
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new SseController(registry, delivery))
                .setCustomArgumentResolvers(jwtResolver(jwt)).build());
    }

    private static HandlerMethodArgumentResolver jwtResolver(Jwt jwt) {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && Jwt.class.isAssignableFrom(parameter.getParameterType());
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory binderFactory) { return jwt; }
        };
    }
}
