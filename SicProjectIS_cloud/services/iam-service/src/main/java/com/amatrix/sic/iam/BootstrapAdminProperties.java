package com.amatrix.sic.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password, String realName) {
}
