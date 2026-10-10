package com.amatrix.sic.project;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.service-client")
public record ServiceClientProperties(String bootstrapSecret) {
}
