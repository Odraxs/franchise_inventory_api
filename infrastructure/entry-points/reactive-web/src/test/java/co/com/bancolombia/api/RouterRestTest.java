package co.com.bancolombia.api;

import co.com.bancolombia.api.config.JacksonConfig;
import co.com.bancolombia.model.branch.Branch;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.exception.TechnicalException;
import co.com.bancolombia.model.franchise.Franchise;
import co.com.bancolombia.model.product.BranchTopStock;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.TopStockReport;
import co.com.bancolombia.usecase.addproduct.AddProductUseCase;
import co.com.bancolombia.usecase.addbranch.AddBranchUseCase;
import co.com.bancolombia.usecase.createfranchise.CreateFranchiseUseCase;
import co.com.bancolombia.usecase.deleteproduct.DeleteProductUseCase;
import co.com.bancolombia.usecase.updateproductstock.UpdateProductStockUseCase;
import co.com.bancolombia.usecase.gettopstockreport.GetTopStockReportUseCase;
import co.com.bancolombia.usecase.renamebranch.RenameBranchUseCase;
import co.com.bancolombia.usecase.renamefranchise.RenameFranchiseUseCase;
import co.com.bancolombia.usecase.renameproduct.RenameProductUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, Handler.class, JacksonConfig.class})
@WebFluxTest
class RouterRestTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateFranchiseUseCase createFranchiseUseCase;

    @MockitoBean
    private AddBranchUseCase addBranchUseCase;

    @MockitoBean
    private AddProductUseCase addProductUseCase;

    @MockitoBean
    private DeleteProductUseCase deleteProductUseCase;

    @MockitoBean
    private UpdateProductStockUseCase updateProductStockUseCase;

    @MockitoBean
    private GetTopStockReportUseCase getTopStockReportUseCase;

    @MockitoBean
    private RenameFranchiseUseCase renameFranchiseUseCase;

    @MockitoBean
    private RenameBranchUseCase renameBranchUseCase;

    @MockitoBean
    private RenameProductUseCase renameProductUseCase;

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
                .jsonPath("$.message").isEqualTo("Only the name changes here; update the stock with PATCH "
                        + "/franchises/{franchiseId}/branches/{branchId}/products/{id}/stock");
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
    void getTopStockProductsReturns200WithEveryBranchAndItsTopProduct() {
        UUID franchiseId = UUID.randomUUID();
        UUID centroId = UUID.randomUUID();
        UUID norteId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(getTopStockReportUseCase.execute(franchiseId)).thenReturn(Mono.just(TopStockReport.builder()
                .franchiseId(franchiseId)
                .branches(List.of(
                        BranchTopStock.builder().branchId(centroId).branchName("Centro")
                                .topProduct(Product.builder().id(productId).branchId(centroId).name("Cola").stock(0)
                                        .build())
                                .build(),
                        BranchTopStock.builder().branchId(norteId).branchName("Norte").build()))
                .build()));

        getTopStockProducts(franchiseId.toString())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.franchiseId").isEqualTo(franchiseId.toString())
                .jsonPath("$.branches.length()").isEqualTo(2)
                .jsonPath("$.branches[0].branchId").isEqualTo(centroId.toString())
                .jsonPath("$.branches[0].branchName").isEqualTo("Centro")
                .jsonPath("$.branches[0].topProduct.id").isEqualTo(productId.toString())
                .jsonPath("$.branches[0].topProduct.name").isEqualTo("Cola")
                .jsonPath("$.branches[0].topProduct.stock").isEqualTo(0)
                .jsonPath("$.branches[1].branchId").isEqualTo(norteId.toString())
                .jsonPath("$.branches[1].branchName").isEqualTo("Norte")
                .jsonPath("$.branches[1]").value(Map.class,
                        branch -> assertThat(branch).containsEntry("topProduct", null));
    }

    @Test
    void getTopStockProductsReturns404ForUnknownFranchise() {
        when(getTopStockReportUseCase.execute(any(UUID.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.FRANCHISE_NOT_FOUND)));

        getTopStockProducts(UUID.randomUUID().toString())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FRANCHISE_NOT_FOUND")
                .jsonPath("$.message").isEqualTo("The franchise does not exist");
    }

    @Test
    void getTopStockProductsReturns400InvalidRequestForMalformedFranchiseIdWithoutCallingTheUseCase() {
        getTopStockProducts("not-a-uuid")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(getTopStockReportUseCase);
    }

    private WebTestClient.ResponseSpec getTopStockProducts(String franchiseId) {
        return webTestClient.get()
                .uri("/franchises/{franchiseId}/top-stock-products", franchiseId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
    }

    private WebTestClient.ResponseSpec deleteProduct(String franchiseId, String branchId, String id) {
        return webTestClient.delete()
                .uri("/franchises/{franchiseId}/branches/{branchId}/products/{id}", franchiseId, branchId, id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
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

    private WebTestClient.ResponseSpec patchStock(String franchiseId, String branchId, String id, Object body) {
        return webTestClient.patch()
                .uri(ApiPaths.PRODUCT_STOCK, franchiseId, branchId, id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private WebTestClient.ResponseSpec postProduct(String franchiseId, String branchId, Object body) {
        return webTestClient.post()
                .uri("/franchises/{franchiseId}/branches/{branchId}/products", franchiseId, branchId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private WebTestClient.ResponseSpec postBranch(String franchiseId, Object body) {
        return webTestClient.post()
                .uri("/franchises/{franchiseId}/branches", franchiseId)
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

    private WebTestClient.ResponseSpec patchBranch(String franchiseId, String id, Object body) {
        return webTestClient.patch()
                .uri("/franchises/{franchiseId}/branches/{id}", franchiseId, id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
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
