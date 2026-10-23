package co.com.bancolombia.api.config;

import co.com.bancolombia.api.ApiTestBase;
import co.com.bancolombia.model.franchise.Franchise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(SecurityHeadersConfig.class)
class SecurityConfigTest extends ApiTestBase {
    private static final String FRANCHISES = "/franchises";
    private static final String CHALLENGE = "Basic realm=\"franchise-inventory\"";
    private static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";
    private static final String UNAUTHORIZED_MESSAGE = "A valid API credential is required, sent with HTTP Basic";
    private static final String ID = "3f2b8c1e-7d4a-4e5b-9c6d-0a1b2c3d4e5f";

    @ParameterizedTest
    @CsvSource({
            "POST, /franchises",
            "PATCH, /franchises/{id}",
            "POST, /franchises/{franchiseId}/branches",
            "PATCH, /franchises/{franchiseId}/branches/{id}",
            "POST, /franchises/{franchiseId}/branches/{branchId}/products",
            "PATCH, /franchises/{franchiseId}/branches/{branchId}/products/{id}",
            "DELETE, /franchises/{franchiseId}/branches/{branchId}/products/{id}",
            "PATCH, /franchises/{franchiseId}/branches/{branchId}/products/{id}/stock",
            "GET, /franchises/{franchiseId}/top-stock-products"
    })
    void everyBusinessRouteReturns401WithoutCredentials(String method, String path) {
        expectUnauthorized(anonymousClient.method(HttpMethod.valueOf(method))
                .uri(path, ID, ID, ID)
                .exchange());
    }

    @Test
    void wrongPasswordReturns401() {
        expectUnauthorized(postFranchise(headers -> headers.setBasicAuth(credential.username(), "wrong-password")));
    }

    @Test
    void wrongUsernameReturns401() {
        expectUnauthorized(postFranchise(headers -> headers.setBasicAuth("other-client", credential.password())));
    }

    /** Not Basic, no token, bad base64, and a token without a colon ("test-client"). */
    @ParameterizedTest
    @ValueSource(strings = {"Bearer some-token", "Basic", "Basic %%%not-base64%%%", "Basic dGVzdC1jbGllbnQ="})
    void malformedAuthorizationReturns401(String authorization) {
        expectUnauthorized(postFranchise(headers -> headers.set(HttpHeaders.AUTHORIZATION, authorization)));
    }

    @Test
    void validCredentialsReachTheHandler() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.just(Franchise.builder().id(UUID.randomUUID()).name("Nequi").build()));

        postFranchise(this::authenticate)
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Nequi");
        verify(createFranchiseUseCase).execute(any(Franchise.class));
    }

    @Test
    void invalidBodyWithoutCredentialsReturns401BeforeValidation() {
        expectUnauthorized(anonymousClient.post()
                .uri(FRANCHISES)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":")
                .exchange());
    }

    @Test
    void invalidBodyWithCredentialsReturns400() {
        anonymousClient.post()
                .uri(FRANCHISES)
                .headers(this::authenticate)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(createFranchiseUseCase);
    }

    /** Each request is checked by itself: an authenticated call sets no cookie and opens no session. */
    @Test
    void authenticatedRequestSetsNoCookieAndOpensNoSession() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.just(Franchise.builder().id(UUID.randomUUID()).name("Nequi").build()));

        postFranchise(this::authenticate)
                .expectStatus().isCreated()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);

        anonymousClient.post()
                .uri(FRANCHISES)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Nequi"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
    }

    @Test
    void unauthorizedResponseCarriesTheSecurityHeadersOnce() {
        postFranchise(headers -> { })
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'self'; form-action 'self'")
                .expectHeader().valueEquals("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().doesNotExist("Server")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
                .expectHeader().doesNotExist("X-Frame-Options")
                .expectHeader().doesNotExist("X-XSS-Protection")
                .expectHeader().doesNotExist("Expires");
    }

    private void authenticate(HttpHeaders headers) {
        headers.setBasicAuth(credential.username(), credential.password());
    }

    private WebTestClient.ResponseSpec postFranchise(Consumer<HttpHeaders> authorization) {
        return anonymousClient.post()
                .uri(FRANCHISES)
                .headers(authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Nequi"))
                .exchange();
    }

    /** Every rejected credential gets exactly this response, and no Use Case runs. */
    private void expectUnauthorized(WebTestClient.ResponseSpec response) {
        response.expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.code").isEqualTo(UNAUTHORIZED_CODE)
                .jsonPath("$.message").isEqualTo(UNAUTHORIZED_MESSAGE);
        verifyNoInteractions(createFranchiseUseCase, addBranchUseCase, addProductUseCase, deleteProductUseCase,
                updateProductStockUseCase, getTopStockReportUseCase, renameFranchiseUseCase, renameBranchUseCase,
                renameProductUseCase);
    }
}
