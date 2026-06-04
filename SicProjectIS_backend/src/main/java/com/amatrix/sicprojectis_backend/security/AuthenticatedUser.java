package com.amatrix.sicprojectis_backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(Long userId, String username, List<String> roleCodes) {
    public Collection<? extends GrantedAuthority> authorities() {
        return roleCodes.stream()
                .map(roleCode -> new SimpleGrantedAuthority("ROLE_" + roleCode))
                .toList();
    }
}
