package com.amatrix.sic.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = CloudResourceServerSecurityTest.TestApplication.class, properties = {
        "sic.security.jwk-set-uri=http://localhost/unused",
        "sic.security.issuer=sic-iam",
        "sic.security.audience=sic-api"
})
@AutoConfigureMockMvc
class CloudResourceServerSecurityTest {
    private final MockMvc mvc;

    @Autowired
    CloudResourceServerSecurityTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void apiAcceptsBearerJwtAndRejectsAnonymousOrBasicAuthentication() throws Exception {
        mvc.perform(get("/api/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required"));

        mvc.perform(get("/api/test").with(httpBasic("user", "password")))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/test").with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isOk());
    }

    @Test
    void internalEndpointsRequireAnIamServiceScope() throws Exception {
        mvc.perform(get("/internal/test").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mvc.perform(get("/internal/test").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_internal"))))
                .andExpect(status().isOk());
    }

    @Test
    void exposesOnlyTheRequiredActuatorPaths() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(jwt())).andExpect(status().isForbidden());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({CloudResourceServerAutoConfiguration.class, TestEndpoints.class})
    static class TestApplication {
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/test", "/internal/test", "/actuator/health", "/actuator/prometheus", "/actuator/env"})
        String ok() {
            return "ok";
        }
    }
}
