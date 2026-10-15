package com.amatrix.sic.material.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Material 的 sic.service-client.bootstrapSecret 配置。
 */
@ConfigurationProperties("sic.service-client")
public record MaterialClientProperties(String bootstrapSecret){}
