package com.amatrix.sic.workflow.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.service-client")
public record WorkflowClientProperties(String bootstrapSecret) {
}
