package com.amatrix.sic.iam.service;

import com.amatrix.sic.iam.dao.IamRepository;
import com.amatrix.sic.iam.dto.IamDtos;
import com.amatrix.sic.iam.security.JwtTokenIssuer;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.iam.dto.IamDtos.LoginResponse;
import com.amatrix.sic.iam.dto.IamDtos.RegisterRequest;
import com.amatrix.sic.iam.dto.IamDtos.UserAccount;

/**
 * 规范化用户名、验证 BCrypt 密码与启用状态、注册用户、读取当前用户、签发登录响应、维护 Redis 授权版本和注销黑名单。
 */
@Service
public class AuthApplicationService {
    private final IamRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer tokenIssuer;
    private final StringRedisTemplate redis;
    private final SessionVersionService versions;

    public AuthApplicationService(IamRepository repository, PasswordEncoder passwordEncoder,
            JwtTokenIssuer tokenIssuer, StringRedisTemplate redis, SessionVersionService versions) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.redis = redis;
        this.versions = versions;
    }

    /**
     * 验证密码及账号状态，再签发当前身份的登录结果。发布权限版本失败会阻止正常登录响应，不能把旧角色当成成功登录。
     */
    public LoginResponse login(String username, String password) {
        UserAccount user = repository.findByUsername(normalize(username));
        if (user == null || !user.enabled() || password == null
                || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return response(user);
    }

    /**
     * 创建账号并返回登录结果；注册请求可携带部门，后续申请仍需验证当前启用部门。
     * 注册本身不授予高级审批或专家角色，这些身份由管理和资格审批取得。
     */
    @Transactional(isolation=Isolation.READ_COMMITTED)
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

    /**
     * 按当前 token 的 jti 写入撤销标记，保存到 token 过期时间；不通过这个入口撤销用户的其他 token。
     */
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
        versions.publishLogin(user.userId(), user.authVersion());
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
