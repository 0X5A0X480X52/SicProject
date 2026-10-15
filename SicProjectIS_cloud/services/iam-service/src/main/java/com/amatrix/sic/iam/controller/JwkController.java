package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.security.RsaKeyProvider;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /.well-known/jwks.json，发布 RSA 公钥的 JWK 参数供服务与 Gateway 验签。
 */
@RestController
public class JwkController {
    private final RsaKeyProvider keys;

    public JwkController(RsaKeyProvider keys) {
        this.keys = keys;
    }

    /**
     * {@code GET /.well-known/jwks.json}：发布 JWT 验签所需的 RSA 公钥集合。
     * <p>公开接口，返回标准 JWKS JSON（keys 数组及 kid、kty、alg、n、e），不使用 ApiResponse 包装。
     * <p>Gateway 和其他服务据此选择公钥校验签名；响应仅包含公钥参数，不包含私钥或签发凭据。
     */
    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> keys() {
        return Map.of("keys", java.util.List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", keys.keyId(),
                "n", base64(keys.publicKey().getModulus()),
                "e", base64(keys.publicKey().getPublicExponent()))));
    }

    private static String base64(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
