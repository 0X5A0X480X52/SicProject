package com.amatrix.sic.workflow.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Workflow 的 sic.service-client.bootstrapSecret 配置。
 */
@ConfigurationProperties("sic.service-client")
public record WorkflowClientProperties(String bootstrapSecret) {
}
