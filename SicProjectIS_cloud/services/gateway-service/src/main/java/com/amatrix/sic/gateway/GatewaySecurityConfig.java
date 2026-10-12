package com.amatrix.sic.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

import com.amatrix.sic.security.AudienceValidator;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * WebFlux 安全链：JWT 校验、登录注册放行、内部路径拒绝、SSE token 转换与监控端点放行。
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(GatewaySecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new AudienceValidator(properties.audience())));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http,
            SessionValidityAuthorizationManager sessionValidity,
            ReactiveJwtDecoder jwtDecoder,
            ObjectMapper objectMapper) {
        ServerAuthenticationEntryPoint unauthorized = (exchange, ex) ->
                writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication is required");
        ServerAccessDeniedHandler forbidden = (exchange, ex) -> {
            if (Boolean.TRUE.equals(exchange.getAttribute(SessionValidityAuthorizationManager.UNAVAILABLE)))
                return writeError(exchange, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                        "Session authorization is temporarily unavailable");
            boolean invalidApiSession = exchange.getRequest().getPath().value().startsWith("/api/");
            return invalidApiSession
                    ? writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication is required")
                    : writeError(exchange, objectMapper, HttpStatus.FORBIDDEN, "Access denied");
        };

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/actuator/**").denyAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/api/**").access(sessionValidity)
                        .anyExchange().permitAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }

    private static Mono<Void> writeError(
            org.springframework.web.server.ServerWebExchange exchange,
            ObjectMapper objectMapper,
            HttpStatus status,
            String message) {
        return GatewayErrorWriter.write(exchange, objectMapper, status, message);
    }
}
