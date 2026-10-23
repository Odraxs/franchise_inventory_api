package co.com.bancolombia.api;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.product.Product;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductHandlerTest extends ApiTestBase {

    @Test
    void addProductReturns201WithLocationAndBody() {
        UUID franchiseId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(addProductUseCase.execute(eq(franchiseId), argThat(product -> branchId.equals(product.branchId())
                && "Cola".equals(product.name()) && product.stock() == 0)))
                .thenReturn(Mono.just(Product.builder().id(id).branchId(branchId).name("Cola").stock(0).build()));

        postProduct(franchiseId.toString(), branchId.toString(), "{\"name\": \"Cola\", \"stock\": 0}")
                .expectStatus().isCreated()
                .expectHeader().location("/franchises/" + franchiseId + "/branches/" + branchId + "/products/" + id)
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Cola")
                .jsonPath("$.stock").isEqualTo(0)
                .jsonPath("$.branchId").doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_PRODUCT_NAME, 400",
            "INVALID_STOCK, 400",
            "FRANCHISE_NOT_FOUND, 404",
            "BRANCH_NOT_FOUND, 404",
            "DUPLICATE_PRODUCT_NAME, 409"
    })
    void addProductMapsBusinessErrorsToStatusAndCode(ErrorCode errorCode, int status) {
        when(addProductUseCase.execute(any(UUID.class), any(Product.class)))
                .thenReturn(Mono.error(new BusinessException(errorCode)));

        postProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "{\"name\": \"Cola\", \"stock\": 1}")
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(errorCode.name());
    }

    @Test
    void addProductReturns503WithDatabaseUnavailableWhenTheUseCaseFailsForATechnicalReason() {
        when(addProductUseCase.execute(any(UUID.class), any(Product.class)))
                .thenReturn(Mono.error(new TechnicalException(TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)));

        postProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "{\"name\": \"Cola\", \"stock\": 1}")
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DATABASE_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"stock\": 1}", "{\"name\": \" \", \"stock\": 1}"})
    void addProductReturns400InvalidProductNameWithoutCallingTheUseCase(String body) {
        postProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PRODUCT_NAME");
        verifyNoInteractions(addProductUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": \"Cola\"}", "{\"name\": \"Cola\", \"stock\": null}",
            "{\"name\": \"Cola\", \"stock\": -1}"})
    void addProductReturns400InvalidStockWithoutCallingTheUseCase(String body) {
        postProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_STOCK");
        verifyNoInteractions(addProductUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"abc\"", "\"5\"", "true", "{}", "[]", "2147483648", "99999999999999999999"})
    void addProductReturns400InvalidRequestForWrongStockType(String stock) {
        postProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{\"name\": \"Cola\", \"stock\": " + stock + "}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(addProductUseCase);
    }

    @Test
    void addProductReturns400InvalidRequestForMalformedIds() {
        postProduct("not-a-uuid", UUID.randomUUID().toString(), "{\"name\": \"Cola\", \"stock\": 1}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        postProduct(UUID.randomUUID().toString(), "not-a-uuid", "{\"name\": \"Cola\", \"stock\": 1}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void addProductReturns400InvalidRequestForMalformedJsonAndMissingBody() {
        postProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "{\"name\": ")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        webTestClient.post()
                .uri("/franchises/{franchiseId}/branches/{branchId}/products", UUID.randomUUID(), UUID.randomUUID())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void renameProductReturns200WithStrippedNameStockAndNoLocation() {
        UUID franchiseId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(renameProductUseCase.execute(franchiseId, branchId, id, "  Cola  "))
                .thenReturn(Mono.just(Product.builder().id(id).branchId(branchId).name("Cola").stock(7).build()));

        patchProduct(franchiseId.toString(), branchId.toString(), id.toString(), Map.of("name", "  Cola  "))
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Location")
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Cola")
                .jsonPath("$.stock").isEqualTo(7);
    }

    @Test
    void renameProductIgnoresUnknownFields() {
        UUID id = UUID.randomUUID();
        when(renameProductUseCase.execute(any(UUID.class), any(UUID.class), eq(id), eq("Cola")))
                .thenReturn(Mono.just(Product.builder().id(id).branchId(UUID.randomUUID()).name("Cola").stock(7).build()));

        patchProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), id.toString(),
                "{\"name\": \"Cola\", \"extra\": true}")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.stock").isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "0", "2147483648", "null", "\"5\"", "true", "{}", "[]"})
    void renameProductReturns400InvalidProductUpdateForAnyStockValueWithoutCallingTheUseCase(String stock) {
        patchProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{\"name\": \"Cola\", \"stock\": " + stock + "}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PRODUCT_UPDATE")
                .jsonPath("$.message")
                .isEqualTo("Only the name changes here; update the stock through the stock endpoint");
        verifyNoInteractions(renameProductUseCase);
    }

    @Test
    void renameProductRejectsStockBeforeTheName() {
        patchProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{\"name\": \" \", \"stock\": 5}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PRODUCT_UPDATE");
        verifyNoInteractions(renameProductUseCase);
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_PRODUCT_NAME, 400",
            "INVALID_PRODUCT_UPDATE, 400",
            "FRANCHISE_NOT_FOUND, 404",
            "BRANCH_NOT_FOUND, 404",
            "PRODUCT_NOT_FOUND, 404",
            "DUPLICATE_PRODUCT_NAME, 409"
    })
    void renameProductMapsBusinessErrorsToStatusAndCode(ErrorCode errorCode, int status) {
        when(renameProductUseCase.execute(any(UUID.class), any(UUID.class), any(UUID.class), any()))
                .thenReturn(Mono.error(new BusinessException(errorCode)));

        patchProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                Map.of("name", "Cola"))
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(errorCode.name())
                .jsonPath("$.message").isEqualTo(new BusinessException(errorCode).getMessage());
    }

    @Test
    void renameProductPassesAMissingNameToTheUseCase() {
        when(renameProductUseCase.execute(any(UUID.class), any(UUID.class), any(UUID.class), eq(null)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.INVALID_PRODUCT_NAME)));

        patchProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "{}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PRODUCT_NAME");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": ", "{\"name\": 123}", "{\"name\": true}"})
    void renameProductReturns400InvalidRequestForMalformedJsonOrWrongType(String body) {
        patchProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameProductUseCase);
    }

    @Test
    void renameProductReturns400InvalidRequestForMissingBody() {
        webTestClient.patch()
                .uri("/franchises/{franchiseId}/branches/{branchId}/products/{id}", UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameProductUseCase);
    }

    @ParameterizedTest
    @CsvSource({
            "not-a-uuid, 3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60, 3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60",
            "3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60, not-a-uuid, 3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60",
            "3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60, 3f2c7a1e-8d4b-4c59-9a6e-1b2d3c4e5f60, not-a-uuid"
    })
    void renameProductChecksTheIdsBeforeTheName(String franchiseId, String branchId, String id) {
        patchProduct(franchiseId, branchId, id, Map.of("name", " "))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(renameProductUseCase);
    }

    @Test
    void deleteProductReturns204WithoutBody() {
        UUID franchiseId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(deleteProductUseCase.execute(franchiseId, branchId, id)).thenReturn(Mono.empty());

        deleteProduct(franchiseId.toString(), branchId.toString(), id.toString())
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "FRANCHISE_NOT_FOUND, 404",
            "BRANCH_NOT_FOUND, 404",
            "PRODUCT_NOT_FOUND, 404"
    })
    void deleteProductMapsBusinessErrorsToStatusAndCode(ErrorCode errorCode, int status) {
        when(deleteProductUseCase.execute(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new BusinessException(errorCode)));

        deleteProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString())
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(errorCode.name());
    }

    @Test
    void deleteProductReturns503WithDatabaseUnavailableWhenTheUseCaseFailsForATechnicalReason() {
        when(deleteProductUseCase.execute(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new TechnicalException(TechnicalException.ErrorCode.DATABASE_UNAVAILABLE)));

        deleteProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString())
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DATABASE_UNAVAILABLE");
    }

    @Test
    void deleteProductReturns400InvalidRequestForMalformedIdsWithoutCallingTheUseCase() {
        deleteProduct("not-a-uuid", UUID.randomUUID().toString(), UUID.randomUUID().toString())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        deleteProduct(UUID.randomUUID().toString(), "not-a-uuid", UUID.randomUUID().toString())
                .expectStatus().isBadRequest();
        deleteProduct(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "not-a-uuid")
                .expectStatus().isBadRequest();
        verifyNoInteractions(deleteProductUseCase);
    }

    @Test
    void updateProductStockReturns200WithTheUpdatedProduct() {
        UUID franchiseId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(updateProductStockUseCase.execute(franchiseId, branchId, id, 0))
                .thenReturn(Mono.just(Product.builder().id(id).branchId(branchId).name("Cola").stock(0).build()));

        patchStock(franchiseId.toString(), branchId.toString(), id.toString(), "{\"stock\": 0}")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Cola")
                .jsonPath("$.stock").isEqualTo(0);
    }

    @Test
    void updateProductStockIgnoresUnknownFields() {
        UUID id = UUID.randomUUID();
        when(updateProductStockUseCase.execute(any(UUID.class), any(UUID.class), eq(id), eq(3)))
                .thenReturn(Mono.just(Product.builder().id(id).branchId(id).name("Cola").stock(3).build()));

        patchStock(UUID.randomUUID().toString(), UUID.randomUUID().toString(), id.toString(),
                "{\"stock\": 3, \"extra\": 1}")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.stock").isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_STOCK, 400",
            "FRANCHISE_NOT_FOUND, 404",
            "BRANCH_NOT_FOUND, 404",
            "PRODUCT_NOT_FOUND, 404"
    })
    void updateProductStockMapsBusinessErrorsToStatusAndCode(ErrorCode errorCode, int status) {
        when(updateProductStockUseCase.execute(any(UUID.class), any(UUID.class), any(UUID.class), any()))
                .thenReturn(Mono.error(new BusinessException(errorCode)));

        patchStock(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{\"stock\": 1}")
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(errorCode.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"stock\": null}", "{\"stock\": -1}"})
    void updateProductStockReturns400InvalidStockFromTheUseCase(String body) {
        when(updateProductStockUseCase.execute(any(UUID.class), any(UUID.class), any(UUID.class), any()))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.INVALID_STOCK)));

        patchStock(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_STOCK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"abc\"", "\"5\"", "true", "{}", "[]", "2147483648"})
    void updateProductStockReturns400InvalidRequestForWrongStockType(String stock) {
        patchStock(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{\"stock\": " + stock + "}")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(updateProductStockUseCase);
    }

    @Test
    void updateProductStockReturns400InvalidRequestForMalformedIdsJsonAndMissingBody() {
        String id = UUID.randomUUID().toString();
        patchStock("not-a-uuid", id, id, "{\"stock\": 1}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        patchStock(id, "not-a-uuid", id, "{\"stock\": 1}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        patchStock(id, id, "not-a-uuid", "{\"stock\": 1}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        patchStock(id, id, id, "{\"stock\": ").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        webTestClient.patch()
                .uri(ApiPaths.PRODUCT_STOCK, id, id, id)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(updateProductStockUseCase);
    }

    private WebTestClient.ResponseSpec postProduct(String franchiseId, String branchId, Object body) {
        return webTestClient.post()
                .uri("/franchises/{franchiseId}/branches/{branchId}/products", franchiseId, branchId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private WebTestClient.ResponseSpec patchProduct(String franchiseId, String branchId, String id, Object body) {
        return webTestClient.patch()
                .uri("/franchises/{franchiseId}/branches/{branchId}/products/{id}", franchiseId, branchId, id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private WebTestClient.ResponseSpec deleteProduct(String franchiseId, String branchId, String id) {
        return webTestClient.delete()
                .uri("/franchises/{franchiseId}/branches/{branchId}/products/{id}", franchiseId, branchId, id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
    }

    private WebTestClient.ResponseSpec patchStock(String franchiseId, String branchId, String id, Object body) {
        return webTestClient.patch()
                .uri(ApiPaths.PRODUCT_STOCK, franchiseId, branchId, id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }
}
