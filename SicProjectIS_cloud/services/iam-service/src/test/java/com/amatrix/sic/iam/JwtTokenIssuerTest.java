package com.amatrix.sic.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.amatrix.sic.iam.IamDtos.UserAccount;

class JwtTokenIssuerTest {

    @Test
    void issuesRsaSignedUserTokenWithRequiredIdentityAndAuthorizationClaims() {
        IamJwtProperties properties = new IamJwtProperties("https://iam.sic.test", "sic-api", 30, null, null);
        RsaKeyProvider keys = new RsaKeyProvider(properties);
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties, keys);
        UserAccount user = new UserAccount(42L, "alice", "unused", "Alice", 7L, "Science",
                "13800000000", "alice@example.test", true, 9L,
                List.of("PROJECT_LEADER"), List.of("project:create", "material:read"));

        JwtTokenIssuer.IssuedToken issued = issuer.issue(user);
        var jwt = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build().decode(issued.token());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", keys.keyId());
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://iam.sic.test");
        assertThat(jwt.getAudience()).containsExactly("sic-api");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getId()).isEqualTo(issued.tokenId()).isNotBlank();
        assertThat(jwt.getClaimAsString("username")).isEqualTo("alice");
        assertThat(jwt.getClaimAsString("realName")).isEqualTo("Alice");
        assertThat(((Number) jwt.getClaim("deptId")).longValue()).isEqualTo(7L);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("PROJECT_LEADER");
        assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("project:create", "material:read");
        assertThat(((Number) jwt.getClaim("ver")).longValue()).isEqualTo(9L);
        assertThat(((Number) jwt.getClaim("authVersion")).longValue()).isEqualTo(9L);
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void issuesInternalServiceTokenWithScopeAndDistinctKeyFingerprint() {
        IamJwtProperties properties = new IamJwtProperties("sic-iam", "sic-api", 5, null, null);
        RsaKeyProvider firstKeys = new RsaKeyProvider(properties);
        RsaKeyProvider secondKeys = new RsaKeyProvider(properties);
        var token = new JwtTokenIssuer(properties, firstKeys)
                .issueServiceToken("workflow-service", List.of("internal"));
        var jwt = NimbusJwtDecoder.withPublicKey(firstKeys.publicKey()).build().decode(token.token());

        assertThat(jwt.getSubject()).isEqualTo("0");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("workflow-service");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE");
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("internal");
        assertThat(firstKeys.keyId()).startsWith("sic-iam-").isNotEqualTo(secondKeys.keyId());
    }

    @Test
    void rejectsPartialOrMismatchedConfiguredKeyPairsAtStartup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = generator.generateKeyPair();
        var second = generator.generateKeyPair();
        String firstPrivate = Base64.getEncoder().encodeToString(first.getPrivate().getEncoded());
        String firstPublic = Base64.getEncoder().encodeToString(first.getPublic().getEncoded());
        String secondPublic = Base64.getEncoder().encodeToString(second.getPublic().getEncoded());

        assertThatThrownBy(() -> new RsaKeyProvider(
                new IamJwtProperties("sic-iam", "sic-api", 5, firstPrivate, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Both IAM RSA private and public keys must be configured");
        assertThatThrownBy(() -> new RsaKeyProvider(
                new IamJwtProperties("sic-iam", "sic-api", 5, firstPrivate, secondPublic)))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("IAM RSA public key does not match the private key");

        assertThat(new RsaKeyProvider(
                new IamJwtProperties("sic-iam", "sic-api", 5, firstPrivate, firstPublic)).keyId())
                .startsWith("sic-iam-");
    }
}
