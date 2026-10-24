package co.com.bancolombia.api.config;

import co.com.bancolombia.api.ApiTestBase;
import co.com.bancolombia.model.franchise.Franchise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(SecurityHeadersConfig.class)
class ConfigTest extends ApiTestBase {

    private static final String FOREIGN_ORIGIN = "https://foreign.example.com";

    @Test
    void corsConfigurationShouldAllowOrigins() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.just(Franchise.builder().id(UUID.randomUUID()).name("Nequi").build()));

        webTestClient.post()
                .uri("/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Nequi"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'self'; form-action 'self'")
                .expectHeader().valueEquals("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().doesNotExist("Server")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    @Test
    void swaggerUiPathsAllowDataImagesAndInlineStyles() {
        webTestClient.get()
                .uri("/swagger-ui/index.html")
                .exchange()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                + "frame-ancestors 'self'; form-action 'self'");
    }

    /** The aws profile sets no origin: every cross-origin call is rejected. */
    @Nested
    @TestPropertySource(properties = "cors.allowed-origins=")
    class WithEmptyAllowedOrigins {

        @Test
        void corsConfigurationShouldRejectForeignOrigin() {
            webTestClient.post()
                    .uri("/franchises")
                    .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("name", "Nequi"))
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);

            verifyNoInteractions(createFranchiseUseCase);
        }
    }
}
