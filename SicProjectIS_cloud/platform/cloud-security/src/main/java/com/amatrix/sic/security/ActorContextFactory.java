package com.amatrix.sic.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

import com.amatrix.sic.contracts.ActorContext;

/**
 * 将 JWT claims 转为 ActorContext；兼容 authVersion 与旧 ver，规范化角色/权限和部门字段。
 */
public final class ActorContextFactory {
    private ActorContextFactory() {
    }

    public static ActorContext from(Jwt jwt) {
        return new ActorContext(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                numberOrNull(jwt.getClaim("deptId")),
                jwt.getClaimAsString("deptName"),
                strings(jwt.getClaim("roles")),
                strings(jwt.getClaim("permissions")),
                jwt.getId(),
                authVersion(jwt));
    }

    private static List<String> strings(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (value instanceof String string) {
            return List.of(string.split(" "));
        }
        return List.of();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static long authVersion(Jwt jwt) {
        long canonical = number(jwt.getClaim("authVersion"));
        return canonical > 0 ? canonical : number(jwt.getClaim("ver"));
    }

    private static Long numberOrNull(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
