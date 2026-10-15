package com.amatrix.sic.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Project 的 sic.service-client.bootstrapSecret 配置。
 */
@ConfigurationProperties("sic.service-client")
public record ServiceClientProperties(String bootstrapSecret) {
}
