package com.amatrix.sic.iam.security;

import com.amatrix.sic.iam.config.properties.IamJwtProperties;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * 加载配置的 PKCS8/X509 RSA 密钥或生成临时 3072 位密钥；校验成对配置、匹配及至少 2048 位，生成公钥指纹 kid。
 */
@Component
public class RsaKeyProvider {
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId;

    public RsaKeyProvider(IamJwtProperties properties) {
        try {
            boolean hasPrivateKey = present(properties.privateKey());
            boolean hasPublicKey = present(properties.publicKey());
            if (hasPrivateKey != hasPublicKey) {
                throw new IllegalArgumentException("Both IAM RSA private and public keys must be configured");
            }
            if (hasPrivateKey) {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                privateKey = (RSAPrivateKey) factory.generatePrivate(
                        new PKCS8EncodedKeySpec(decode(properties.privateKey())));
                publicKey = (RSAPublicKey) factory.generatePublic(
                        new X509EncodedKeySpec(decode(properties.publicKey())));
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(3072);
                var pair = generator.generateKeyPair();
                privateKey = (RSAPrivateKey) pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
            }
            if (!privateKey.getModulus().equals(publicKey.getModulus())) {
                throw new IllegalArgumentException("IAM RSA public key does not match the private key");
            }
            if (publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("IAM RSA key must be at least 2048 bits");
            }
            keyId = keyId(publicKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load IAM RSA signing keys", ex);
        }
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public String keyId() {
        return keyId;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static byte[] decode(String value) {
        String normalized = value
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static String keyId(RSAPublicKey key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        return "sic-iam-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
    }
}
