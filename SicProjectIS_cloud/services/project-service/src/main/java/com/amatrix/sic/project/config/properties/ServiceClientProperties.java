package com.amatrix.sic.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.service-client")
public record ServiceClientProperties(String bootstrapSecret) {
}
