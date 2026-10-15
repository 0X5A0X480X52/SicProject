package com.amatrix.sic.gateway.config;

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

import com.amatrix.sic.gateway.config.properties.GatewaySecurityProperties;
import com.amatrix.sic.gateway.error.GatewayErrorWriter;
import com.amatrix.sic.gateway.security.SessionValidityAuthorizationManager;
import com.amatrix.sic.security.AudienceValidator;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * WebFlux 安全链：JWT 校验、登录注册放行、内部路径拒绝、SSE token 转换与监控端点放行。
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /**
     * 配置网关的 JWT 解码器，使用 JWK Set URI 获取公钥，并设置验证器。
     *
     * @param properties 网关安全配置属性
     * @return ReactiveJwtDecoder 实例
     */
    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(GatewaySecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new AudienceValidator(properties.audience())));
        return decoder;
    }

    /**
     * 配置网关的安全过滤链，定义路径权限、异常处理和 JWT 解码器。
     *
     * @param http          ServerHttpSecurity 实例
     * @param sessionValidity SessionValidityAuthorizationManager 实例
     * @param jwtDecoder    ReactiveJwtDecoder 实例
     * @param objectMapper  ObjectMapper 实例
     * @return SecurityWebFilterChain 实例
     */
    @Bean
    SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http,
            SessionValidityAuthorizationManager sessionValidity,
            ReactiveJwtDecoder jwtDecoder,
            ObjectMapper objectMapper) {

        // 处理未认证的请求
        ServerAuthenticationEntryPoint unauthorized = (exchange, ex) ->
                writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication is required");
        
        // 处理访问被拒绝的请求
        ServerAccessDeniedHandler forbidden = (exchange, ex) -> {
            // 如果会话授权管理器标记为不可用，则返回 503 服务不可用
            if (Boolean.TRUE.equals(exchange.getAttribute(SessionValidityAuthorizationManager.UNAVAILABLE)))
                return writeError(exchange, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                        "Session authorization is temporarily unavailable");
            // 如果请求路径以 /api/ 开头，则返回 401 未授权，否则返回 403 禁止访问
            boolean invalidApiSession = exchange.getRequest().getPath().value().startsWith("/api/");
            return invalidApiSession
                    ? writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication is required")
                    : writeError(exchange, objectMapper, HttpStatus.FORBIDDEN, "Access denied");
        };

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 配置路径权限规则
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/actuator/**").denyAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/api/**").access(sessionValidity)
                        .anyExchange().permitAll())
                // 配置 OAuth2 资源服务器，使用 JWT 解码器和异常处理器
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                // 处理异常 
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
