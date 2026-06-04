package com.amatrix.sicprojectis_backend.auth;

import java.util.List;
import java.util.Objects;

import com.amatrix.sicprojectis_backend.auth.dto.CurrentUserResponse;
import com.amatrix.sicprojectis_backend.auth.dto.LoginResponse;
import com.amatrix.sicprojectis_backend.auth.dto.RegisterRequest;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.security.JwtTokenService;
import com.amatrix.sicprojectis_backend.system.dao.AppUserDao;
import com.amatrix.sicprojectis_backend.system.dao.PermissionDao;
import com.amatrix.sicprojectis_backend.system.dao.UserRoleDetailViewDao;
import com.amatrix.sicprojectis_backend.system.entity.AppUser;
import com.amatrix.sicprojectis_backend.system.entity.UserRoleDetailView;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final AppUserDao appUserDao;
    private final UserRoleDetailViewDao userRoleDetailViewDao;
    private final PermissionDao permissionDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(AppUserDao appUserDao, UserRoleDetailViewDao userRoleDetailViewDao, PermissionDao permissionDao,
            PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.appUserDao = appUserDao;
        this.userRoleDetailViewDao = userRoleDetailViewDao;
        this.permissionDao = permissionDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(String username, String password) {
        AppUser user = appUserDao.selectByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        CurrentUserResponse currentUser = currentUser(user.getUserId());
        JwtTokenService.TokenIssueResult token = jwtTokenService.issueToken(
                currentUser.userId(), currentUser.username(), currentUser.roleCodes());
        return new LoginResponse(token.token(), token.expiresAt(), currentUser);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = normalizeRequired(request.username(), "Username is required");
        String password = requirePresent(request.password(), "Password is required");
        String realName = normalizeOptional(request.realName());
        if (password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        if (appUserDao.selectByUsername(username) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRealName(realName == null ? username : realName);
        user.setDeptId(request.deptId());
        user.setPhone(normalizeOptional(request.phone()));
        user.setEmail(normalizeOptional(request.email()));
        user.setEnabled(true);
        appUserDao.insert(user);

        CurrentUserResponse currentUser = currentUser(user.getUserId());
        JwtTokenService.TokenIssueResult token = jwtTokenService.issueToken(
                currentUser.userId(), currentUser.username(), currentUser.roleCodes());
        return new LoginResponse(token.token(), token.expiresAt(), currentUser);
    }

    public CurrentUserResponse currentUser(AuthenticatedUser authenticatedUser) {
        return currentUser(authenticatedUser.userId());
    }

    public CurrentUserResponse currentUser(Long userId) {
        List<UserRoleDetailView> roleDetails = userRoleDetailViewDao.selectByUserId(userId);
        if (roleDetails.isEmpty()) {
            AppUser user = appUserDao.selectById(userId);
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is disabled or missing");
            }
            return new CurrentUserResponse(
                    user.getUserId(),
                    user.getUsername(),
                    user.getRealName(),
                    user.getDeptId(),
                    null,
                    List.of(),
                    permissionDao.selectPermissionCodesByUserId(userId));
        }

        UserRoleDetailView first = roleDetails.getFirst();
        List<String> roleCodes = roleDetails.stream()
                .map(UserRoleDetailView::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new CurrentUserResponse(
                first.getUserId(),
                first.getUsername(),
                first.getRealName(),
                first.getDeptId(),
                first.getDeptName(),
                roleCodes,
                permissionDao.selectPermissionCodesByUserId(userId));
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String requirePresent(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
