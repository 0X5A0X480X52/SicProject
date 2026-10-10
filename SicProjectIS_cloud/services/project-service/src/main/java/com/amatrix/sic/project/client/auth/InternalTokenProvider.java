package com.amatrix.sic.project.client.auth;

import com.amatrix.sic.project.client.ServiceTokenClient;
import com.amatrix.sic.project.config.properties.ServiceClientProperties;

import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class InternalTokenProvider {
    private final ServiceTokenClient client;
    private final ServiceClientProperties properties;
    private volatile ServiceTokenClient.TokenResponse cached;

    public InternalTokenProvider(ServiceTokenClient client, ServiceClientProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public synchronized String authorization() {
        if (cached == null || cached.expiresAt().isBefore(Instant.now().plusSeconds(60))) {
            var response = client.token(properties.bootstrapSecret(), "project-service");
            if (!response.success() || response.data() == null) {
                throw new IllegalStateException("IAM did not issue a project-service token");
            }
            cached = response.data();
        }
        return "Bearer " + cached.token();
    }
}
