package com.amatrix.sic.iam.controller;

import com.amatrix.sic.iam.service.AuthApplicationService;
import com.amatrix.sic.iam.dto.IamDtos;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.iam.dto.IamDtos.CurrentUser;
import com.amatrix.sic.iam.dto.IamDtos.LoginRequest;
import com.amatrix.sic.iam.dto.IamDtos.LoginResponse;
import com.amatrix.sic.iam.dto.IamDtos.RegisterRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthApplicationService service;

    public AuthController(AuthApplicationService service) {
        this.service = service;
    }

    @PostMapping("/login")
    ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(service.login(request.username(), request.password()));
    }

    @PostMapping("/register")
    ApiResponse<LoginResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(service.register(request));
    }

    @GetMapping("/me")
    ApiResponse<CurrentUser> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.current(Long.valueOf(jwt.getSubject())).currentUser());
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        service.logout(jwt.getId(), jwt.getExpiresAt());
        return ApiResponse.ok(null);
    }
}
