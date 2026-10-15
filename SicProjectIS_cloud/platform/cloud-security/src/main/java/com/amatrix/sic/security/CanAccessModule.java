package com.amatrix.sic.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 方法权限元注解：调用名为 projectAccessPolicy 的 Bean 的 canAccessModule，验证模块读取。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@projectAccessPolicy.canAccessModule(@actorContextResolver.from(authentication), #moduleInstanceId)")
public @interface CanAccessModule {
}
