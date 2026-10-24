package co.com.bancolombia.usecase.gettopstockreport;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.page.Page;
import co.com.bancolombia.model.product.BranchTopStock;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.TopStockReport;
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
    private BranchGateway branchGateway;

    @Mock
    private ProductGateway productGateway;

    private GetTopStockReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTopStockReportUseCase(franchiseGateway, branchGateway, productGateway);
    }

    @Test
    void reportsThePageOfBranchesInTheGatewayOrderWithItsMetadata() {
        Page page = Page.builder().number(1).size(3).build();
        BranchTopStock bello = branch("Bello", product("Agua", 0));
        BranchTopStock centro = branch("Centro", null);
        BranchTopStock norte = branch("norte", product("Cola", 7));
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.findTopStockByFranchiseId(FRANCHISE_ID, page)).thenReturn(Flux.just(bello, centro, norte));
        when(branchGateway.countByFranchiseId(FRANCHISE_ID)).thenReturn(Mono.just(7L));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, page))
                .expectNext(TopStockReport.builder().franchiseId(FRANCHISE_ID).branches(List.of(bello, centro, norte))
                        .page(1).size(3).totalElements(7).totalPages(3).build())
                .verifyComplete();
    }

    @Test
    void reportsAnEmptyPageWithItsMetadataPastTheLastBranch() {
        Page page = Page.builder().number(5).size(2).build();
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.findTopStockByFranchiseId(FRANCHISE_ID, page)).thenReturn(Flux.empty());
        when(branchGateway.countByFranchiseId(FRANCHISE_ID)).thenReturn(Mono.just(3L));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, page))
                .expectNext(TopStockReport.builder().franchiseId(FRANCHISE_ID).branches(List.of())
                        .page(5).size(2).totalElements(3).totalPages(2).build())
                .verifyComplete();
    }

    @Test
    void reportsNoBranchesAndNoPagesWhenTheFranchiseHasNoBranches() {
        Page page = Page.builder().number(Page.DEFAULT_NUMBER).size(Page.DEFAULT_SIZE).build();
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.findTopStockByFranchiseId(FRANCHISE_ID, page)).thenReturn(Flux.empty());
        when(branchGateway.countByFranchiseId(FRANCHISE_ID)).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute(FRANCHISE_ID, page))
                .expectNextMatches(report -> report.branches().isEmpty() && report.totalElements() == 0
                        && report.totalPages() == 0)
                .verifyComplete();
    }

    @Test
    void failsWithFranchiseNotFoundWithoutReadingBranchesWhenTheFranchiseDoesNotExist() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(FRANCHISE_ID,
                        Page.builder().number(Page.DEFAULT_NUMBER).size(Page.DEFAULT_SIZE).build()))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && exception.getErrorCode() == ErrorCode.FRANCHISE_NOT_FOUND)
                .verify();
        verifyNoInteractions(productGateway, branchGateway);
    }

    private static BranchTopStock branch(String name, Product topProduct) {
        return BranchTopStock.builder().branchId(UUID.randomUUID()).branchName(name).topProduct(topProduct).build();
    }

    private static Product product(String name, int stock) {
        return Product.builder().id(UUID.randomUUID()).name(name).stock(stock).build();
    }
}
