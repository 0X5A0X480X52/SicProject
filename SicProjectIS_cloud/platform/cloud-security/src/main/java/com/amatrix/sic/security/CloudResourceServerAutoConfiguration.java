package com.amatrix.sic.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import tools.jackson.databind.ObjectMapper;

/**
 * Servlet 安全自动装配：JWT decoder/converter、方法安全、401/403 处理、异常处理、Actor 解析、请求上下文 Filter；内部接口要求 SCOPE_internal。
 */
@AutoConfiguration
@EnableMethodSecurity
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CloudSecurityProperties.class)
public class CloudResourceServerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(CloudSecurityProperties properties) {
        if (properties.jwkSetUri() == null || properties.jwkSetUri().isBlank()) {
            throw new IllegalStateException("sic.security.jwk-set-uri must be configured");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new AudienceValidator(properties.audience())));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtAuthoritiesConverter());
        converter.setPrincipalClaimName("username");
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    JsonSecurityHandlers jsonSecurityHandlers(ObjectMapper objectMapper) {
        return new JsonSecurityHandlers(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiExceptionHandler apiExceptionHandler() {
        return new ApiExceptionHandler();
    }

    @Bean("actorContextResolver")
    @ConditionalOnMissingBean
    ActorContextResolver actorContextResolver() {
        return new ActorContextResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    FilterRegistrationBean<RequestLoggingContextFilter> requestLoggingContextFilter(org.springframework.core.env.Environment environment) {
        FilterRegistrationBean<RequestLoggingContextFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingContextFilter(
                        environment.getProperty("sic.observability.load-balancer-evidence-enabled", Boolean.class, false),
                        environment.getProperty("spring.application.name", "unknown"),
                        environment.getProperty("HOSTNAME", "unknown")));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JsonSecurityHandlers handlers) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/internal/**").hasAuthority("SCOPE_internal")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .httpBasic(httpBasic -> httpBasic.disable());
        return http.build();
    }
}
