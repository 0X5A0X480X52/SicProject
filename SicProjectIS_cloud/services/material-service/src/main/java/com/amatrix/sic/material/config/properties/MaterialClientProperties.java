package com.amatrix.sic.material.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.service-client")
public record MaterialClientProperties(String bootstrapSecret){}
