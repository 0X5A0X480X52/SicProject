package com.amatrix.sic.iam.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 初始管理员 username/password/realName 配置。
 */
@ConfigurationProperties("sic.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password, String realName) {
}
