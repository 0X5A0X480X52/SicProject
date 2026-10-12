package com.amatrix.sic.material.config;

import com.amatrix.sic.material.config.properties.MaterialProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.minio.MinioClient;

/**
 * 根据 MaterialProperties 创建 MinioClient Bean。
 */
@Configuration
public class MinioConfiguration{
    @Bean MinioClient minioClient(MaterialProperties p){return MinioClient.builder().endpoint(p.endpoint()).credentials(p.accessKey(),p.secretKey()).build();}
}
