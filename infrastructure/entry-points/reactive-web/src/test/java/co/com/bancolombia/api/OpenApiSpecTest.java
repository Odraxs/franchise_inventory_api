package co.com.bancolombia.api;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.TechnicalException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the hand-written static/openapi.yaml in step with RouterRest and the error codes. */
class OpenApiSpecTest {
    private static final String SPEC = "/static/openapi.yaml";
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    private static Map<String, Object> spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        try (InputStream input = OpenApiSpecTest.class.getResourceAsStream(SPEC)) {
            spec = new Yaml().load(input);
        }
    }

    @Test
    void documentsExactlyTheRoutesOfTheRouter() {
        assertThat(operations().keySet()).containsExactlyInAnyOrder(
                "post " + ApiPaths.FRANCHISES,
                "patch " + ApiPaths.FRANCHISE,
                "post " + ApiPaths.BRANCHES,
                "patch " + ApiPaths.BRANCH,
                "post " + ApiPaths.PRODUCTS,
                "patch " + ApiPaths.PRODUCT,
                "delete " + ApiPaths.PRODUCT,
                "patch " + ApiPaths.PRODUCT_STOCK,
                "get " + ApiPaths.TOP_STOCK_PRODUCTS);
    }

    @Test
    void everyRequestBodyHasAnExample() {
        operations().forEach((route, operation) -> {
            Map<String, Object> requestBody = resolve(map(operation.get("requestBody")));
            if (requestBody != null) {
                assertThat(hasExample(requestBody)).as("request example of %s", route).isTrue();
            }
        });
    }

    @Test
    void everyResponseWithContentHasAnExample() {
        operations().forEach((route, operation) -> map(operation.get("responses")).forEach((status, value) -> {
            Map<String, Object> response = resolve(map(value));
            if (response.containsKey("content")) {
                assertThat(hasExample(response)).as("%s example of %s", status, route).isTrue();
            }
        }));
    }

    @Test
    void everyErrorCodeHasAnExample() {
        List<String> documentedCodes = new ArrayList<>();
        collectCodes(spec, documentedCodes);

        Stream.concat(Arrays.stream(BusinessException.ErrorCode.values()).map(Enum::name),
                        Arrays.stream(TechnicalException.ErrorCode.values()).map(Enum::name))
                .forEach(code -> assertThat(documentedCodes).as("example for %s", code).contains(code));
    }

    private static Map<String, Map<String, Object>> operations() {
        Map<String, Map<String, Object>> operations = new HashMap<>();
        map(spec.get("paths")).forEach((path, item) -> map(item).forEach((method, operation) -> {
            if (HTTP_METHODS.contains(method)) {
                operations.put(method + " " + path, map(operation));
            }
        }));
        return operations;
    }

    private static boolean hasExample(Map<String, Object> bodyOrResponse) {
        Map<String, Object> json = map(map(bodyOrResponse.get("content")).get("application/json"));
        return json != null && (json.containsKey("example")
                || json.get("examples") instanceof Map<?, ?> examples && !examples.isEmpty());
    }

    /** Follows a local $ref such as #/components/responses/FranchiseNotFound. */
    private static Map<String, Object> resolve(Map<String, Object> node) {
        if (node == null || !node.containsKey("$ref")) {
            return node;
        }
        Map<String, Object> target = spec;
        for (String segment : ((String) node.get("$ref")).substring(2).split("/")) {
            target = map(target.get(segment));
        }
        return target;
    }

    private static void collectCodes(Object node, List<String> codes) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if ("code".equals(key) && value instanceof String code) {
                    codes.add(code);
                }
                collectCodes(value, codes);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collectCodes(item, codes));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        return (Map<String, Object>) node;
    }
}
