package com.amatrix.sic.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 方法权限元注解：调用 projectAccessPolicy.canAccess 验证项目读取。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@projectAccessPolicy.canAccess(@actorContextResolver.from(authentication), #projectId)")
public @interface CanAccessProject {
}
