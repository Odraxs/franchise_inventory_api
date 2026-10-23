package co.com.bancolombia.api;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.product.BranchTopStock;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.TopStockReport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TopStockHandlerTest extends ApiTestBase {

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
}
