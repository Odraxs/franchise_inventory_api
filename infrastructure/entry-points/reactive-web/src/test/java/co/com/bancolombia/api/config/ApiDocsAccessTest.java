package co.com.bancolombia.api.config;

import co.com.bancolombia.api.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class ApiDocsAccessTest extends ApiTestBase {
    private static final String SPEC = "/openapi.yaml";
    private static final String BASIC_AUTH = "basicAuth";

    /** Springdoc isn't loaded in this slice, so a 404 (not a 401) proves the chain lets the docs through. */
    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui.html", "/swagger-ui/index.html", "/webjars/swagger-ui/index.html",
            "/v3/api-docs", "/v3/api-docs/swagger-config"})
    void swaggerUiAndApiDocsArePublic(String path) {
        anonymousClient.get()
                .uri(path)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void specIsPublicAndRequiresBasicAuthOnEveryOperation() {
        String body = anonymousClient.get()
                .uri(SPEC)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        Map<String, Object> spec = new Yaml().load(body);
        assertThat(spec.get("security")).isEqualTo(List.of(Map.of(BASIC_AUTH, List.of())));
        assertThat(spec).extractingByKey("components").asInstanceOf(MAP)
                .extractingByKey("securitySchemes").asInstanceOf(MAP)
                .extractingByKey(BASIC_AUTH).asInstanceOf(MAP)
                .containsEntry("type", "http")
                .containsEntry("scheme", "basic");
    }

    @Test
    void businessRouteStillReturns401WithoutCredentials() {
        anonymousClient.get()
                .uri("/franchises/{franchiseId}/top-stock-products", "3f2b8c1e-7d4a-4e5b-9c6d-0a1b2c3d4e5f")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
