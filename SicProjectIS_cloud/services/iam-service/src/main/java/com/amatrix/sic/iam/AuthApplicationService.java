package com.amatrix.sic.iam;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.iam.IamDtos.LoginResponse;
import com.amatrix.sic.iam.IamDtos.RegisterRequest;
import com.amatrix.sic.iam.IamDtos.UserAccount;

@Service
public class AuthApplicationService {
    private final IamRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer tokenIssuer;
    private final StringRedisTemplate redis;

    public AuthApplicationService(IamRepository repository, PasswordEncoder passwordEncoder,
            JwtTokenIssuer tokenIssuer, StringRedisTemplate redis) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.redis = redis;
    }

    public LoginResponse login(String username, String password) {
        UserAccount user = repository.findByUsername(normalize(username));
        if (user == null || !user.enabled() || password == null
                || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return response(user);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = required(request.username(), "Username is required");
        String password = requiredRaw(request.password(), "Password is required");
        if (password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        if (repository.findByUsername(username) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        try {
            long userId = repository.insertUser(username, passwordEncoder.encode(password),
                    valueOr(request.realName(), username), request.deptId(), normalize(request.phone()),
                    normalize(request.email()));
            return response(repository.findById(userId));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
    }

    public UserAccount current(Long userId) {
        UserAccount user = repository.findById(userId);
        if (user == null || !user.enabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is disabled or missing");
        }
        return user;
    }

    public void logout(String tokenId, Instant expiresAt) {
        if (tokenId == null || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redis.opsForValue().set("sic:jwt:revoked:" + tokenId, "1", ttl);
        }
    }

    private LoginResponse response(UserAccount user) {
        JwtTokenIssuer.IssuedToken token = tokenIssuer.issue(user);
        redis.opsForValue().set("sic:jwt:version:" + user.userId(), Long.toString(user.authVersion()));
        return new LoginResponse(token.token(), token.expiresAt(), user.currentUser());
    }

    private static String required(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return normalized;
    }

    private static String requiredRaw(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value;
    }

    private static String valueOr(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
