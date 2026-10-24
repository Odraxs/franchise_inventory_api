package co.com.bancolombia.usecase.updateproductstock;

import co.com.bancolombia.model.branch.gateways.BranchGateway;
import co.com.bancolombia.model.exception.BusinessException;
import co.com.bancolombia.model.exception.BusinessException.ErrorCode;
import co.com.bancolombia.model.franchise.gateways.FranchiseGateway;
import co.com.bancolombia.model.product.Product;
import co.com.bancolombia.model.product.UpdateProductStockCommand;
import co.com.bancolombia.model.product.gateways.ProductGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProductStockUseCaseTest {

    private static final UUID FRANCHISE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private FranchiseGateway franchiseGateway;

    @Mock
    private BranchGateway branchGateway;

    @Mock
    private ProductGateway productGateway;

    private UpdateProductStockUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateProductStockUseCase(franchiseGateway, branchGateway, productGateway);
    }

    @Test
    void emitsTheProductWithTheNewStock() {
        Product updated = Product.builder().id(PRODUCT_ID).branchId(BRANCH_ID).name("Cola").stock(0).build();
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.updateStock(PRODUCT_ID, BRANCH_ID, 0)).thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.execute(command(0)))
                .expectNext(updated)
                .verifyComplete();
    }

    @Test
    void failsWithProductNotFoundWhenTheGatewayUpdatesNothing() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(productGateway.updateStock(PRODUCT_ID, BRANCH_ID, 5)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(command(5)))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.PRODUCT_NOT_FOUND))
                .verify();
    }

    @Test
    void failsWithFranchiseNotFoundWithoutUpdatingWhenTheFranchiseDoesNotExist() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(command(5)))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.FRANCHISE_NOT_FOUND))
                .verify();
        verifyNoInteractions(branchGateway, productGateway);
    }

    @Test
    void failsWithBranchNotFoundWithoutUpdatingWhenTheBranchIsNotInTheFranchise() {
        when(franchiseGateway.existsById(FRANCHISE_ID)).thenReturn(Mono.just(true));
        when(branchGateway.existsByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(command(5)))
                .expectErrorMatches(error -> hasCode(error, ErrorCode.BRANCH_NOT_FOUND))
                .verify();
        verifyNoInteractions(productGateway);
    }

    private static UpdateProductStockCommand command(int stock) {
        return UpdateProductStockCommand.builder().franchiseId(FRANCHISE_ID).branchId(BRANCH_ID).productId(PRODUCT_ID)
                .stock(stock).build();
    }

    private static boolean hasCode(Throwable error, ErrorCode code) {
        return error instanceof BusinessException exception && exception.getErrorCode() == code;
    }
}
