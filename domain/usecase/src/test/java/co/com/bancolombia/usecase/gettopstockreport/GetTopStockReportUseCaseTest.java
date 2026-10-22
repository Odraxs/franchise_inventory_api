package co.com.bancolombia.usecase.gettopstockreport;

import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.BranchTopStock;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTopStockReportUseCaseTest {

    private static final UUID FRANCHISE_ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    @Mock
    private ProductGateway productGateway;

    private GetTopStockReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTopStockReportUseCase(franchiseGateway, productGateway);
    }

    @Test
    void listsEveryBranchSortedByNameIgnoringCase() {
        BranchTopStock norte = branch("norte", product("Cola", 7));
        BranchTopStock centro = branch("Centro", null);
        BranchTopStock bello = branch("Bello", product("Agua", 0));
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.findTopStockByFranchiseId(FRANCHISE_ID)).thenReturn(Flux.just(norte, centro, bello));

        StepVerifier.create(useCase.execute(FRANCHISE_ID))
                .expectNextMatches(report -> FRANCHISE_ID.equals(report.franchiseId())
                        && report.branches().equals(List.of(bello, centro, norte)))
                .verifyComplete();
    }

    @Test
    void emitsAnEmptyListWhenTheFranchiseHasNoBranches() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.findTopStockByFranchiseId(FRANCHISE_ID)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(FRANCHISE_ID))
                .expectNextMatches(report -> report.branches().isEmpty())
                .verifyComplete();
    }

    @Test
    void failsWithFranchiseNotFoundWithoutReadingProductsWhenTheFranchiseDoesNotExist() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(FRANCHISE_ID))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.FRANCHISE_NOT_FOUND)
                .verify();
        verifyNoInteractions(productGateway);
    }

    private static BranchTopStock branch(String name, Product topProduct) {
        return BranchTopStock.builder().branchId(UUID.randomUUID()).branchName(name).topProduct(topProduct).build();
    }

    private static Product product(String name, int stock) {
        return Product.builder().id(UUID.randomUUID()).name(name).stock(stock).build();
    }
}
