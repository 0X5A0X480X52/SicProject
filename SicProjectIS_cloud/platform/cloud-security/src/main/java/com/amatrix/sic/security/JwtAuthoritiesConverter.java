package com.amatrix.sic.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : strings(jwt.getClaim("roles"))) {
            authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
        }
        for (String permission : strings(jwt.getClaim("permissions"))) {
            String authority = permission.startsWith("PERM_")
                    ? permission
                    : "PERM_" + permission.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(java.util.Locale.ROOT);
            authorities.add(new SimpleGrantedAuthority(authority));
        }
        for (String scope : strings(jwt.getClaim("scope"))) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        return authorities;
    }

    private List<String> strings(Object value) {
        if (value instanceof String string) {
            return List.of(string.split(" "));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
