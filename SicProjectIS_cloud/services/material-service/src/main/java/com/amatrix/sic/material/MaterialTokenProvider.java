package com.amatrix.sic.material;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MaterialTokenProvider{
    private final MaterialTokenClient client;private final MaterialClientProperties properties;private volatile MaterialTokenClient.Token cached;
    public MaterialTokenProvider(MaterialTokenClient client,MaterialClientProperties properties){this.client=client;this.properties=properties;}
    public synchronized String authorization(){if(cached==null||cached.expiresAt().isBefore(Instant.now().plusSeconds(60))){var r=client.token(properties.bootstrapSecret(),"material-service");if(!r.success()||r.data()==null)throw new IllegalStateException("IAM did not issue a material-service token");cached=r.data();}return "Bearer "+cached.token();}
}
