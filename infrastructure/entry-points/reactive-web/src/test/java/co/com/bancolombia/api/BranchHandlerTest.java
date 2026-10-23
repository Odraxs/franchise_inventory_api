package co.com.bancolombia.api;

import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.TechnicalException;
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

class BranchHandlerTest extends ApiTestBase {

    @Test
    void renameBranchReturns200WithStrippedNameAndNoLocation() {
        UUID franchiseId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(renameBranchUseCase.execute(argThat(branch -> id.equals(branch.id())
                && franchiseId.equals(branch.franchiseId()) && "Centro".equals(branch.name()))))
                .thenReturn(Mono.just(Branch.builder().id(id).franchiseId(franchiseId).name("Centro").build()));

        patchBranch(franchiseId.toString(), id.toString(), Map.of("name", "  Centro  "))
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Location")
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Centro")
                .jsonPath("$.franchiseId").doesNotExist();
    }

    @Test
    void renameBranchIgnoresUnknownFieldsIncludingStock() {
        UUID id = UUID.randomUUID();
        when(renameBranchUseCase.execute(any(Branch.class)))
                .thenReturn(Mono.just(Branch.builder().id(id).franchiseId(UUID.randomUUID()).name("Centro").build()));

        patchBranch(UUID.randomUUID().toString(), id.toString(), "{\"name\": \"Centro\", \"stock\": 5, \"extra\": true}")
                .expectStatus().isOk();
    }

    @ParameterizedTest
    @CsvSource({
            "FRANCHISE_NOT_FOUND, 404",
            "BRANCH_NOT_FOUND, 404",
            "DUPLICATE_BRANCH_NAME, 409"
    })
    void renameBranchMapsBusinessErrorsToStatusAndCode(ErrorCode errorCode, int status) {
        when(renameBranchUseCase.execute(any(Branch.class))).thenReturn(Mono.error(new BusinessException(errorCode)));

        patchBranch(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Map.of("name", "Centro"))
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(errorCode.name())
                .jsonPath("$.message").isEqualTo(new BusinessException(errorCode).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\": null}", "{\"name\": \"\"}", "{\"name\": \"   \"}",
            "{\"name\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"})
    void renameBranchReturns400InvalidBranchNameWithoutCallingTheUseCase(String body) {
        patchBranch(UUID.randomUUID().toString(), UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_BRANCH_NAME");
        verifyNoInteractions(renameBranchUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": ", "{\"name\": 123}", "{\"name\": true}"})
    void renameBranchReturns400InvalidRequestForMalformedJsonOrWrongType(String body) {
        patchBranch(UUID.randomUUID().toString(), UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameBranchUseCase);
    }

    @Test
    void renameBranchReturns400InvalidRequestForMissingBody() {
        webTestClient.patch()
                .uri("/franchises/{franchiseId}/branches/{id}", UUID.randomUUID(), UUID.randomUUID())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameBranchUseCase);
    }

    @ParameterizedTest
    @CsvSource({"not-a-uuid, 3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60", "3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60, not-a-uuid"})
    void renameBranchChecksTheIdsBeforeTheName(String franchiseId, String id) {
        patchBranch(franchiseId, id, Map.of("name", " "))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameBranchUseCase);
    }

    @Test
    void addBranchReturns201WithLocationAndBody() {
        UUID franchiseId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(addBranchUseCase.execute(argThat(branch -> franchiseId.equals(branch.franchiseId())
                && "Centro".equals(branch.name()))))
                .thenReturn(Mono.just(Branch.builder().id(id).franchiseId(franchiseId).name("Centro").build()));

        postBranch(franchiseId.toString(), Map.of("name", "Centro"))
                .expectStatus().isCreated()
                .expectHeader().location("/franchises/" + franchiseId + "/branches/" + id)
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Centro")
                .jsonPath("$.franchiseId").doesNotExist();
    }

    @Test
    void addBranchIgnoresUnknownFields() {
        UUID franchiseId = UUID.randomUUID();
        when(addBranchUseCase.execute(any(Branch.class)))
                .thenReturn(Mono.just(Branch.builder().id(UUID.randomUUID()).name("Centro").build()));

        postBranch(franchiseId.toString(), "{\"name\": \"Centro\", \"extra\": 1}")
                .expectStatus().isCreated();
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_BRANCH_NAME, 400",
            "FRANCHISE_NOT_FOUND, 404",
            "DUPLICATE_BRANCH_NAME, 409"
    })
    void addBranchMapsBusinessErrorsToStatusAndCode(ErrorCode errorCode, int status) {
        when(addBranchUseCase.execute(any(Branch.class))).thenReturn(Mono.error(new BusinessException(errorCode)));

        postBranch(UUID.randomUUID().toString(), Map.of("name", "Centro"))
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(errorCode.name());
    }

    @Test
    void addBranchReturns503WithDatabaseUnavailableWhenTheUseCaseFailsForATechnicalReason() {
        when(addBranchUseCase.execute(any(Branch.class)))
                .thenReturn(Mono.error(new TechnicalException(TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)));

        postBranch(UUID.randomUUID().toString(), Map.of("name", "Centro"))
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DATABASE_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void addBranchReturns400InvalidBranchNameWithoutCallingTheUseCase(String name) {
        postBranch(UUID.randomUUID().toString(), Map.of("name", name))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_BRANCH_NAME");
        verifyNoInteractions(addBranchUseCase);
    }

    @Test
    void addBranchReturns400InvalidBranchNameForMissingName() {
        postBranch(UUID.randomUUID().toString(), "{}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_BRANCH_NAME");
    }

    @Test
    void addBranchReturns400InvalidRequestForMalformedFranchiseId() {
        postBranch("not-a-uuid", Map.of("name", "Centro"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void addBranchReturns400InvalidRequestForMalformedJson() {
        postBranch(UUID.randomUUID().toString(), "{\"name\": ")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void addBranchReturns400InvalidRequestForMissingBody() {
        webTestClient.post()
                .uri("/franchises/{id}/branches", UUID.randomUUID())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "true", "{}", "[]"})
    void addBranchReturns400InvalidRequestForNonStringName(String name) {
        postBranch(UUID.randomUUID().toString(), "{\"name\": " + name + "}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    private WebTestClient.ResponseSpec postBranch(String franchiseId, Object body) {
        return webTestClient.post()
                .uri("/franchises/{franchiseId}/branches", franchiseId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private WebTestClient.ResponseSpec patchBranch(String franchiseId, String id, Object body) {
        return webTestClient.patch()
                .uri("/franchises/{franchiseId}/branches/{id}", franchiseId, id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }
}
