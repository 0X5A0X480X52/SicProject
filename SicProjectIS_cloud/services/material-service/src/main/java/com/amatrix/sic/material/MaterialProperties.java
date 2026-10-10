package com.amatrix.sic.material;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sic.minio")
public record MaterialProperties(String endpoint,String accessKey,String secretKey,String bucket){
    public MaterialProperties{bucket=bucket==null||bucket.isBlank()?"sic-materials":bucket;}
}
