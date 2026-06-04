package com.amatrix.sicprojectis_backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtTokenService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final JwtProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC(), new ObjectMapper());
    }

    JwtTokenService(JwtProperties properties, Clock clock) {
        this(properties, clock, new ObjectMapper());
    }

    JwtTokenService(JwtProperties properties, Clock clock, ObjectMapper objectMapper) {
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public TokenIssueResult issueToken(Long userId, String username, List<String> roleCodes) {
        Instant expiresAt = Instant.now(clock).plus(properties.jwtExpirationMinutes(), ChronoUnit.MINUTES);
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "sub", userId,
                "username", username,
                "roles", roleCodes,
                "exp", expiresAt.getEpochSecond()));
        String unsignedToken = header + "." + payload;
        return new TokenIssueResult(unsignedToken + "." + sign(unsignedToken), expiresAt);
    }

    public AuthenticatedUser parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        String payload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> payloadClaims = parseJson(payload);
        long expiresAt = ((Number) payloadClaims.get("exp")).longValue();
        if (Instant.now(clock).getEpochSecond() >= expiresAt) {
            throw new IllegalArgumentException("Token expired");
        }

        Long userId = ((Number) payloadClaims.get("sub")).longValue();
        String username = (String) payloadClaims.get("username");
        @SuppressWarnings("unchecked")
        List<String> roleCodes = (List<String>) payloadClaims.getOrDefault("roles", List.of());
        return new AuthenticatedUser(userId, username, roleCodes);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign JWT", ex);
        }
    }

    private static String encode(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeJson(Map<String, ?> value) {
        try {
            return encode(objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encode JWT payload", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }

    private Map<String, Object> parseJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid token payload", ex);
        }
    }

    public record TokenIssueResult(String token, Instant expiresAt) {
    }
}
