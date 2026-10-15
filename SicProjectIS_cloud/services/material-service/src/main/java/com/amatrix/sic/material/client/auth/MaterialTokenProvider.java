package com.amatrix.sic.material.client.auth;

import com.amatrix.sic.material.client.MaterialTokenClient;
import com.amatrix.sic.material.config.properties.MaterialClientProperties;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 缓存内部 Token，过期前 60 秒刷新，提供 Bearer Header。
 */
@Component
public class MaterialTokenProvider{
    private final MaterialTokenClient client;private final MaterialClientProperties properties;private volatile MaterialTokenClient.Token cached;
    public MaterialTokenProvider(MaterialTokenClient client,MaterialClientProperties properties){this.client=client;this.properties=properties;}

    /**
     * 返回 material-service 调用 Project 内部鉴权接口使用的完整 Bearer Header。
     * <p>首次使用或 token 距离到期不足 60 秒时同步向 IAM 兑换；其余调用复用本实例缓存。
     * 兑换失败直接抛出异常，材料读写不会因 IAM 不可用而放行。
     */
    public synchronized String authorization(){if(cached==null||cached.expiresAt().isBefore(Instant.now().plusSeconds(60))){var r=client.token(properties.bootstrapSecret(),"material-service");if(!r.success()||r.data()==null)throw new IllegalStateException("IAM did not issue a material-service token");cached=r.data();}return "Bearer "+cached.token();}
}
