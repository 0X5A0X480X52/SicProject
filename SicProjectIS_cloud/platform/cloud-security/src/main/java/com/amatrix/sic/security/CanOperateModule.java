package com.amatrix.sic.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 方法权限元注解：调用 workflowOperationPolicy.canOperate，验证当前模块任务可操作。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@workflowOperationPolicy.canOperate(@actorContextResolver.from(authentication), #moduleInstanceId)")
public @interface CanOperateModule {
}
