package com.amatrix.sic.material;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.service-client")
public record MaterialClientProperties(String bootstrapSecret){}
