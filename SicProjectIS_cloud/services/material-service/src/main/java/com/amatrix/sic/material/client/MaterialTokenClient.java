package com.amatrix.sic.material.client;

import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import com.amatrix.sic.contracts.ApiResponse;

@FeignClient(name="iam-service",contextId="materialTokenClient")
public interface MaterialTokenClient{
    @PostMapping("/internal/tokens/service") ApiResponse<Token> token(@RequestHeader("X-Service-Bootstrap-Secret")String secret,@RequestParam("serviceName")String serviceName);
    record Token(String token,Instant expiresAt,String tokenId){}
}
