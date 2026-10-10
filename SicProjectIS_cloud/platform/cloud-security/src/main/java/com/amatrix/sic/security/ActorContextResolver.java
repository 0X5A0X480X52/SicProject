package com.amatrix.sic.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.amatrix.sic.contracts.ActorContext;

/** Converts the method-security authentication boundary into an immutable domain actor. */
public final class ActorContextResolver {
    public ActorContext from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) return null;
        try {
            return ActorContextFactory.from(token.getToken());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
