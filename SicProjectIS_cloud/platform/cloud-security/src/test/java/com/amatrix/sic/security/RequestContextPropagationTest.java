package com.amatrix.sic.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestTemplate;

class RequestContextPropagationTest {
    @Test
    void evidenceIsOptInAndExcludesCredentialsAndQueryParameters() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RequestLoggingContextFilter.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start(); logger.addAppender(capture);
        try {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setQueryString("token=query-secret");
            request.addHeader("Authorization", "Bearer header-secret");
            request.addHeader("X-Trace-Id", "valid-trace-123");
            request.setContent("{\"password\":\"body-secret\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new RequestLoggingContextFilter().doFilter(request, new MockHttpServletResponse(), (a, b) -> {});
            assertThat(capture.list).isEmpty();
            new RequestLoggingContextFilter(true, "iam-service", "replica-2")
                    .doFilter(request, new MockHttpServletResponse(), (a, b) -> {});
            assertThat(capture.list).hasSize(1);
            assertThat(capture.list.getFirst().getFormattedMessage())
                    .contains("service=iam-service instance=replica-2 trace=valid-trace-123", "path=/api/auth/login status=200")
                    .doesNotContain("query-secret", "header-secret", "body-secret", "Authorization");
        } finally {
            logger.detachAppender(capture); capture.stop();
        }
    }

    @AfterEach
    void cleanThreadLocals() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void addsBoundedIdentifiersAndRestoresTheCallingThreadsMdc() throws Exception {
        MDC.put("existing", "keep-me");
        var request = new MockHttpServletRequest();
        request.addHeader(RequestLoggingContextFilter.TRACE_HEADER, "trace-safe-123");
        request.addHeader(RequestLoggingContextFilter.SAGA_HEADER, "saga_7");
        request.addHeader(RequestLoggingContextFilter.EVENT_HEADER, "invalid event id");
        request.setUserPrincipal(jwt("42"));
        var response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> inside = new AtomicReference<>();

        new RequestLoggingContextFilter().doFilter(request, response,
                (incoming, outgoing) -> inside.set(MDC.getCopyOfContextMap()));

        assertThat(response.getHeader(RequestLoggingContextFilter.TRACE_HEADER)).isEqualTo("trace-safe-123");
        assertThat(request.getAttribute(RequestLoggingContextFilter.TRACE_ATTRIBUTE)).isEqualTo("trace-safe-123");
        assertThat(request.getAttribute(RequestLoggingContextFilter.SAGA_ATTRIBUTE)).isEqualTo("saga_7");
        assertThat(request.getAttribute(RequestLoggingContextFilter.EVENT_ATTRIBUTE)).isNull();
        assertThat(inside.get()).containsEntry("traceId", "trace-safe-123")
                .containsEntry("sagaId", "saga_7").containsEntry("userId", "42")
                .doesNotContainKey("eventId");
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("existing", "keep-me"));
    }

    @Test
    void replacesAnInvalidIncomingTraceIdentifier() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestLoggingContextFilter.TRACE_HEADER, "bad trace");
        var response = new MockHttpServletResponse();

        new RequestLoggingContextFilter().doFilter(request, response, (incoming, outgoing) -> {
        });

        assertThat(response.getHeader(RequestLoggingContextFilter.TRACE_HEADER))
                .matches("[0-9a-f-]{36}").isNotEqualTo("bad trace");
    }

    @Test
    void feignRelaysOnlySanitizedRequestAttributes() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestLoggingContextFilter.TRACE_ATTRIBUTE, "trace-safe-123");
        request.setAttribute(RequestLoggingContextFilter.SAGA_ATTRIBUTE, "saga-safe");
        request.addHeader(RequestLoggingContextFilter.EVENT_HEADER, "raw value must not propagate");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        new FeignTracePropagationAutoConfiguration().sicTraceRequestInterceptor().apply(template);

        assertThat(template.headers().get(RequestLoggingContextFilter.TRACE_HEADER))
                .containsExactly("trace-safe-123");
        assertThat(template.headers().get(RequestLoggingContextFilter.SAGA_HEADER)).containsExactly("saga-safe");
        assertThat(template.headers()).doesNotContainKey(RequestLoggingContextFilter.EVENT_HEADER);
    }

    private static JwtAuthenticationToken jwt(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
