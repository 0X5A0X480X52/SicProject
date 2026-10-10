package com.amatrix.sic.iam;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.security.JsonSecurityHandlers;
import com.amatrix.sic.security.JwtAuthoritiesConverter;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = IamSecurityConfigTest.TestApplication.class, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class IamSecurityConfigTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void apiAcceptsJwtAndRejectsAnonymousOrBasicAuthentication() throws Exception {
        mvc.perform(get("/api/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/test").with(httpBasic("user", "password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test").with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isOk());
    }

    @Test
    void onlyBootstrapTokenEndpointIsPublicInsideInternalNamespace() throws Exception {
        mvc.perform(post("/internal/tokens/service")).andExpect(status().isOk());
        mvc.perform(get("/internal/test").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/internal/test").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_internal"))))
                .andExpect(status().isOk());
    }

    @Test
    void exposesJwkAndRequiredMonitoringEndpointsButNotOtherActuatorData() throws Exception {
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(jwt())).andExpect(status().isForbidden());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(IamJwtProperties.class)
    @Import({IamSecurityConfig.class, TestEndpoints.class})
    static class TestApplication {
        @Bean
        RsaKeyProvider rsaKeyProvider(IamJwtProperties properties) {
            return new RsaKeyProvider(properties);
        }

        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(new JwtAuthoritiesConverter());
            return converter;
        }

        @Bean
        JsonSecurityHandlers jsonSecurityHandlers(ObjectMapper objectMapper) {
            return new JsonSecurityHandlers(objectMapper);
        }
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/test", "/internal/test", "/.well-known/jwks.json", "/actuator/health/readiness",
                "/actuator/prometheus", "/actuator/env"})
        String get() {
            return "ok";
        }

        @PostMapping("/internal/tokens/service")
        String token() {
            return "ok";
        }
    }
}
