package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.security.RsaKeyProvider;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwkController {
    private final RsaKeyProvider keys;

    public JwkController(RsaKeyProvider keys) {
        this.keys = keys;
    }

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
