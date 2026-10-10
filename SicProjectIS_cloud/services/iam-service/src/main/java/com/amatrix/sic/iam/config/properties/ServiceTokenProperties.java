package com.amatrix.sic.iam.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.service-token")
public record ServiceTokenProperties(String bootstrapSecret) {
}
