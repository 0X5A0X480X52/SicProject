package com.amatrix.sic.project;

import com.amatrix.sic.project.policy.ProjectAccessPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.security.ActorContextResolver;
import com.amatrix.sic.security.CanAccessProject;

class MethodSecurityProxyTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void metaAnnotationIsEnforcedThroughTheSpringBeanProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            ProjectAccessPolicy policy = context.getBean(ProjectAccessPolicy.class);
            ActorContextResolver resolver = context.getBean(ActorContextResolver.class);
            when(resolver.from(any())).thenReturn(new ActorContext(42L, "actor", null, null,
                    java.util.List.of(), java.util.List.of(), "token", 1));
            when(policy.canAccess(any(), eq(1L))).thenReturn(true);
            when(policy.canAccess(any(), eq(2L))).thenReturn(false);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("actor", "n/a", java.util.List.of()));

            SecuredProbe probe = context.getBean(SecuredProbe.class);
            assertThat(AopUtils.isAopProxy(probe)).isTrue();
            assertThat(probe.read(1L)).isEqualTo("project-1");
            assertThatThrownBy(() -> probe.read(2L)).isInstanceOf(AuthorizationDeniedException.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean
        ProjectAccessPolicy projectAccessPolicy() {
            return mock(ProjectAccessPolicy.class);
        }

        @Bean("actorContextResolver")
        ActorContextResolver actorContextResolver() {
            return mock(ActorContextResolver.class);
        }

        @Bean
        SecuredProbe securedProbe() {
            return new SecuredProbe();
        }
    }

    static class SecuredProbe {
        @CanAccessProject
        public String read(Long projectId) {
            return "project-" + projectId;
        }
    }
}
