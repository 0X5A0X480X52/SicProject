package com.amatrix.sic.iam.security;

import com.amatrix.sic.iam.dto.IamDtos;
import com.amatrix.sic.iam.config.properties.IamJwtProperties;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.amatrix.sic.iam.dto.IamDtos.UserAccount;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

@Service
public class JwtTokenIssuer {
    private final IamJwtProperties properties;
    private final RsaKeyProvider keys;
    private final JwtEncoder encoder;

    public JwtTokenIssuer(IamJwtProperties properties, RsaKeyProvider keys) {
        this.properties = properties;
        this.keys = keys;
        RSAKey rsaKey = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID(keys.keyId())
                .build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    public IssuedToken issue(UserAccount user) {
        return issue(user.userId(), user.username(), user.authVersion(), user.roleCodes(), user.permissionCodes(),
                properties.audience(), List.of(), user.realName(), user.deptId(), user.deptName());
    }

    public IssuedToken issueServiceToken(String serviceName, List<String> scopes) {
        return issue(0L, serviceName, 1L, List.of("SERVICE"), List.of(), properties.audience(), scopes,
                serviceName, null, null);
    }

    private IssuedToken issue(Long subject, String username, long authVersion, List<String> roles,
            List<String> permissions, String audience, List<String> scopes) {
        return issue(subject, username, authVersion, roles, permissions, audience, scopes, username, null, null);
    }

    private IssuedToken issue(Long subject, String username, long authVersion, List<String> roles,
            List<String> permissions, String audience, List<String> scopes, String realName, Long deptId,
            String deptName) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.expirationMinutes(), ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(audience))
                .subject(subject.toString())
                .id(jti)
                .issuedAt(now)
                .notBefore(now.minusSeconds(5))
                .expiresAt(expiresAt)
                .claim("username", username)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("scope", scopes)
                .claim("ver", authVersion)
                .claim("authVersion", authVersion);
        if (realName != null) claims.claim("realName", realName);
        if (deptId != null) claims.claim("deptId", deptId);
        if (deptName != null) claims.claim("deptName", deptName);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.keyId()).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, expiresAt, jti);
    }

    public record IssuedToken(String token, Instant expiresAt, String tokenId) {
    }
}
