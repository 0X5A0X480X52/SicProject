package com.amatrix.sic.material.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;

import com.amatrix.sic.contracts.ActorContext;
import com.amatrix.sic.material.MaterialApplicationService;
import com.amatrix.sic.material.MaterialApplicationService.Download;
import com.amatrix.sic.material.MaterialController;
import com.amatrix.sic.security.ApiExceptionHandler;

import io.restassured.module.mockmvc.RestAssuredMockMvc;

public abstract class MaterialContractBase {
    @BeforeEach
    void setUpContractProvider() {
        MaterialApplicationService service = mock(MaterialApplicationService.class);
        Map<String, Object> context = linked("materialId", 30L, "projectId", 10L, "materialTypeId", 3L,
                "materialTypeCode", "APPLICATION_FORM", "materialTypeName", "Application form",
                "moduleType", "APPLICATION", "allowedFileTypes", null, "maxFileSizeMb", 20,
                "materialVersionId", 40L, "versionNo", 1, "fileName", "demo.txt",
                "fileUrl", "/api/material-versions/40/download", "fileHash", "abc123",
                "uploadedBy", 1L, "uploadedByName", null, "uploadedAt", "2030-01-02T03:04:05",
                "isCurrent", true);
        when(service.list(any(ActorContext.class), eq(10L))).thenReturn(List.of(context));
        when(service.upload(any(ActorContext.class), eq(10L), eq("APPLICATION_FORM"), any(MultipartFile.class)))
                .thenReturn(linked("version", linked("materialVersionId", 40L, "materialId", 30L,
                        "versionNo", 1, "fileName", "demo.txt", "fileUrl", "/api/material-versions/40/download",
                        "fileHash", "abc123", "uploadedBy", 1L, "uploadedAt", "2030-01-02T03:04:05",
                        "isCurrent", true), "context", context));
        when(service.download(any(ActorContext.class), eq(40L))).thenReturn(new Download(
                new InputStreamResource(new ByteArrayInputStream("hello".getBytes())), "demo.txt", "text/plain", 5));
        doNothing().when(service).delete(any(ActorContext.class), eq(40L));

        Jwt jwt = Jwt.withTokenValue("contract.jwt.token").header("alg", "none").subject("1")
                .claim("username", "alice").claim("roles", List.of("PROJECT_LEADER"))
                .claim("permissions", List.of("PERM_MATERIAL_VIEW", "PERM_MATERIAL_DELETE"))
                .claim("ver", 3L).claim("jti", "contract-jti")
                .issuedAt(Instant.parse("2029-01-02T03:04:05Z")).expiresAt(Instant.parse("2030-01-02T03:04:05Z"))
                .build();
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new MaterialController(service))
                .setControllerAdvice(new ApiExceptionHandler()).setCustomArgumentResolvers(jwtResolver(jwt)).build());
    }

    private static HandlerMethodArgumentResolver jwtResolver(Jwt jwt) {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && Jwt.class.isAssignableFrom(parameter.getParameterType());
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory binderFactory) { return jwt; }
        };
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
