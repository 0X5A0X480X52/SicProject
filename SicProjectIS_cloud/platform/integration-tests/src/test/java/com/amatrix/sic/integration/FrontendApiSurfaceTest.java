package com.amatrix.sic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.yaml.snakeyaml.Yaml;

import com.amatrix.sic.iam.controller.AuthController;
import com.amatrix.sic.iam.controller.ExpertQualificationController;
import com.amatrix.sic.iam.controller.IamAdminController;
import com.amatrix.sic.material.controller.MaterialController;
import com.amatrix.sic.notification.controller.SseController;
import com.amatrix.sic.project.controller.ProjectAdminController;
import com.amatrix.sic.project.controller.ProjectAuditController;
import com.amatrix.sic.project.controller.ProjectAuthorizationController;
import com.amatrix.sic.project.controller.ProjectController;
import com.amatrix.sic.workflow.controller.ExpertReviewController;
import com.amatrix.sic.workflow.controller.ModuleBusinessDataController;
import com.amatrix.sic.workflow.controller.NodeFormController;
import com.amatrix.sic.workflow.controller.WorkflowDefinitionController;
import com.amatrix.sic.workflow.controller.WorkflowRuntimeController;

class FrontendApiSurfaceTest {
    private static final List<Class<?>> CONTROLLERS = List.of(
            AuthController.class, IamAdminController.class, ExpertQualificationController.class,
            ProjectController.class, ProjectAuthorizationController.class, ProjectAdminController.class,
            ProjectAuditController.class, WorkflowRuntimeController.class, WorkflowDefinitionController.class,
            ModuleBusinessDataController.class, ExpertReviewController.class, NodeFormController.class,
            MaterialController.class, SseController.class,
            com.amatrix.sic.project.controller.ProjectDepartmentController.class,
            com.amatrix.sic.workflow.controller.WorkflowWorkbenchController.class,
            com.amatrix.sic.workflow.controller.ExpertPersonalReviewController.class);

    @Test
    void everyFrontendCallHasAnExactProviderControllerMapping() throws Exception {
        Set<HttpEndpoint> mappings = controllerMappings();
        List<ApiContract> contracts = contracts();

        assertThat(contracts).extracting(ApiContract::endpoint).doesNotHaveDuplicates();
        assertThat(contracts).allSatisfy(contract -> assertThat(mappings)
                .as("provider controller mapping for %s %s", contract.method(), contract.path())
                .contains(contract.endpoint()));
    }

    @Test
    void gatewayFirstMatchRoutesEveryFrontendCallToItsOwningService() throws Exception {
        List<GatewayRoute> routes = gatewayRoutes();
        AntPathMatcher matcher = new AntPathMatcher();

        for (ApiContract contract : contracts()) {
            String concretePath = contract.path().replaceAll("\\{[^/]+}", "1");
            GatewayRoute selected = routes.stream()
                    .filter(route -> route.patterns().stream().anyMatch(pattern -> matcher.match(pattern, concretePath)))
                    .findFirst().orElseThrow(() -> new AssertionError("No gateway route for " + concretePath));
            assertThat(selected.service()).as("first gateway match for %s", concretePath)
                    .isEqualTo(contract.provider());
        }
    }

    @Test
    void everyFrontendCallHasAnExecutableSpringCloudContractWithTheExpectedResponseKind() throws Exception {
        AntPathMatcher matcher = new AntPathMatcher();
        for (String provider : List.of("iam-service", "project-service", "workflow-service", "material-service",
                "notification-service")) {
            List<ProviderContract> formalContracts = springCloudContracts(provider);
            for (ApiContract contract : contracts().stream()
                    .filter(item -> provider.equals(item.provider())).toList()) {
                String pattern = contract.path().replaceAll("\\{[^/]+}", "*");
                ProviderContract formal = formalContracts.stream()
                        .filter(item -> item.endpoint().method().equals(contract.method())
                                && matcher.match(pattern, item.endpoint().path())
                                && item.status() == contract.successStatus())
                        .findFirst().orElse(null);
                assertThat(formal)
                        .as("Spring Cloud Contract for %s %s", contract.method(), contract.path())
                        .isNotNull();
                assertResponseKind(contract, formal);
            }
        }
    }

    private static void assertResponseKind(ApiContract expected, ProviderContract actual) {
        String contentType = actual.contentType() == null ? "" : actual.contentType().toLowerCase();
        switch (expected.responseKind()) {
            case "JSON" -> {
                assertThat(contentType).as("JSON content type for %s", expected.path()).startsWith("application/json");
                assertThat(actual.body()).as("JSON wrapper for %s", expected.path()).isInstanceOf(Map.class);
                Map<?, ?> body = (Map<?, ?>) actual.body();
                assertThat(body.get("success")).isEqualTo(true);
                assertThat(body.containsKey("data")).isTrue();
                assertThat(body.containsKey("message")).isTrue();
                assertThat(body.get("data")).as("non-null JSON data for %s", expected.path()).isNotNull();
            }
            case "JSON_NULL" -> {
                assertThat(contentType).as("JSON content type for %s", expected.path()).startsWith("application/json");
                assertThat(actual.body()).as("JSON wrapper for %s", expected.path()).isInstanceOf(Map.class);
                Map<?, ?> body = (Map<?, ?>) actual.body();
                assertThat(body.get("success")).isEqualTo(true);
                assertThat(body.containsKey("data")).isTrue();
                assertThat(body.containsKey("message")).isTrue();
                assertThat(body.get("data")).as("null JSON data for %s", expected.path()).isNull();
            }
            case "BINARY" -> {
                assertThat(contentType).as("binary content type for %s", expected.path())
                        .doesNotStartWith("application/json").doesNotStartWith("text/event-stream");
                assertThat(actual.body()).as("binary response body for %s", expected.path()).isNotNull();
            }
            case "SSE" -> {
                assertThat(contentType).as("SSE content type for %s", expected.path()).startsWith("text/event-stream");
                assertThat(String.valueOf(actual.body())).contains("event:MODULE_STATE_CHANGED",
                        "\"moduleInstanceId\"", "\"state\"");
            }
            default -> throw new AssertionError("Unsupported response kind: " + expected.responseKind());
        }
    }

    private static Set<HttpEndpoint> controllerMappings() {
        Set<HttpEndpoint> result = new HashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            List<String> prefixes = paths(classMapping);
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                for (String prefix : prefixes) for (String suffix : paths(mapping)) {
                    String path = normalizePath(prefix + "/" + suffix);
                    for (RequestMethod requestMethod : mapping.method()) {
                        result.add(new HttpEndpoint(requestMethod.name(), path));
                    }
                }
            }
        }
        return result;
    }

    private static List<String> paths(RequestMapping mapping) {
        if (mapping == null) return List.of("");
        String[] paths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return paths.length == 0 ? List.of("") : Arrays.asList(paths);
    }

    private static String normalizePath(String path) {
        String normalized = path.replaceAll("/+", "/").replaceAll("\\{[^/]+}", "{}");
        if (normalized.length() > 1 && normalized.endsWith("/")) return normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static List<ApiContract> contracts() throws Exception {
        try (var stream = FrontendApiSurfaceTest.class.getResourceAsStream("/contracts/frontend-api-surface.csv")) {
            if (stream == null) throw new IllegalStateException("Missing frontend API contract resource");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().skip(1).filter(line -> !line.isBlank()).map(line -> {
                    String[] values = line.split(",", -1);
                    return new ApiContract(values[0], values[1], values[2], Integer.parseInt(values[3]), values[4]);
                }).toList();
            }
        }
    }

    private static List<GatewayRoute> gatewayRoutes() throws Exception {
        Path root = projectRoot();
        List<String> lines = Files.readAllLines(root.resolve(
                "platform/config-server/src/main/resources/config/gateway-service.yaml"), StandardCharsets.UTF_8);
        List<GatewayRoute> routes = new ArrayList<>();
        String service = null;
        List<String> patterns = List.of();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("- id:")) {
                if (service != null) routes.add(new GatewayRoute(service, patterns));
                service = null;
                patterns = List.of();
            } else if (line.startsWith("uri: lb://")) {
                service = line.substring("uri: lb://".length()).trim();
            } else if (line.startsWith("- Path=")) {
                patterns = Arrays.stream(line.substring("- Path=".length()).split(","))
                        .map(String::trim).toList();
            }
        }
        if (service != null) routes.add(new GatewayRoute(service, patterns));
        return routes;
    }

    private static List<ProviderContract> springCloudContracts(String provider) throws Exception {
        Path directory = projectRoot().resolve("services").resolve(provider)
                .resolve("src/test/resources/contracts");
        List<ProviderContract> contracts = new ArrayList<>();
        Yaml yaml = new Yaml();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Map<String, Object> root;
                try (var input = Files.newInputStream(path)) {
                    root = yaml.load(input);
                }
                Map<String, Object> request = map(root.get("request"));
                Map<String, Object> response = map(root.get("response"));
                if (request.isEmpty() || response.isEmpty()) continue;
                Map<String, Object> headers = map(response.get("headers"));
                Object rawUrl = request.containsKey("url") ? request.get("url") : request.get("urlPath");
                String url = String.valueOf(rawUrl).split("\\?", 2)[0];
                Object body = response.get("body");
                if (body == null && response.get("bodyFromFile") != null) {
                    try (var input = Files.newInputStream(path.resolveSibling(String.valueOf(response.get("bodyFromFile"))))) {
                        body = yaml.load(input);
                    }
                }
                contracts.add(new ProviderContract(
                        new HttpEndpoint(String.valueOf(request.get("method")), url),
                        ((Number) response.get("status")).intValue(),
                        headers.get("Content-Type") == null ? null : String.valueOf(headers.get("Content-Type")),
                        body));
            }
        }
        return contracts;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("platform/config-server/src/main/resources/config/gateway-service.yaml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sic cloud project root");
    }

    private record HttpEndpoint(String method, String path) {}
    private record ApiContract(String provider, String method, String path, int successStatus, String responseKind) {
        HttpEndpoint endpoint() { return new HttpEndpoint(method, normalizePath(path)); }
    }
    private record ProviderContract(HttpEndpoint endpoint, int status, String contentType, Object body) {}
    private record GatewayRoute(String service, List<String> patterns) {}
}
