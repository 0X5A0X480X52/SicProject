package com.amatrix.sic.iam.controller.internal;

import com.amatrix.sic.iam.service.IamOverviewService;
import com.amatrix.sic.iam.dao.IamRepository;
import com.amatrix.sic.iam.config.properties.ServiceTokenProperties;
import com.amatrix.sic.iam.security.JwtTokenIssuer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;



import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.IamOverviewStats;
import com.amatrix.sic.contracts.UserSummary;

@RestController
@RequestMapping("/internal")
public class InternalIdentityController {
    private final IamRepository repository;
    private final JwtTokenIssuer tokenIssuer;
    private final ServiceTokenProperties tokenProperties;
    private final IamOverviewService overview;

    public InternalIdentityController(IamRepository repository, JwtTokenIssuer tokenIssuer,
            ServiceTokenProperties tokenProperties, IamOverviewService overview) {
        this.repository = repository;
        this.tokenIssuer = tokenIssuer;
        this.tokenProperties = tokenProperties;
        this.overview = overview;
    }

    @PostMapping("/tokens/service")
    ApiResponse<JwtTokenIssuer.IssuedToken> serviceToken(
            @RequestHeader("X-Service-Bootstrap-Secret") String secret,
            @RequestParam String serviceName) {
        if (tokenProperties.bootstrapSecret() == null || !MessageDigest.isEqual(
                tokenProperties.bootstrapSecret().getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
        return ApiResponse.ok(tokenIssuer.issueServiceToken(serviceName, List.of("internal")));
    }

    @GetMapping("/users/{userId}/summary")
    ApiResponse<UserSummary> summary(@org.springframework.web.bind.annotation.PathVariable Long userId) {
        var users = repository.summaries(List.of(userId));
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return ApiResponse.ok(users.getFirst());
    }

    @PostMapping("/users/summaries")
    ApiResponse<List<UserSummary>> summaries(@org.springframework.web.bind.annotation.RequestBody List<Long> userIds) {
        return ApiResponse.ok(repository.summaries(userIds));
    }

    @GetMapping("/admin/overview-stats")
    ApiResponse<IamOverviewStats> overviewStats() {
        return ApiResponse.ok(overview.overview());
    }
}
