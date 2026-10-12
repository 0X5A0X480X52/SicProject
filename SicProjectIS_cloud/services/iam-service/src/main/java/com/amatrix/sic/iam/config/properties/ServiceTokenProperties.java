package com.amatrix.sic.iam.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * sic.service-token 的 bootstrapSecret 配置。
 */
@ConfigurationProperties("sic.service-token")
public record ServiceTokenProperties(String bootstrapSecret) {
}
