package com.amatrix.sic.iam.bootstrap;

import com.amatrix.sic.iam.dao.IamRepository;
import com.amatrix.sic.iam.config.properties.BootstrapAdminProperties;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动时按配置创建不存在的管理员并赋 SYSTEM_ADMIN；缺少配置、密码短于 10 或已有同名用户时跳过。
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final BootstrapAdminProperties properties;
    private final IamRepository repository;
    private final PasswordEncoder encoder;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties, IamRepository repository,
            PasswordEncoder encoder) {
        this.properties = properties;
        this.repository = repository;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().length() < 10) {
            return;
        }
        repository.lockBootstrapRole();
        if (repository.findByUsername(properties.username().trim()) != null) return;
        long id = repository.insertUser(properties.username().trim(), encoder.encode(properties.password()),
                properties.realName() == null || properties.realName().isBlank() ? "系统管理员" : properties.realName(),
                1L, null, null);
        repository.assignRole(id, "SYSTEM_ADMIN");
    }
}
