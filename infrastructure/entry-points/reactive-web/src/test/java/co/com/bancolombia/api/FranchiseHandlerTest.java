package co.com.bancolombia.api;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.franchise.Franchise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FranchiseHandlerTest extends ApiTestBase {

    @Test
    void createFranchiseReturns201WithLocationAndBody() {
        UUID id = UUID.randomUUID();
        when(createFranchiseUseCase.execute(argThat(franchise -> "Nequi".equals(franchise.name()))))
                .thenReturn(Mono.just(Franchise.builder().id(id).name("Nequi").build()));

        postFranchise(Map.of("name", "Nequi"))
                .expectStatus().isCreated()
                .expectHeader().location("/franchises/" + id)
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Nequi");
    }

    @Test
    void createFranchiseReturns400ForInvalidName() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.INVALID_FRANCHISE_NAME)));

        postFranchise(Map.of("name", " "))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_FRANCHISE_NAME");
    }

    @Test
    void createFranchiseReturns400InvalidRequestForEmptyBody() {
        webTestClient.post()
                .uri("/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void createFranchiseReturns409ForDuplicateName() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.DUPLICATE_FRANCHISE_NAME)));

        postFranchise(Map.of("name", "Nequi"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_FRANCHISE_NAME")
                .jsonPath("$.message").isEqualTo("A franchise with this name already exists");
    }

    @Test
    void createFranchiseReturns400InvalidRequestForMissingBodyWithoutContentType() {
        webTestClient.post()
                .uri("/franchises")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void createFranchiseReturns404ForNotFoundErrorCode() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)));

        postFranchise(Map.of("name", "Nequi"))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FRANCHISE_NOT_FOUND")
                .jsonPath("$.message").isEqualTo("The franchise does not exist");
    }

    @Test
    void createFranchiseReturns503WithDatabaseUnavailableWhenTheUseCaseFailsForATechnicalReason() {
        when(createFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.error(new TechnicalException(TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)));

        postFranchise(Map.of("name", "Nequi"))
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DATABASE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("The database is unavailable, try again later");
    }

    @Test
    void createFranchiseReturns400InvalidRequestForMalformedJson() {
        postFranchise("{\"name\": ")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.message").isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "1.5", "true", "{}", "[]"})
    void createFranchiseReturns400InvalidRequestForNonStringName(String name) {
        postFranchise("{\"name\": " + name + "}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void renameFranchiseReturns200WithStrippedNameAndNoLocation() {
        UUID id = UUID.randomUUID();
        when(renameFranchiseUseCase.execute(argThat(franchise -> id.equals(franchise.id())
                && "Nequi Store".equals(franchise.name()))))
                .thenReturn(Mono.just(Franchise.builder().id(id).name("Nequi Store").build()));

        patchFranchise(id.toString(), Map.of("name", "  Nequi Store  "))
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Location")
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Nequi Store");
    }

    @Test
    void renameFranchiseIgnoresUnknownFieldsIncludingStock() {
        UUID id = UUID.randomUUID();
        when(renameFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.just(Franchise.builder().id(id).name("Nequi").build()));

        patchFranchise(id.toString(), "{\"name\": \"Nequi\", \"stock\": 5, \"extra\": true}")
                .expectStatus().isOk();
    }

    @ParameterizedTest
    @CsvSource({
            "FRANCHISE_NOT_FOUND, 404",
            "DUPLICATE_FRANCHISE_NAME, 409"
    })
    void renameFranchiseMapsBusinessErrorsToStatusAndCode(ErrorCode errorCode, int status) {
        when(renameFranchiseUseCase.execute(any(Franchise.class)))
                .thenReturn(Mono.error(new BusinessException(errorCode)));

        patchFranchise(UUID.randomUUID().toString(), Map.of("name", "Nequi"))
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(errorCode.name())
                .jsonPath("$.message").isEqualTo(new BusinessException(errorCode).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\": null}", "{\"name\": \"\"}", "{\"name\": \"   \"}",
            "{\"name\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"})
    void renameFranchiseReturns400InvalidFranchiseNameWithoutCallingTheUseCase(String body) {
        patchFranchise(UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_FRANCHISE_NAME");
        verifyNoInteractions(renameFranchiseUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": ", "{\"name\": 123}", "{\"name\": true}"})
    void renameFranchiseReturns400InvalidRequestForMalformedJsonOrWrongType(String body) {
        patchFranchise(UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameFranchiseUseCase);
    }

    @Test
    void renameFranchiseReturns400InvalidRequestForMissingBody() {
        webTestClient.patch()
                .uri("/franchises/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameFranchiseUseCase);
    }

    @Test
    void renameFranchiseChecksTheIdBeforeTheName() {
        patchFranchise("not-a-uuid", Map.of("name", " "))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameFranchiseUseCase);
    }

    private WebTestClient.ResponseSpec patchFranchise(String id, Object body) {
        return webTestClient.patch()
                .uri("/franchises/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    /** A String body is sent as raw JSON text; a Map is serialized. */
    private WebTestClient.ResponseSpec postFranchise(Object body) {
        return webTestClient.post()
                .uri("/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }
}
